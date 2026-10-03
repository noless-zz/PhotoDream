package com.noam.photodream;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glue shared by the screensaver and the preview screen:
 * reads the settings, loads the photo list in the background,
 * and starts the {@link SlideshowView}.
 * Expects a view inflated from R.layout.view_slideshow_overlay.
 */
public class SlideshowController {

    private final Context context;
    private final SlideshowView slideshow;
    private final View clockBox;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started;

    public SlideshowController(Context context, View root, SlideshowView.Listener listener) {
        this.context = context;
        this.slideshow = root.findViewById(R.id.slideshow);
        this.clockBox = root.findViewById(R.id.clock_box);
        slideshow.setListener(listener);
    }

    public void start() {
        Prefs prefs = new Prefs(context);
        slideshow.setIntervalSeconds(prefs.getIntervalSeconds());
        slideshow.setTransition(prefs.getTransition());
        slideshow.setCrop(prefs.isCrop());
        clockBox.setVisibility(prefs.isShowClock() ? View.VISIBLE : View.GONE);

        started = true;
        io.execute(() -> {
            List<Uri> photos = PhotoRepository.loadAll(context);
            main.post(() -> {
                if (started) slideshow.start(photos);
            });
        });
    }

    public void stop() {
        started = false;
        slideshow.stop();
    }

    public void release() {
        stop();
        slideshow.release();
        io.shutdownNow();
    }
}
