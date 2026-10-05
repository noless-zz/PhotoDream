package com.noam.photodream;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Photo table" mode: printed-looking photos (white border, shadow, slight
 * tilt) drop onto a dark table one at a time and pile up. When there are more
 * than {@code maxCards}, the oldest one fades away.
 *
 * Touch: drag a photo to move it, flick it to throw it off the table,
 * long-press anywhere to exit.
 *
 * Each card is a plain ImageView. Its position on the table is stored in
 * translationX/Y (its layout position is always the top-left corner), so all
 * movement is done with cheap view properties and ViewPropertyAnimator.
 */
public class PhotoTableView extends FrameLayout implements PhotoDisplay {

    private static final long RETRY_MS = 300;
    private static final long EXIT_MS = 600;
    private static final long FLICK_MS = 400;
    private static final float FLICK_SPEED = 2500f;   // px per second

    /** One photo on the table. */
    private static final class Card {
        final ImageView view;
        Animator drift;

        Card(ImageView view) { this.view = view; }

        void cancelAnimations() {
            view.animate().cancel();
            if (drift != null) {
                drift.cancel();
                drift = null;
            }
        }
    }

    // settings
    private Prefs.Entry entry = Prefs.Entry.RANDOM;
    private int maxCards = 8;
    private int cardSizePercent = 50;
    private int maxRotation = 12;
    private boolean drift = true;
    private long intervalMs = 10_000;
    private PhotoDisplay.Listener listener;

    // state
    private final List<Card> cards = new ArrayList<>();   // oldest first
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final GestureDetector gestures;
    private final TextView messageView;
    private final float density;

    private List<Photo> photos = new ArrayList<>();
    private int index = -1;
    private int requestId;
    private int failuresInARow;
    private boolean running;

    // dragging
    private Card dragged;
    private float lastX, lastY;
    private VelocityTracker velocity;

    private final Runnable advance = this::addNextCard;

    public PhotoTableView(Context context) { this(context, null); }

    public PhotoTableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setClipChildren(false);

