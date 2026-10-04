package com.noam.photodream.onedrive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Microsoft sign-in with the OAuth 2.0 "authorization code + PKCE" flow.
 *
 * 1. {@link #startSignIn} opens the Microsoft login page in the browser.
 * 2. Microsoft sends the browser to photodream://auth?code=..., which opens
 *    {@link OneDriveRedirectActivity}, which calls {@link #finishSignIn}.
 * 3. We swap the code for an access token (valid ~1 hour) and a refresh
 *    token (lets us get new access tokens without asking the user again).
 *
 * PKCE means the app needs no secret: only the app that started the login
 * knows the random "code verifier" needed to redeem the code.
 */
public final class OneDriveAuth {

    /** The user must sign in (again). */
    public static class NotSignedInException extends IOException {
        NotSignedInException(String msg) { super(msg); }
    }

    private static final String PENDING = "onedrive_pending";   // plain prefs, short-lived
    private static final String K_VERIFIER = "verifier";
    private static final String K_STATE = "state";

    private static final String K_REFRESH = "refresh_token";
    private static final String K_ACCESS = "access_token";
    private static final String K_EXPIRES = "access_expires";
    private static final String K_ACCOUNT = "account_name";

    private static final SecureRandom RANDOM = new SecureRandom();

    private OneDriveAuth() { }

    // ---------------------------------------------------------------- sign in

    public static void startSignIn(Activity activity) {
        String verifier = randomUrlSafe(64);
        String state = randomUrlSafe(16);
        activity.getSharedPreferences(PENDING, Context.MODE_PRIVATE).edit()
                .putString(K_VERIFIER, verifier)
                .putString(K_STATE, state)
                .apply();

        Uri uri = Uri.parse(OneDriveConfig.AUTH_BASE + "authorize").buildUpon()
                .appendQueryParameter("client_id", OneDriveConfig.clientId(activity))
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", OneDriveConfig.REDIRECT_URI)
                .appendQueryParameter("response_mode", "query")
                .appendQueryParameter("scope", OneDriveConfig.SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge(verifier))
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("prompt", "select_account")
                .build();
        activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    /**
     * Second half of sign-in, called with the redirect URI. Blocking (network).
     * @return the signed-in account's display name
     */
    public static String finishSignIn(Context context, Uri redirect) throws IOException {
        String error = redirect.getQueryParameter("error");
        if (error != null) {
            String desc = redirect.getQueryParameter("error_description");
            throw new IOException(desc != null ? desc : error);
        }
        SharedPreferences pending = context.getSharedPreferences(PENDING, Context.MODE_PRIVATE);
        String verifier = pending.getString(K_VERIFIER, null);
        String state = pending.getString(K_STATE, null);
        pending.edit().clear().apply();

        String code = redirect.getQueryParameter("code");
        if (code == null || verifier == null || state == null
                || !state.equals(redirect.getQueryParameter("state"))) {
            throw new IOException("Sign-in answer did not match – please try again");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", OneDriveConfig.clientId(context));
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", OneDriveConfig.REDIRECT_URI);
        form.put("code_verifier", verifier);
        form.put("scope", OneDriveConfig.SCOPES);
        saveTokens(context, Http.postForm(OneDriveConfig.AUTH_BASE + "token", form));

        String name;
        try {
            JSONObject me = Http.getJson(OneDriveConfig.GRAPH + "/me", getAccessToken(context));
            name = me.optString("displayName", "");
            String mail = me.optString("userPrincipalName", "");
            if (name.isEmpty()) name = mail;
        } catch (IOException e) {
            name = "Microsoft account";
        }
        new SecureStore(context).put(K_ACCOUNT, name);
        return name;
    }

    // ---------------------------------------------------------------- tokens

    public static boolean isSignedIn(Context context) {
        return new SecureStore(context).get(K_REFRESH) != null;
    }

    public static String accountName(Context context) {
        String n = new SecureStore(context).get(K_ACCOUNT);
        return n == null ? "" : n;
    }

    /** A valid access token, refreshed if needed. Blocking (network). */
    public static synchronized String getAccessToken(Context context) throws IOException {
        SecureStore store = new SecureStore(context);
        String access = store.get(K_ACCESS);
        String expires = store.get(K_EXPIRES);
        long now = System.currentTimeMillis();
        if (access != null && expires != null && Long.parseLong(expires) - 60_000 > now) {
            return access;
        }

        String refresh = store.get(K_REFRESH);
        if (refresh == null) throw new NotSignedInException("Not connected to OneDrive");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", OneDriveConfig.clientId(context));
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refresh);
        form.put("scope", OneDriveConfig.SCOPES);
        try {
            saveTokens(context, Http.postForm(OneDriveConfig.AUTH_BASE + "token", form));
        } catch (Http.HttpException e) {
            if (e.code == 400 || e.code == 401) {
                // refresh token expired or was revoked: user has to sign in again
                signOutTokensOnly(context);
                throw new NotSignedInException("OneDrive sign-in expired – please connect again");
            }
            throw e;
        }
        return store.get(K_ACCESS);
    }

    public static void signOut(Context context) {
        signOutTokensOnly(context);
    }

    private static void signOutTokensOnly(Context context) {
        new SecureStore(context).clear();
    }

    private static void saveTokens(Context context, JSONObject json) throws IOException {
        try {
            SecureStore store = new SecureStore(context);
            store.put(K_ACCESS, json.getString("access_token"));
            long expiresIn = json.optLong("expires_in", 3600);
            store.put(K_EXPIRES, String.valueOf(System.currentTimeMillis() + expiresIn * 1000));
            // Microsoft may hand out a new refresh token each time – always keep the newest
            String refresh = json.optString("refresh_token", "");
            if (!refresh.isEmpty()) store.put(K_REFRESH, refresh);
        } catch (JSONException e) {
            throw new IOException("Unexpected token answer", e);
        }
    }

    // ---------------------------------------------------------------- PKCE helpers

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
