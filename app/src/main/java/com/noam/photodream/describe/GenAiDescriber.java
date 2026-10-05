package com.noam.photodream.describe;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.imagedescription.ImageDescriber;
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions;
import com.google.mlkit.genai.imagedescription.ImageDescription;
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest;
import com.google.mlkit.genai.imagedescription.ImageDescriptionResult;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * ML Kit GenAI "Image Description" (Gemini Nano): a real sentence about the photo, English.
 * Only on newer phones (e.g. Pixel 9+, Galaxy S25+). Google only allows this inference while the
 * app is the top foreground app, so it never runs in the background job.
 */
public class GenAiDescriber implements PhotoDescriber {

    private static final String TAG = "GenAiDescriber";
    private static final long STATUS_CACHE_MS = 60_000;

    private ImageDescriber client;
    private int lastStatus = FeatureStatus.UNAVAILABLE;
    private long lastCheck;

    @Override public String engine() { return "genai"; }

    @Override public boolean needsForeground() { return true; }

    @Override public boolean isAvailable(Context context) { return status(context) == FeatureStatus.AVAILABLE; }

    /** One of {@link FeatureStatus}: UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, AVAILABLE. Never throws. */
    public synchronized int status(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < STATUS_CACHE_MS) return lastStatus;
        try {
            lastStatus = client(context).checkFeatureStatus().get(10, TimeUnit.SECONDS);
        } catch (Exception | LinkageError e) {
            Log.i(TAG, "Gemini Nano not available on this phone: " + e);
            lastStatus = FeatureStatus.UNAVAILABLE;
        }
        lastCheck = now;
        return lastStatus;
    }

    /** Starts downloading the on-device model (Settings › Prepare). Result arrives through the callback. */
    public void download(Context context, DownloadCallback callback) {
        client(context).downloadFeature(callback);
        lastCheck = 0;                        // look again next time
    }

    @Override
    public Result describe(Context context, Bitmap bitmap) throws Exception {
        ImageDescriptionResult r = client(context)
                .runInference(ImageDescriptionRequest.builder(bitmap).build())
                .get(90, TimeUnit.SECONDS);
        String sentence = r == null ? "" : r.getDescription();
        return new Result(new ArrayList<String>(), sentence == null ? "" : sentence.trim());
    }

    private synchronized ImageDescriber client(Context context) {
        if (client == null) client = ImageDescription.getClient(ImageDescriberOptions.builder(context.getApplicationContext()).build());
        return client;
    }

    public synchronized void close() {
        if (client != null) client.close();
        client = null;
    }
}
