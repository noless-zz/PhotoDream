package com.noam.photodream;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Decodes photos on a background thread and delivers the result on the main thread.
 *
 * One instance is one "slot": only the <b>latest</b> {@link #load} counts. If you ask for
 * another photo (or call {@link #cancel()}) before the first one is ready, the first result is
 * dropped – that is what the {@code requestId} counter in the old views did.
 */
public final class AsyncBitmapLoader {

    public interface Callback {
        /** Called on the main thread. {@code bitmap} is null when the file could not be read. */
        void onLoaded(Bitmap bitmap);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int requestId;                       // only touched on the main thread

    public AsyncBitmapLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Main thread only. Replaces any request that is still waiting. */
    public void load(Uri uri, int targetLongSide, Callback callback) {
        final int id = ++requestId;
        executor.execute(() -> {
            Bitmap bmp = BitmapLoader.load(context, uri, targetLongSide);
            main.post(() -> {
                if (id == requestId) callback.onLoaded(bmp);   // else: superseded or cancelled
            });
        });
    }

    /** Ignore whatever is still being decoded. Main thread only. */
    public void cancel() {
        requestId++;
    }

    /** Stop for good and free the background thread. */
    public void release() {
        cancel();
        executor.shutdownNow();
    }
}