        messageView = new TextView(context);
        messageView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        messageView.setGravity(Gravity.CENTER);
        messageView.setTextColor(0xE6FFFFFF);
        messageView.setTextSize(20);
        messageView.setVisibility(GONE);
        addView(messageView);

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public void onLongPress(MotionEvent e) {
                // Only on the empty table: holding a photo before dragging it must not exit.
                if (dragged == null && listener != null) listener.onExitRequested();
            }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                performClick();
                return true;
            }
        });
    }

    // ---------------------------------------------------------------- settings

    public void setEntry(Prefs.Entry e) { entry = e; }
    public void setMaxCards(int n) { maxCards = clamp(n, 3, 20); }
    public void setCardSizePercent(int p) { cardSizePercent = clamp(p, 30, 80); }
    public void setMaxRotation(int deg) { maxRotation = clamp(deg, 0, 30); }
    public void setDrift(boolean on) { drift = on; }

    @Override
    public void setIntervalSeconds(int seconds) { intervalMs = Math.max(1, seconds) * 1000L; }

    @Override
    public void setListener(PhotoDisplay.Listener l) { listener = l; }

    // ---------------------------------------------------------------- PhotoDisplay

    @Override
    public void start(List<Photo> newPhotos) {
        stop();
        removeAllCards();
        photos = new ArrayList<>(newPhotos);
        index = -1;
        failuresInARow = 0;
        running = true;
        if (photos.isEmpty()) {
            messageView.setText(R.string.msg_no_photos);
            messageView.setVisibility(VISIBLE);
            return;
        }
        messageView.setVisibility(GONE);
        addNextCard();
    }

    @Override
    public void stop() {
        running = false;
        requestId++;
        handler.removeCallbacksAndMessages(null);
        for (Card c : cards) c.cancelAnimations();
        endDrag();
    }

    @Override
    public void release() {
        stop();
        loader.shutdownNow();
        removeAllCards();
    }

    // ---------------------------------------------------------------- adding cards

    private void addNextCard() {
        if (!running || photos.isEmpty()) return;
        handler.removeCallbacks(advance);
        index = (index + 1) % photos.size();
        final Uri uri = photos.get(index).uri;
        final int id = ++requestId;
        final int longSide = cardLongSide();

        loader.execute(() -> {
            Bitmap bmp = BitmapLoader.load(getContext(), uri, longSide);
            handler.post(() -> {
                if (id != requestId || !running) return;
                if (bmp == null) {
                    if (++failuresInARow < photos.size()) handler.postDelayed(advance, RETRY_MS);
                    return;
                }
                failuresInARow = 0;
                placeCard(bmp, longSide);
                handler.postDelayed(advance, intervalMs);
            });
        });
    }

    private void placeCard(Bitmap bmp, int longSide) {
        // keep the photo's own shape: long side = longSide, other side follows
        int w, h;
        if (bmp.getWidth() >= bmp.getHeight()) {
            w = longSide;
            h = Math.round(longSide * (float) bmp.getHeight() / bmp.getWidth());
        } else {
            h = longSide;
            w = Math.round(longSide * (float) bmp.getWidth() / bmp.getHeight());
        }
        int pad = Math.max(6, Math.round(w * 0.04f));

        ImageView iv = new ImageView(getContext());
        iv.setLayoutParams(new LayoutParams(w, h, Gravity.TOP | Gravity.START));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);   // trims a sliver so the border never distorts the photo
        iv.setBackgroundColor(Color.WHITE);
        iv.setPadding(pad, pad, pad, pad);
        iv.setCropToPadding(true);
        iv.setImageBitmap(bmp);
        iv.setElevation(8 * density);
        iv.setOutlineProvider(ViewOutlineProvider.BOUNDS);

        // random spot: centre kept far enough inside that ~90% of the card shows
        int tableW = tableWidth(), tableH = tableHeight();
        float cx = randomBetween(w * 0.4f, tableW - w * 0.4f);
        float cy = randomBetween(h * 0.4f, tableH - h * 0.4f);
        float x = cx - w / 2f;
        float y = cy - h / 2f;
        float rot = maxRotation == 0 ? 0 : randomBetween(-maxRotation, maxRotation);

        Card card = new Card(iv);
        cards.add(card);
        addView(iv);
        messageView.bringToFront();

        playEntry(card, x, y, rot);
        removeExtraCards();
    }

    private void playEntry(Card card, float x, float y, float rot) {
        View v = card.view;
        Prefs.Entry e = entry;
        if (e == Prefs.Entry.RANDOM) {
            Prefs.Entry[] all = {Prefs.Entry.DROP, Prefs.Entry.FLY_IN, Prefs.Entry.POP, Prefs.Entry.FADE};
            e = all[random.nextInt(all.length)];
        }

        // final state
        v.setTranslationX(x);
        v.setTranslationY(y);
        v.setRotation(rot);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setAlpha(1f);

        switch (e) {
            case DROP:
                v.setScaleX(1.6f);
                v.setScaleY(1.6f);
                v.setAlpha(0f);
                v.setRotation(rot + 10f);
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(rot)
                        .setDuration(800).setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> startDrift(card));
                break;

            case FLY_IN: {
                int tw = tableWidth(), th = tableHeight();
                float fromX = x, fromY = y;
                switch (random.nextInt(4)) {
                    case 0: fromX = -v.getLayoutParams().width - 50; break;   // left
                    case 1: fromX = tw + 50; break;                            // right
                    case 2: fromY = -v.getLayoutParams().height - 50; break;  // top
                    default: fromY = th + 50; break;                           // bottom
                }
                v.setTranslationX(fromX);
                v.setTranslationY(fromY);
                v.setRotation(rot + (random.nextBoolean() ? 90f : -90f));
                v.animate().translationX(x).translationY(y).rotation(rot)
                        .setDuration(900).setInterpolator(new DecelerateInterpolator(1.5f))
                        .withEndAction(() -> startDrift(card));
                break;
            }

            case POP:
                v.setScaleX(0f);
                v.setScaleY(0f);
                v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(700).setInterpolator(new OvershootInterpolator(1.4f))
                        .withEndAction(() -> startDrift(card));
                break;

            case FADE:
            default:
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(900)
                        .withEndAction(() -> startDrift(card));
                break;
        }
    }

    /** Oldest cards leave when the table is too full. */
    private void removeExtraCards() {
        while (cards.size() > maxCards) {
            Card old = cards.remove(0);
            if (old == dragged) endDrag();
            old.cancelAnimations();
            old.view.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                    .setDuration(EXIT_MS)
                    .withEndAction(() -> discard(old.view));
        }
    }

    private void discard(ImageView v) {
        removeView(v);
        v.setImageDrawable(null);   // let the bitmap be garbage-collected
    }

    private void removeAllCards() {
        for (Card c : cards) {
            c.cancelAnimations();
            discard(c.view);
        }
        cards.clear();
        // also cards that were still animating out
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof ImageView) {
                child.animate().cancel();
                discard((ImageView) child);
            }
        }
    }

    // ---------------------------------------------------------------- floating

    private void startDrift(Card card) {
        if (!drift || !running || !cards.contains(card) || card == dragged) return;
        View v = card.view;
        float range = tableWidth() * (0.02f + random.nextFloat() * 0.02f);
        float dx = random.nextBoolean() ? range : -range;
        float dy = (random.nextFloat() * 2f - 1f) * range;
        float dr = randomBetween(-3f, 3f);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, v.getTranslationX(), v.getTranslationX() + dx),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, v.getTranslationY(), v.getTranslationY() + dy),
                PropertyValuesHolder.ofFloat(View.ROTATION, v.getRotation(), v.getRotation() + dr));
        a.setDuration(20_000 + random.nextInt(10_000));
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.start();
        card.drift = a;
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Card hit = cardAt(e.getX(), e.getY());
                if (hit != null) {
                    dragged = hit;
                    hit.cancelAnimations();
                    hit.view.setAlpha(1f);
                    hit.view.setScaleX(1f);
                    hit.view.setScaleY(1f);
                    hit.view.bringToFront();
                    messageView.bringToFront();
                    hit.view.setTranslationZ(8 * density);   // lift it while held
                    lastX = e.getX();
                    lastY = e.getY();
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(e);
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE:
                if (dragged != null) {
                    velocity.addMovement(e);
                    View v = dragged.view;
                    v.setTranslationX(v.getTranslationX() + e.getX() - lastX);
                    v.setTranslationY(v.getTranslationY() + e.getY() - lastY);
                    lastX = e.getX();
                    lastY = e.getY();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (dragged != null) {
                    velocity.addMovement(e);
                    velocity.computeCurrentVelocity(1000);
                    float vx = velocity.getXVelocity();
                    float vy = velocity.getYVelocity();
                    Card card = dragged;
                    endDrag();
                    if (Math.hypot(vx, vy) > FLICK_SPEED) {
                        flickAway(card, vx, vy);
                    } else {
                        startDrift(card);
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                Card card = dragged;
                endDrag();
                if (card != null) startDrift(card);
                return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void endDrag() {
        if (dragged != null) dragged.view.setTranslationZ(0f);
        dragged = null;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    /** Throw the card off the table in the direction of the flick. */
    private void flickAway(Card card, float vx, float vy) {
        cards.remove(card);
        card.cancelAnimations();
        View v = card.view;
        float speed = (float) Math.hypot(vx, vy);
        float distance = (float) Math.hypot(tableWidth(), tableHeight());
        v.animate()
                .translationXBy(vx / speed * distance)
                .translationYBy(vy / speed * distance)
                .rotationBy(vx > 0 ? 25f : -25f)
                .setDuration(FLICK_MS)
                .setInterpolator(new AccelerateInterpolator(0.6f))
                .withEndAction(() -> discard((ImageView) v));
    }

    /** Top-most card under the finger, taking each card's rotation into account. */
    private Card cardAt(float x, float y) {
        float[] pt = new float[2];
        Matrix inverse = new Matrix();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            Card card = cardFor(child);
            if (card == null) continue;
            pt[0] = x - child.getLeft();
            pt[1] = y - child.getTop();
            if (!child.getMatrix().invert(inverse)) continue;
            inverse.mapPoints(pt);
            if (pt[0] >= 0 && pt[1] >= 0 && pt[0] <= child.getWidth() && pt[1] <= child.getHeight()) {
                return card;
            }
        }
        return null;
    }

    private Card cardFor(View v) {
        for (Card c : cards) if (c.view == v) return c;
        return null;
    }

    // ---------------------------------------------------------------- helpers

    private int tableWidth() {
        return getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
    }

    private int tableHeight() {
        return getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
    }

    private int cardLongSide() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int shortSide = Math.min(tableWidth(), tableHeight());
        if (shortSide <= 0) shortSide = Math.min(dm.widthPixels, dm.heightPixels);
        return Math.max(100, shortSide * cardSizePercent / 100);
    }

    private float randomBetween(float min, float max) {
        if (max <= min) return (min + max) / 2f;
        return min + random.nextFloat() * (max - min);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
