package com.noam.photodream.describe.cloud;

import android.content.Context;
import android.graphics.Bitmap;

import com.noam.photodream.Prefs;
import com.noam.photodream.cloud.Http;
import com.noam.photodream.onedrive.SecureStore;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Sends one downscaled photo to the AI service the user chose and returns a sentence.
 * Opt-in: it is only "ready" when the switch is on, a key is saved and today's cap is not used up.
 * The key is kept encrypted (one entry per service) and is never logged; callers must not log
 * exception messages from here either, because a service may echo part of the key in an error.
 * Blocking – background threads only.
 */
public final class CloudDescriber {

    /** Longest side of the JPEG that leaves the phone. */
    public static final int MAX_SIDE = 512;
    private static final int JPEG_QUALITY = 80;
    private static final int TIMEOUT_MS = 20_000;

    /** The service rejected the key (HTTP 401/403): stop trying for this run. */
    public static final class BadKeyException extends Exception {
        BadKeyException() { super("API key rejected"); }
    }

    private final Context app;
    private final Prefs prefs;
    private final CloudDescriptionProvider provider;
    private final DailyCap cap;

    public CloudDescriber(Context context) {
        app = context.getApplicationContext();
        prefs = new Prefs(app);
        provider = CloudDescriptionProviders.byId(prefs.getCloudProvider());
        cap = new DailyCap(today(), prefs.getCloudUsedDay(), prefs.getCloudUsedCount(), prefs.getCloudDailyLimit());
    }

    // ---------------------------------------------------------------- key storage

    private static SecureStore store(Context context) {
        return new SecureStore(context, "describe_cloud_secure", "photodream_describe_cloud");
    }

    public static boolean hasKey(Context context, String providerId) {
        String k = store(context).get("key_" + providerId);
        return k != null && !k.isEmpty();
    }

    public static void saveKey(Context context, String providerId, String key) {
        store(context).put("key_" + providerId, key.trim());
    }

    public static void removeKey(Context context, String providerId) {
        store(context).put("key_" + providerId, null);
    }

    // ---------------------------------------------------------------- describing

    /** True if the switch is on, a key exists and today's cap allows another photo. */
    public boolean isReady() {
        return prefs.isCloudDescribe() && cap.allows() && hasKey(app, provider.id());
    }

    /**
     * @return a sentence in {@code lang}, or "" if the service gave none.
     * @throws BadKeyException if the key was refused; other failures are IOExceptions
     */
    public String describe(Bitmap bitmap, String lang) throws Exception {
        String key = store(app).get("key_" + provider.id());
        if (key == null || key.isEmpty()) throw new BadKeyException();

        String custom = prefs.getCloudModel(provider.id()).trim();
        String model = custom.isEmpty() ? provider.defaultModel() : custom;

        CloudDescriptionProvider.Request req = provider.buildRequest(key, model, toJpeg(bitmap), lang);
        String answer;
        try {
            answer = Http.postJson(req.url, req.headers, req.body, TIMEOUT_MS);
        } catch (Http.HttpException e) {
            if (e.code == 401 || e.code == 403) throw new BadKeyException();
            throw new java.io.IOException("Cloud description failed: HTTP " + e.code);   // no body: it may echo the key
        }
        cap.record();                                           // a request that was answered counts, even if empty
        prefs.setCloudUsed(cap.day(), cap.used());
        return provider.parseSentence(answer);
    }

    /** Scales to {@link #MAX_SIDE} and compresses; keeps data use and cost small. */
    static byte[] toJpeg(Bitmap src) {
        int longSide = Math.max(src.getWidth(), src.getHeight());
        Bitmap scaled = src;
        if (longSide > MAX_SIDE) {
            float f = (float) MAX_SIDE / longSide;
            scaled = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * f)),
                    Math.max(1, Math.round(src.getHeight() * f)), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        if (scaled != src) scaled.recycle();
        return out.toByteArray();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
