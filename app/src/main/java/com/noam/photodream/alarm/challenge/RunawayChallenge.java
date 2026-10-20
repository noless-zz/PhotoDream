package com.noam.photodream.alarm.challenge;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.noam.photodream.BitmapLoader;
import com.noam.photodream.Photo;
import com.noam.photodream.alarm.Alarm;
import com.noam.photodream.alarm.AlarmChallenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Catch the runaway photo": one photo dodges your finger. Touch it (the card jumps away before the
 * tap lands, see {@link RunawayRules}) and lift your finger inside its current bounds to catch it.
 * Hard mode adds two decoy photos that don't count. Touches are read on the whole table, because the
 * card is usually not under the finger any more.
 */
public class RunawayChallenge implements AlarmChallenge {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private Context context;
    private Listener listener;
    private RunawayRules rules;
    private FrameLayout table;
    private FlipCardView runaway;
    private final List<FlipCardView> decoys = new ArrayList<>();
    private List<Photo> photos;
    private Alarm.Difficulty difficulty;
    private int baseW, baseH;
    private boolean built, running, solved;

    // a dodging game needs raw touch events on the whole table; there is no "click" to forward to accessibility
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View createView(Context ctx, List<Photo> photoList, Alarm.Difficulty diff, Listener l) {
        context = ctx;
        listener = l;
        photos = photoList;
        difficulty = diff;
        rules = new RunawayRules(diff, random);

        table = new FrameLayout(ctx);
        table.setOnTouchListener(this::onTouch);
        table.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            if (!built && right - left > 0 && bottom - top > 0) {
                built = true;
                final int w = right - left, h = bottom - top;
                handler.post(() -> build(w, h));      // not while the layout pass is still running
            }
        });
        l.onProgress(0, rules.catchesNeeded());
        return table;
    }

    private void build(int w, int h) {
        // sized so a small 5.5" phone held in one hand can still reach it
        baseW = Math.round(Math.min(w, h) * 0.42f);
        baseH = Math.round(baseW / 0.75f);

        runaway = newCard(photos.get(0), baseW, baseH);
        place(runaway, w / 2f, h / 2f, 1f);
        for (int i = 0; i < RunawayRules.decoys(difficulty); i++) {
            FlipCardView d = newCard(photos.get((i + 1) % photos.size()), baseW, baseH);
            float[] c = rules.nextCenter(baseW, baseH, w, h, w / 2f, h / 2f);
            place(d, c[0], c[1], 0.8f);
            decoys.add(d);
        }
        runaway.bringToFront();
    }

    private FlipCardView newCard(Photo photo, int w, int h) {
        FlipCardView card = new FlipCardView(context);
        card.setLayoutParams(new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START));
        card.setFaceUp(true);                                   // this game shows the photo from the start
        card.setContentDescription(context.getString(com.noam.photodream.R.string.runaway_card));
        table.addView(card);
        decoder.execute(() -> {
            Bitmap bmp = BitmapLoader.load(context, photo.uri, Math.max(w, h));
            handler.post(() -> {
                if (bmp != null) card.setPhoto(bmp);
            });
        });
        return card;
    }

    /** Put a card's centre at (cx, cy) with the given scale; position = translation from the top-left corner. */
    private void place(View card, float cx, float cy, float scale) {
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setTranslationX(cx - baseW / 2f);
        card.setTranslationY(cy - baseH / 2f);
        card.setRotation((random.nextFloat() - 0.5f) * 10f);
    }

    // ---------------------------------------------------------------- touch

    @SuppressLint("ClickableViewAccessibility")
    private boolean onTouch(View v, MotionEvent e) {
        if (!running || solved || runaway == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (rules.onTouchDown(isNear(e.getX(), e.getY()))) dodge();
                return true;
            case MotionEvent.ACTION_UP:
                if (rules.onTouchUp(isInside(e.getX(), e.getY()))) caught();
                return true;
            default:
                return true;
        }
    }

    /** Current centre and half-size of the runaway card (it may be mid-animation: we use where it is now). */
    private float centerX() { return runaway.getTranslationX() + baseW / 2f; }
    private float centerY() { return runaway.getTranslationY() + baseH / 2f; }
    private float halfW() { return baseW * runaway.getScaleX() / 2f; }
    private float halfH() { return baseH * runaway.getScaleY() / 2f; }

    private boolean isInside(float x, float y) {
        return Math.abs(x - centerX()) <= halfW() && Math.abs(y - centerY()) <= halfH();
    }

    /** "Near" = inside the card grown by half its size on every side: that is where the card gets nervous. */
    private boolean isNear(float x, float y) {
        return Math.abs(x - centerX()) <= halfW() * 2f && Math.abs(y - centerY()) <= halfH() * 2f;
    }

    private void dodge() {
        moveTo(rules.dodgeMillis(), 60f);
    }

    private void caught() {
        listener.onProgress(rules.catches(), rules.catchesNeeded());
        if (rules.isSolved()) {
            solved = true;
            runaway.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(300).start();
            for (FlipCardView d : decoys) d.animate().alpha(0f).setDuration(300).start();
            handler.postDelayed(() -> listener.onSolved(), 350);
            return;
        }
        // a catch: shrink a little and hop somewhere else
        moveTo(300, 20f);
    }

    private void moveTo(long durationMs, float spinDegrees) {
        float scale = rules.scale();
        float[] c = rules.nextCenter(baseW * scale, baseH * scale, table.getWidth(), table.getHeight(), centerX(), centerY());
        runaway.animate().cancel();
        runaway.animate()
                .translationX(c[0] - baseW / 2f)
                .translationY(c[1] - baseH / 2f)
                .scaleX(scale).scaleY(scale)
                .rotationBy(random.nextBoolean() ? spinDegrees : -spinDegrees)
                .setDuration(durationMs)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    public void start() { running = true; }

    @Override
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (runaway != null) runaway.cancelAnimations();
        for (FlipCardView d : decoys) d.cancelAnimations();
        decoder.shutdownNow();
    }
}
