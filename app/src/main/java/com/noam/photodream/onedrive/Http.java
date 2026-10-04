package com.noam.photodream.onedrive;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Tiny HTTP helper on top of HttpURLConnection (no extra libraries). Blocking – background threads only. */
final class Http {

    /** Thrown for non-2xx answers; {@link #code} 401 means the token is no longer valid. */
    static final class HttpException extends IOException {
        final int code;

        HttpException(int code, String body) {
            super("HTTP " + code + ": " + shorten(body));
            this.code = code;
        }

        private static String shorten(String s) {
            return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s);
        }
    }

    private static final int TIMEOUT_MS = 30_000;

    private Http() { }

    static JSONObject postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection c = open(url, null);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readJson(c);
    }

    static JSONObject getJson(String url, String accessToken) throws IOException {
        HttpURLConnection c = open(url, accessToken);
        c.setRequestProperty("Accept", "application/json");
        return readJson(c);
    }

    /**
     * Downloads a Graph "/content" URL. Graph answers with a redirect to a
     * pre-authenticated download link; we follow it ourselves WITHOUT sending
     * our token to that other host.
     */
    static void download(String url, String accessToken, File target) throws IOException {
        HttpURLConnection c = open(url, accessToken);
        c.setInstanceFollowRedirects(false);
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
            String location = c.getHeaderField("Location");
            c.disconnect();
            if (location == null) throw new IOException("Redirect without location");
            c = open(location, null);
            code = c.getResponseCode();
        }
        if (code < 200 || code >= 300) throw new HttpException(code, readAll(c.getErrorStream()));
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String accessToken) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        if (accessToken != null) c.setRequestProperty("Authorization", "Bearer " + accessToken);
        return c;
    }

    private static JSONObject readJson(HttpURLConnection c) throws IOException {
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new HttpException(code, readAll(c.getErrorStream()));
            String text = readAll(c.getInputStream());
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new IOException("Bad JSON from server", e);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
