package com.noam.photodream;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen photo slideshow with animated transitions and touch control.
 *
 * Used by both {@link PhotoDreamService} (the real screensaver) and
 * {@link PreviewActivity}, so all the slideshow logic lives here once.
 *
 * How it works: two ImageViews stacked on top of each other. The next photo
 * is decoded on a background thread into the hidden one, then animated in
 * on top of the current one. Then they swap roles.
 *
 * Gestures: swipe left/right = next/previous, tap = pause/resume,
 * long-press = ask the owner to exit ({@link PhotoDisplay.Listener#onExitRequested()}).
 */
public class SlideshowView extends FrameLayout implements PhotoDisplay {

    public enum Transition { SLIDE, FADE, KEN_BURNS }

    private static final long SLIDE_MS = 700;
    private static final long FADE_MS = 1200;
    private static final long RETRY_MS = 300;

    private final ImageView[] imageViews = new ImageView[2];
    private int front = 0;                       // index into imageViews currently visible
    private final TextView messageView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final GestureDetector gestures;

    private List<Photo> photos = new ArrayList<>();
    private int index = -1;
    private int requestId = 0;                   // ignores decodes that finished too late
    private boolean running;
    private boolean paused;
    private int failuresInARow;
    private Animator kenBurns;

    private long intervalMs = 10_000;
    private Transition transition = Transition.SLIDE;
    private boolean crop = true;
    private PhotoDisplay.Listener listener;

    private final Runnable advance = () -> show(+1);
    private final Runnable hideMessage = this::fadeOutMessage;

    public SlideshowView(Context context) { this(context, null); }

    public SlideshowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(context);
            iv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addView(iv);
            imageViews[i] = iv;
        }
        messageView = new TextView(context);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        messageView.setLayoutParams(lp);
        messageView.setGravity(Gravity.CENTER);
        messageView.setTextColor(0xE6FFFFFF);
        messageView.setTextSize(20);
        messageView.setShadowLayer(12, 0, 0, 0xAA000000);
        messageView.setAlpha(0f);
        addView(messageView);

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();      // also what TalkBack's "double-tap to activate" does
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                if (listener != null) listener.onExitRequested();   // double-tap anywhere = leave
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (Math.abs(vx) < Math.abs(vy)) return false;   // vertical swipe: ignore
                if (vx < 0) next(); else previous();
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                if (listener != null) listener.onExitRequested();
            }
        });
        applyScaleType();
    }

    // ---------------------------------------------------------------- settings

    @Override
    public void setIntervalSeconds(int seconds) { intervalMs = seconds * 1000L; }
    public void setTransition(Transition t) { transition = t; }
    public void setCrop(boolean crop) { this.crop = crop; applyScaleType(); }
    @Override
    public void setListener(PhotoDisplay.Listener l) { listener = l; }

    private void applyScaleType() {
        ImageView.ScaleType type = crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER;
        for (ImageView iv : imageViews) iv.setScaleType(type);
    }

    // ---------------------------------------------------------------- control

    /** Start (or restart) with a new list of photos. Call on the main thread. */
    @Override
    public void start(List<Photo> newPhotos) {
        photos = new ArrayList<>(newPhotos);
        index = -1;
        running = true;
        paused = false;
        failuresInARow = 0;
        if (photos.isEmpty()) {
            showMessage(getContext().getString(R.string.msg_no_photos), false);
            return;
        }
        show(+1);
    }

    /** Stop timers and animations. Safe to call more than once. */
    @Override
    public void stop() {
        running = false;
        requestId++;
        handler.removeCallbacksAndMessages(null);
        cancelAnimations();
    }

    /** Stop for good – also shuts down the background thread. */
    @Override
    public void release() {
        stop();
        loader.shutdownNow();
    }

    public void next() { show(+1); }
    public void previous() { show(-1); }

    public void togglePause() {
        if (!running || photos.isEmpty()) return;
        paused = !paused;
        handler.removeCallbacks(advance);
        if (paused) {
            if (kenBurns != null) kenBurns.pause();
            showMessage(getContext().getString(R.string.msg_paused), false);
        } else {
            if (kenBurns != null) kenBurns.resume();
            showMessage(getContext().getString(R.string.msg_playing), true);
            scheduleNext();
        }
    }

    // ---------------------------------------------------------------- internals

    private void show(int delta) {
        if (!running || photos.isEmpty()) return;
        handler.removeCallbacks(advance);
        index = Math.floorMod(index + delta, photos.size());
        final Uri uri = photos.get(index).uri;
        final int id = ++requestId;
        final int target = targetLongSide();
        final boolean forward = delta >= 0;

        loader.execute(() -> {
            Bitmap bmp = BitmapLoader.load(getContext(), uri, target);
            handler.post(() -> {
                if (id != requestId || !running) return;   // user swiped again / stopped
                if (bmp == null) {
                    // unreadable file: skip it, but don't spin forever if all fail
                    if (++failuresInARow < photos.size()) handler.postDelayed(() -> show(forward ? 1 : -1), RETRY_MS);
                    return;
                }
                failuresInARow = 0;
                display(bmp, forward);
                scheduleNext();
            });
        });
    }

    private void scheduleNext() {
        if (running && !paused) handler.postDelayed(advance, intervalMs);
    }

    private void display(Bitmap bmp, boolean forward) {
        cancelAnimations();
        final ImageView current = imageViews[front];
        final ImageView incoming = imageViews[1 - front];

        resetView(incoming);
        incoming.setImageBitmap(bmp);
        incoming.bringToFront();
        messageView.bringToFront();
        float width = getWidth() > 0 ? getWidth() : targetLongSide();

        switch (transition) {
            case SLIDE:
                incoming.setTranslationX(forward ? width : -width);
                incoming.animate().translationX(0f).setDuration(SLIDE_MS)
                        .setInterpolator(new DecelerateInterpolator(1.6f));
                current.animate().translationX(forward ? -width * 0.35f : width * 0.35f)
                        .alpha(0.3f).setDuration(SLIDE_MS)
                        .setInterpolator(new DecelerateInterpolator(1.6f));
                break;

            case FADE:
                incoming.setAlpha(0f);
                incoming.animate().alpha(1f).setDuration(FADE_MS);
                break;

            case KEN_BURNS:
                incoming.setAlpha(0f);
                incoming.animate().alpha(1f).setDuration(FADE_MS);
                // slow zoom + drift for the whole time the photo is on screen
                float endScale = 1.12f + random.nextFloat() * 0.08f;
                float drift = (random.nextFloat() - 0.5f) * width * 0.06f;
                ObjectAnimator zoom = ObjectAnimator.ofPropertyValuesHolder(incoming,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, endScale),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, endScale),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, drift));
                zoom.setDuration(intervalMs + FADE_MS);
                zoom.setInterpolator(new LinearInterpolator());
                zoom.start();
                kenBurns = zoom;
                break;
        }
        front = 1 - front;
    }

    private void resetView(ImageView v) {
        v.animate().cancel();
        v.setAlpha(1f);
        v.setTranslationX(0f);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    private void cancelAnimations() {
        if (kenBurns != null) {
            kenBurns.cancel();
            kenBurns = null;
        }
        // jump any running slide/fade to its end state
        for (ImageView iv : imageViews) iv.animate().cancel();
    }

    private void showMessage(String text, boolean autoHide) {
        handler.removeCallbacks(hideMessage);
        messageView.setText(text);
        messageView.bringToFront();
        messageView.animate().alpha(1f).setDuration(200);
        if (autoHide) handler.postDelayed(hideMessage, 1200);
    }

    private void fadeOutMessage() {
        messageView.animate().alpha(0f).setDuration(400);
    }

    private int targetLongSide() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.max(dm.widthPixels, dm.heightPixels);
    }

    // performClick() is called by the gesture detector once a single tap is confirmed
    // (not a double tap), so lint can't see it in this method.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestures.onTouchEvent(event) || super.onTouchEvent(event);
    }

    /** A tap (or an accessibility click) pauses / resumes the slideshow. */
    @Override
    public boolean performClick() {
        togglePause();
        return super.performClick();
    }
}
