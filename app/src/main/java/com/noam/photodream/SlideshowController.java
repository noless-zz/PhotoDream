package com.noam.photodream;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glue shared by the screensaver and the preview screen:
 * reads the settings, picks the display mode, loads the photo list in
 * the background, and starts the chosen {@link PhotoDisplay}.
 * Expects a view inflated from R.layout.view_slideshow_overlay.
 */
public class SlideshowController {

    private final Context context;
    private final SlideshowView slideshow;
    private final PhotoTableView table;
    private final View clockBox;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PhotoDisplay display;
    private boolean started;

    public SlideshowController(Context context, View root, PhotoDisplay.Listener listener) {
        this.context = context;
        this.slideshow = root.findViewById(R.id.slideshow);
        this.table = root.findViewById(R.id.photo_table);
        this.clockBox = root.findViewById(R.id.clock_box);
        slideshow.setListener(listener);
        table.setListener(listener);
    }

    public void start() {
        Prefs prefs = new Prefs(context);
        boolean tableMode = prefs.getDisplayMode() == Prefs.DisplayMode.TABLE;

        slideshow.setVisibility(tableMode ? View.GONE : View.VISIBLE);
        table.setVisibility(tableMode ? View.VISIBLE : View.GONE);

        if (tableMode) {
            table.setEntry(prefs.getTableEntry());
            table.setMaxCards(prefs.getTableMaxCards());
            table.setCardSizePercent(prefs.getTableCardSize());
            table.setMaxRotation(prefs.getTableRotation());
            table.setDrift(prefs.isTableDrift());
            display = table;
        } else {
            slideshow.setTransition(prefs.getTransition());
            slideshow.setCrop(prefs.isCrop());
            display = slideshow;
        }
        display.setIntervalSeconds(prefs.getIntervalSeconds());
        clockBox.setVisibility(prefs.isShowClock() ? View.VISIBLE : View.GONE);

        started = true;
        final PhotoDisplay target = display;
        io.execute(() -> {
            List<Photo> photos = PhotoRepository.loadAll(context);
            main.post(() -> {
                if (started && display == target) target.start(photos);
            });
        });
    }

    public void stop() {
        started = false;
        if (display != null) display.stop();
    }

    public void release() {
        stop();
        slideshow.release();
        table.release();
        io.shutdownNow();
    }
}
