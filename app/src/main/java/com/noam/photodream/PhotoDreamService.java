package com.noam.photodream;

import android.service.dreams.DreamService;

/**
 * The screensaver itself. Android starts it automatically when the phone
 * is charging and idle (if the user picked it in Settings > Display >
 * Screen saver). The lock screen still protects the phone when it ends.
 *
 * Lifecycle: onAttachedToWindow -> onDreamingStarted -> onDreamingStopped -> onDetachedFromWindow
 */
public class PhotoDreamService extends DreamService {

    private SlideshowController controller;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Interactive: touches go to our view instead of ending the screensaver.
        // So we must give the user a way out: long-press (or the Back gesture).
        setInteractive(true);
        setFullscreen(true);
        setContentView(R.layout.view_slideshow_overlay);

        controller = new SlideshowController(this, getWindow().getDecorView(), this::finish);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        // false = let the system dim the screen (nice next to the bed)
        setScreenBright(!new Prefs(this).isDim());
        controller.start();
    }

    @Override
    public void onDreamingStopped() {
        controller.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        controller.release();
        super.onDetachedFromWindow();
    }
}
