package com.noam.photodream.gdrive;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import com.noam.photodream.R;
import com.noam.photodream.cloud.CloudItem;
import com.noam.photodream.cloud.CloudProvider;
import com.noam.photodream.cloud.Http;
import com.noam.photodream.cloud.NotSignedInException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Google Drive via the Drive v3 REST API.
 *
 * Sign-in uses Google Play services' AuthorizationClient: Google recognises
 * the app by its package name + signing-key fingerprint (registered as an
 * "Android" client in Google Cloud), so there is no client ID or secret in
 * the app. Play services keeps the grant and hands out fresh access tokens,
 * also to the background sync – no refresh tokens for us to store.
 *
 * Scope: drive.readonly ("restricted" – fine for up to 100 users without
 * Google verification; see docs/GOOGLE_DRIVE_SETUP.md).
 */
public class GoogleDriveProvider implements CloudProvider {

    private static final String SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";

    private static final String PREFS = "gdrive_account";
    private static final String K_EMAIL = "email";
    private static final String K_NAME = "name";

    @Override public String id() { return "gdrive"; }

    @Override public String filePrefix() { return "gd_"; }

    @Override public String displayName(Context c) { return c.getString(R.string.gdrive_name); }

    /** Nothing to paste: the Android client in Google Cloud is matched by package + SHA-1. */
    @Override public boolean isConfigured(Context c) { return true; }

    @Override public String notConfiguredMessage(Context c) { return ""; }

    @Override
    public boolean isSignedIn(Context c) {
        return prefs(c).getString(K_EMAIL, null) != null;
    }

    @Override
    public String accountName(Context c) {
        SharedPreferences p = prefs(c);
        String name = p.getString(K_NAME, "");
        String email = p.getString(K_EMAIL, "");
        return name.isEmpty() ? email : name + " (" + email + ")";
    }

    // ---------------------------------------------------------------- sign-in

    private static AuthorizationRequest request(Account account) {
        AuthorizationRequest.Builder b = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE)));
        if (account != null) b.setAccount(account);
        return b.build();
    }

    @Override
    public void connect(Activity activity, ActivityResultLauncher<IntentSenderRequest> resolution,
                        Callback done) {
        Identity.getAuthorizationClient(activity)
                .authorize(request(null))
                .addOnSuccessListener(result -> {
                    if (result.hasResolution() && result.getPendingIntent() != null) {
                        // Google shows account picker + consent ("unverified app" for us)
                        resolution.launch(new IntentSenderRequest.Builder(
                                result.getPendingIntent().getIntentSender()).build());
                    } else {
                        finishConnect(activity, result, done);
                    }
                })
                .addOnFailureListener(e -> done.onDone(message(activity, e)));
    }

    @Override
    public void onResolutionResult(Activity activity, int resultCode, Intent data, Callback done) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            done.onDone(activity.getString(R.string.cloud_cancelled));
            return;
        }
        try {
            AuthorizationResult result = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(data);
            finishConnect(activity, result, done);
        } catch (ApiException e) {
            done.onDone(message(activity, e));
        }
    }

    /** We have a token: ask Drive who we are and remember the account for background syncs. */
    private void finishConnect(Activity activity, AuthorizationResult result, Callback done) {
        String token = result.getAccessToken();
        if (token == null) {
            done.onDone(activity.getString(R.string.cloud_cancelled));
            return;
        }
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                JSONObject about = Http.getJson(API + "/about?fields=user(displayName,emailAddress)", token);
                JSONObject user = about.getJSONObject("user");
                prefs(app).edit()
                        .putString(K_EMAIL, user.optString("emailAddress"))
                        .putString(K_NAME, user.optString("displayName"))
                        .apply();
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            activity.runOnUiThread(() -> done.onDone(err));
        }, "gdrive-connect").start();
    }

    /**
     * Blocking. A valid access token for background work. Play services
     * refreshes it silently; if the user revoked access we must ask again.
     */
    private String accessToken(Context c) throws IOException {
        String email = prefs(c).getString(K_EMAIL, null);
        if (email == null) throw new NotSignedInException(c.getString(R.string.cloud_sign_in_again));
        AuthorizationClient client = Identity.getAuthorizationClient(c);
        try {
            AuthorizationResult r = Tasks.await(
                    client.authorize(request(new Account(email, "com.google"))), 30, TimeUnit.SECONDS);
            if (r.hasResolution() || r.getAccessToken() == null) {
                throw new NotSignedInException(c.getString(R.string.cloud_sign_in_again));
            }
            return r.getAccessToken();
        } catch (ExecutionException e) {
            throw new IOException(message(c, e.getCause()), e);
        } catch (InterruptedException | TimeoutException e) {
            throw new IOException("Google sign-in timed out", e);
        }
    }

    @Override
    public void signOut(Context c) {
        prefs(c).edit().clear().apply();
    }

    // ---------------------------------------------------------------- Drive API

    @Override public String rootId() { return "root"; }

    @Override
    public List<CloudItem> listChildren(Context c, String folderId) throws IOException {
        String q = "'" + folderId.replace("'", "\\'") + "' in parents and trashed = false";
        String base = API + "/files?pageSize=1000&fields="
                + Uri.encode("nextPageToken,files(id,name,mimeType)")
                + "&q=" + Uri.encode(q);
        List<CloudItem> out = new ArrayList<>();
        String pageToken = null;
        do {
            String url = pageToken == null ? base : base + "&pageToken=" + Uri.encode(pageToken);
            JSONObject page = getJson(c, url);
            JSONArray files = page.optJSONArray("files");
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.optJSONObject(i);
                    if (f == null) continue;
                    String mime = f.optString("mimeType", "");
                    out.add(new CloudItem(f.optString("id"), f.optString("name"),
                            FOLDER_MIME.equals(mime), mime.startsWith("image/"), -1));
                }
            }
            pageToken = page.optString("nextPageToken", "");
            if (pageToken.isEmpty()) pageToken = null;
        } while (pageToken != null);
        return out;
    }

    @Override
    public void download(Context c, String itemId, File target) throws IOException {
        Http.download(API + "/files/" + Uri.encode(itemId) + "?alt=media", accessToken(c), target);
    }

    private JSONObject getJson(Context c, String url) throws IOException {
        try {
            return Http.getJson(url, accessToken(c));
        } catch (Http.HttpException e) {
            if (e.code == 401) throw new NotSignedInException(c.getString(R.string.cloud_sign_in_again));
            throw e;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Turn Google's error codes into something readable. */
    private static String message(Context c, Throwable e) {
        if (e instanceof ApiException) {
            int code = ((ApiException) e).getStatusCode();
            if (code == 10) return c.getString(R.string.gdrive_developer_error);   // DEVELOPER_ERROR
            if (code == 16) return c.getString(R.string.cloud_cancelled);         // CANCELED
            return "Google error " + code;
        }
        return e == null ? "Unknown error" : String.valueOf(e.getMessage());
    }
}
