package com.noam.photodream;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.graphics.Matrix;
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
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    private static final long FOCUS_MS = 350;
    private static final float FOCUS_MARGIN = 0.04f;  // free border around a focused photo
    private static final float SCRIM_ALPHA = 0.8f;

    /** One photo on the table. */
    private static final class Card {
        final ImageView view;
        final Photo photo;
        Animator drift;
        Bitmap small;                       // the card-sized bitmap, restored after focus mode
        float homeX, homeY, homeRot, homeScaleX, homeScaleY;   // where it lay before it was focused

        Card(ImageView view, Photo photo) {
            this.view = view;
            this.photo = photo;
        }

        void cancelAnimations() {
            view.animate().cancel();
            if (drift != null) {
                drift.cancel();
                drift = null;
            }
        }
    }

    /** A card: the photo with its border, plus a tiny heart in the corner when it is a favorite. */
    private static final class CardImageView extends androidx.appcompat.widget.AppCompatImageView {
        private final Paint heartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean favorite;
        private String ribbon;                           // "On this day" label, or null
        private final Paint ribbonBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ribbonText = new Paint(Paint.ANTI_ALIAS_FLAG);

        CardImageView(Context c) {
            super(c);
            ribbonBg.setColor(0xE0E0A458);
            ribbonText.setColor(0xFF1B1B1B);
            ribbonText.setFakeBoldText(true);
            heartPaint.setColor(0xFFE0405A);
            heartPaint.setShadowLayer(3f, 0f, 1f, 0x99000000);
        }

        void setRibbon(String text) {
            ribbon = text;
            invalidate();
        }

        void setFavorite(boolean on) {
            favorite = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (ribbon != null) {
                float size = Math.max(11f, getWidth() * 0.06f);
                ribbonText.setTextSize(size);
                float pad = size * 0.5f;
                float w = ribbonText.measureText(ribbon) + pad * 2;
                canvas.drawRoundRect(0f, size * 0.6f, w, size * 0.6f + size * 1.6f, size * 0.3f, size * 0.3f, ribbonBg);
                canvas.drawText(ribbon, pad, size * 0.6f + size * 1.2f, ribbonText);
            }
            if (!favorite) return;
            float size = Math.max(14f, getWidth() * 0.1f);
            heartPaint.setTextSize(size);
            canvas.drawText("\u2665", getWidth() - size * 1.4f, size * 1.3f, heartPaint);
        }
    }

    // settings
    private Prefs.Entry entry = Prefs.Entry.RANDOM;
    private int maxCards = 8;
    private int cardSizePercent = 50;
    private int maxRotation = 12;
    private boolean drift = true;
    private Map<String, Integer> frameColors = new HashMap<>();   // source id -> frame color
    private PhotoMarksStore marks;
    private PhotoQueue.Weights weights;
    private java.util.function.Predicate<Photo> onThisDay;
    private LinearLayout focusButtons;                 // ♥ Favorite and ✕ Hide, shown while a photo is focused
    private TextView btnFavorite;
    private TextView undoPill;
    private long intervalMs = 10_000;
    private PhotoDisplay.Listener listener;

    // state
    private final List<Card> cards = new ArrayList<>();   // oldest first
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AsyncBitmapLoader cardLoader;      // photos for new cards
    private final AsyncBitmapLoader focusLoader;     // the sharp, screen-size version of a focused card
    private final Random random = new Random();
    private final GestureDetector gestures;
    private final TextView messageView;
    private final float density;

    private PhotoQueue queue = new PhotoQueue(new ArrayList<>(), new Random());
    private boolean running;

    // focus mode (double-tap a photo)
    private Card focused;
    private View scrim;
    private boolean doubleTapHandled;      // the second tap of a double-tap must not start a drag

    // dragging
    private Card dragged;
    private float lastX, lastY;
    private VelocityTracker velocity;

    private final Runnable advance = this::addNextCard;

    public PhotoTableView(Context context) { this(context, null); }

    public PhotoTableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        cardLoader = new AsyncBitmapLoader(context);
        focusLoader = new AsyncBitmapLoader(context);
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
                if (dragged == null && focused == null && listener != null) listener.onExitRequested();
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                doubleTapHandled = true;
                if (focused != null) {
                    unfocus();
                } else {
                    Card hit = cardAt(e.getX(), e.getY());
                    if (hit != null) focus(hit);
                    else if (listener != null) listener.onExitRequested();   // double-tap on the black table
                }
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                // while a photo is focused: swipe sideways to look at the next / previous one
                if (focused == null || e1 == null || Math.abs(vx) < Math.abs(vy)) return false;
                int i = cards.indexOf(focused);
                if (i < 0 || cards.size() < 2) return false;
                int next = Math.floorMod(i + (vx < 0 ? 1 : -1), cards.size());
                Card target = cards.get(next);
                unfocus();
                focus(target);
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
    /** Where favorites and hidden photos are kept; without it the focus buttons are not shown. */
    public void setMarks(PhotoMarksStore store) { marks = store; }

    @Override
    public void setWeights(PhotoQueue.Weights w) { weights = w; }

    /** Which photos get the "On this day" ribbon (null = none). */
    public void setOnThisDay(java.util.function.Predicate<Photo> p) { onThisDay = p; }
    /** Border color per source id; sources without an entry keep the classic white frame. */
    public void setFrameColors(Map<String, Integer> colors) { frameColors = new HashMap<>(colors); }

    @Override
    public void setIntervalSeconds(int seconds) { intervalMs = Math.max(1, seconds) * 1000L; }

    @Override
    public void setListener(PhotoDisplay.Listener l) { listener = l; }

    // ---------------------------------------------------------------- PhotoDisplay

    @Override
    public void start(List<Photo> newPhotos) {
        stop();
        removeAllCards();
        queue = new PhotoQueue(newPhotos, random);
        queue.setReshuffleOnWrap(new Prefs(getContext()).isShuffle());
        queue.setWeights(weights);
        running = true;
        if (queue.isEmpty()) {
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
        cardLoader.cancel();
        focusLoader.cancel();
        handler.removeCallbacksAndMessages(null);
        for (Card c : cards) c.cancelAnimations();
        if (scrim != null) scrim.animate().cancel();
        handler.removeCallbacks(hideUndo);
        endDrag();
    }

    @Override
    public void release() {
        stop();
        cardLoader.release();
        focusLoader.release();
        removeAllCards();
    }

    // ---------------------------------------------------------------- adding cards

    private void addNextCard() {
        if (!running || queue.isEmpty() || focused != null) return;   // no new cards while one is focused
        handler.removeCallbacks(advance);
        final Photo photo = queue.next();
        if (photo == null) return;
        final int longSide = cardLongSide();

        cardLoader.load(photo.uri, longSide, bmp -> {
            if (!running) return;
            if (focused != null) {
                // the user focused a photo while this one was loading: put it back in the queue
                queue.previous();
                return;
            }
            if (bmp == null) {
                queue.markFailed(photo);          // unreadable: never pick it again
                handler.postDelayed(advance, RETRY_MS);
                return;
            }
            placeCard(bmp, longSide, photo);
            handler.postDelayed(advance, intervalMs);
        });
    }

    private void placeCard(Bitmap bmp, int longSide, Photo photo) {
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

        CardImageView iv = new CardImageView(getContext());
        iv.setFavorite(marks != null && marks.marks().isFavorite(photo.key()));
        iv.setRibbon(onThisDay != null && onThisDay.test(photo) ? getContext().getString(R.string.on_this_day) : null);
        iv.setLayoutParams(new LayoutParams(w, h, Gravity.TOP | Gravity.START));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);   // trims a sliver so the border never distorts the photo
        iv.setBackgroundColor(frameColors.getOrDefault(photo.sourceId, Color.WHITE));
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

        Card card = new Card(iv, photo);
        card.small = bmp;
        if (listener != null) listener.onPhotoShown(photo);
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
        if (focusButtons != null) focusButtons.setVisibility(GONE);
        if (undoPill != null) undoPill.setVisibility(GONE);
        if (scrim != null) {
            scrim.animate().cancel();
            removeView(scrim);
            scrim = null;
        }
        focused = null;
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
                // focused photos can't be dragged; the 2nd tap of a double-tap must not pick a card up
                Card hit = (focused != null || doubleTapHandled) ? null : cardAt(e.getX(), e.getY());
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
                doubleTapHandled = false;
                if (dragged == null) performClick();   // tap on the empty table: lets accessibility services see it
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
                doubleTapHandled = false;
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

    // ---------------------------------------------------------------- focus mode

    /**
     * Double-tap: remember where the card lies, then glide it to the middle of the screen,
     * straighten it and scale it up to fit. Everything else fades under a dark scrim, no new
     * cards arrive and nothing drifts until {@link #unfocus()}.
     */
    private void focus(Card card) {
        focused = card;
        handler.removeCallbacks(advance);
        for (Card c : cards) {
            c.cancelAnimations();
            // a card caught half-way through its entry animation settles where it is
            c.view.setAlpha(1f);
            c.view.setScaleX(1f);
            c.view.setScaleY(1f);
        }
        endDrag();

        View v = card.view;
        card.homeX = v.getTranslationX();
        card.homeY = v.getTranslationY();
        card.homeRot = v.getRotation();
        card.homeScaleX = v.getScaleX();
        card.homeScaleY = v.getScaleY();

        if (scrim == null) {
            scrim = new View(getContext());
            scrim.setBackgroundColor(Color.BLACK);
            scrim.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            scrim.setAlpha(0f);
            addView(scrim);
        }
        scrim.animate().cancel();
        scrim.bringToFront();
        v.bringToFront();
        messageView.bringToFront();
        scrim.animate().alpha(SCRIM_ALPHA).setDuration(FOCUS_MS).start();

        int w = v.getLayoutParams().width, h = v.getLayoutParams().height;
        float scale = FitMath.fitScale(w, h, tableWidth(), tableHeight(), FOCUS_MARGIN);
        v.setTranslationZ(12 * density);
        showFocusButtons(card);
        v.animate().translationX((tableWidth() - w) / 2f).translationY((tableHeight() - h) / 2f)
                .rotation(0f).scaleX(scale).scaleY(scale)
                .setDuration(FOCUS_MS).setInterpolator(new DecelerateInterpolator()).start();

        // cards are decoded at card size – load the photo at the size it is shown now so it looks sharp
        final int longSide = Math.round(Math.max(w, h) * scale);
        focusLoader.load(card.photo.uri, longSide, big -> {
            if (big == null || focused != card) return;
            card.view.setImageBitmap(big);
        });
    }

    /** Double-tap again: fly back to exactly where it was and let the table carry on. */
    private void unfocus() {
        final Card card = focused;
        if (card == null) return;
        focused = null;
        focusLoader.cancel();                 // a late sharp bitmap must not replace the small one
        View v = card.view;
        v.animate().translationX(card.homeX).translationY(card.homeY).rotation(card.homeRot)
                .scaleX(card.homeScaleX).scaleY(card.homeScaleY)
                .setDuration(FOCUS_MS).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    v.setTranslationZ(0f);
                    if (card.small != null) card.view.setImageBitmap(card.small);   // drop the big bitmap
                    startDrift(card);
                }).start();
        endFocusUi(card);
    }

    /** Scrim, buttons, other cards and the timer go back to normal after a focus ends (by unfocus or hide). */
    private void endFocusUi(Card except) {
        if (focusButtons != null) focusButtons.setVisibility(GONE);
        if (scrim != null) {
            final View s = scrim;
            s.animate().alpha(0f).setDuration(FOCUS_MS).withEndAction(() -> {
                if (scrim == s && focused == null) {
                    removeView(s);
                    scrim = null;
                }
            }).start();
        }
        for (Card c : cards) if (c != except) startDrift(c);   // the others float again too
        if (running) handler.postDelayed(advance, intervalMs);
    }

    // ---------------------------------------------------------------- favorite / hide (focus mode)

    private void showFocusButtons(Card card) {
        if (marks == null) return;
        if (focusButtons == null) {
            focusButtons = new LinearLayout(getContext());
            focusButtons.setOrientation(LinearLayout.HORIZONTAL);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = Math.round(24 * density);
            focusButtons.setLayoutParams(lp);
            btnFavorite = roundButton("\u2665", getContext().getString(R.string.photo_favorite));
            TextView btnHide = roundButton("\u2715", getContext().getString(R.string.photo_hide));
            btnFavorite.setOnClickListener(v -> toggleFavoriteOfFocused());
            btnHide.setOnClickListener(v -> hideFocused());
            focusButtons.addView(btnFavorite);
            focusButtons.addView(btnHide);
            addView(focusButtons);
        }
        focusButtons.setVisibility(VISIBLE);
        focusButtons.bringToFront();
        showHeartState(marks.marks().isFavorite(card.photo.key()));
    }

    private TextView roundButton(String glyph, String description) {
        int size = Math.round(56 * density);
        TextView b = new TextView(getContext());
        b.setText(glyph);
        b.setTextSize(24);
        b.setGravity(Gravity.CENTER);
        b.setContentDescription(description);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xCC202020);
        bg.setStroke(Math.round(1.5f * density), 0x66FFFFFF);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(Math.round(10 * density), 0, Math.round(10 * density), 0);
        b.setLayoutParams(lp);
        b.setClickable(true);
        return b;
    }

    private void showHeartState(boolean favorite) {
        btnFavorite.setTextColor(favorite ? 0xFFE0405A : 0xFFFFFFFF);
        btnFavorite.setSelected(favorite);
    }

    private void toggleFavoriteOfFocused() {
        Card card = focused;
        if (card == null || marks == null) return;
        boolean now = marks.marks().toggleFavorite(card.photo.key());
        marks.save();
        ((CardImageView) card.view).setFavorite(now);
        showHeartState(now);
    }

    /** Hide: the photo leaves the table for good; an Undo pill stays for 5 seconds. */
    private void hideFocused() {
        final Card card = focused;
        if (card == null || marks == null) return;
        marks.marks().hide(card.photo.key());
        marks.save();
        queue.exclude(card.photo);
        focused = null;
        focusLoader.cancel();
        cards.remove(card);
        card.cancelAnimations();
        card.view.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(FOCUS_MS)
                .withEndAction(() -> discard(card.view)).start();
        endFocusUi(card);
        showUndo(card.photo);
    }

    private final Runnable hideUndo = () -> {
        if (undoPill != null) undoPill.setVisibility(GONE);
    };

    private void showUndo(Photo photo) {
        if (undoPill == null) {
            undoPill = new TextView(getContext());
            undoPill.setTextColor(0xFFFFFFFF);
            undoPill.setTextSize(16);
            undoPill.setGravity(Gravity.CENTER);
            int padH = Math.round(20 * density), padV = Math.round(12 * density);
            undoPill.setPadding(padH, padV, padH, padV);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(40 * density);
            bg.setColor(0xDD202020);
            undoPill.setBackground(bg);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = Math.round(24 * density);
            undoPill.setLayoutParams(lp);
            undoPill.setMinimumHeight(Math.round(48 * density));
            addView(undoPill);
        }
        undoPill.setText(R.string.photo_hidden_undo);
        undoPill.setOnClickListener(v -> {
            marks.marks().unhide(photo.key());
            marks.save();
            queue.include(photo);
            undoPill.setVisibility(GONE);
            handler.removeCallbacks(hideUndo);
        });
        undoPill.setVisibility(VISIBLE);
        undoPill.bringToFront();
        handler.removeCallbacks(hideUndo);
        handler.postDelayed(hideUndo, 5000);
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
