package com.noam.photodream.alarm.challenge;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.noam.photodream.BitmapLoader;
import com.noam.photodream.Photo;
import com.noam.photodream.R;
import com.noam.photodream.alarm.Alarm;
import com.noam.photodream.alarm.AlarmChallenge;
import com.noam.photodream.describe.Description;
import com.noam.photodream.describe.DescriptionCache;
import com.noam.photodream.describe.DescriptionStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Find the described photo": a banner describes one photo (a Gemini Nano sentence if there is one,
 * otherwise "Find the photo with: dog · beach · sunset") and you tap it among 4 / 6 / 9 photos,
 * 1 / 2 / 3 times. A wrong tap shakes the card, everything flies away and the round starts again
 * with a new target. The fairness rules live in {@link RoundPicker}.
 *
 * If there are not enough described photos, it says so in one line and plays "Flip them all" instead.
 */
public class FindDescribedChallenge implements AlarmChallenge {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final List<FlipCardView> cards = new ArrayList<>();
    private final Map<String, Photo> photoByKey = new HashMap<>();
    private final Map<String, Description> descriptionByKey = new HashMap<>();

    private AlarmChallenge fallback;            // set when we play "Flip them all" instead
    private Context context;
    private Listener listener;
    private FrameLayout table;
    private TextView banner;
    private List<RoundPicker.Candidate> pool;
    private RoundPicker.Round round;
    private int cardCount, rounds, roundsDone;
    private boolean built, running, busy;

    @Override
    public View createView(Context ctx, List<Photo> photos, Alarm.Difficulty difficulty, Listener l) {
        context = ctx;
        listener = l;
        switch (difficulty) {
            case EASY: cardCount = 4; rounds = 1; break;
            case HARD: cardCount = 9; rounds = 3; break;
            default: cardCount = 6; rounds = 2;
        }

        DescriptionCache cache = DescriptionStore.get(ctx).cache();
        pool = new ArrayList<>();
        for (Photo p : photos) {
            Description d = cache.get(p.key());
            if (d == null || d.labels.isEmpty()) continue;
            pool.add(new RoundPicker.Candidate(p.key(), d.labels));
            photoByKey.put(p.key(), p);
            descriptionByKey.put(p.key(), d);
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        if (RoundPicker.pick(pool, cardCount, random) == null) {
            // not enough described photos: say why in one line, then play the flip game
            TextView why = new TextView(ctx);
            why.setText(R.string.find_fallback);
            why.setGravity(Gravity.CENTER);
            why.setTextColor(0xFFE0A458);
            root.addView(why, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            fallback = new FlipChallenge();
            root.addView(fallback.createView(ctx, photos, difficulty, l),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        banner = new TextView(ctx);
        banner.setGravity(Gravity.CENTER);
        banner.setTextColor(0xFFFFFFFF);
        banner.setTextSize(20);
        banner.setMaxLines(4);
        banner.setPadding(0, 0, 0, Math.round(8 * ctx.getResources().getDisplayMetrics().density));
        root.addView(banner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        table = new FrameLayout(ctx);
        root.addView(table, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        table.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            if (!built && right - left > 0 && bottom - top > 0) {
                built = true;
                handler.post(this::startRound);      // not while the layout pass is still running
            }
        });
        l.onProgress(0, rounds);
        return root;
    }

    // ---------------------------------------------------------------- rounds

    private void startRound() {
        round = RoundPicker.pick(pool, cardCount, random);
        if (round == null) return;                          // cannot happen: checked in createView
        busy = false;
        banner.setText(promptFor(round.target.key));
        for (FlipCardView c : cards) table.removeView(c);
        cards.clear();

        List<CardGrid.Slot> slots = CardGrid.layout(cardCount, table.getWidth(), table.getHeight(), 0.75f, 0.7f, random);
        for (int i = 0; i < cardCount; i++) {
            final int index = i;
            CardGrid.Slot s = slots.get(i);
            Photo photo = photoByKey.get(round.cards.get(i).key);
            FlipCardView card = new FlipCardView(context);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(s.w), Math.round(s.h));
            lp.leftMargin = Math.round(s.x);
            lp.topMargin = Math.round(s.y);
            card.setLayoutParams(lp);
            card.setFaceUp(true);
            card.setRotation((random.nextFloat() - 0.5f) * 8f);
            card.setContentDescription(context.getString(R.string.flip_card, i + 1));
            card.setOnClickListener(v -> onTap(index));
            card.setAlpha(0f);
            table.addView(card);
            cards.add(card);
            card.animate().alpha(1f).setDuration(250).start();
            int longSide = Math.round(Math.max(s.w, s.h));
            decoder.execute(() -> {
                Bitmap bmp = BitmapLoader.load(context, photo.uri, longSide);
                handler.post(() -> {
                    if (bmp != null) card.setPhoto(bmp);
                });
            });
        }
    }

    /** The sentence if Gemini Nano wrote one, otherwise the best labels. */
    private String promptFor(String key) {
        Description d = descriptionByKey.get(key);
        if (d.hasSentence()) return context.getString(R.string.find_prompt_sentence, d.text);
        List<String> top = d.labels.subList(0, Math.min(RoundPicker.TOP_LABELS, d.labels.size()));
        return context.getString(R.string.find_prompt_labels, String.join(" · ", top));
    }

    private void onTap(int index) {
        if (!running || busy || round == null) return;
        busy = true;
        if (index == round.targetIndex()) {
            roundsDone++;
            listener.onProgress(roundsDone, rounds);
            if (roundsDone >= rounds) {
                handler.postDelayed(() -> flyAway(() -> listener.onSolved()), 400);
            } else {
                handler.postDelayed(() -> flyAway(this::startRound), 400);
            }
        } else {
            // wrong: shake that card, then everything flies away and this round starts again with a new target
            ObjectAnimator shake = ObjectAnimator.ofFloat(cards.get(index), View.TRANSLATION_X, 0f, 24f);
            shake.setInterpolator(new CycleInterpolator(3));
            shake.setDuration(400);
            shake.start();
            handler.postDelayed(() -> flyAway(this::startRound), 500);
        }
    }

    private void flyAway(Runnable then) {
        for (FlipCardView c : cards) {
            float dx = (random.nextFloat() - 0.5f) * table.getWidth() * 2f;
            float dy = (random.nextFloat() - 0.5f) * table.getHeight() * 2f;
            c.animate().translationXBy(dx).translationYBy(dy).rotationBy((random.nextFloat() - 0.5f) * 90f)
                    .alpha(0f).setDuration(450).start();
        }
        handler.postDelayed(then, 480);
    }

    @Override
    public void start() {
        running = true;
        if (fallback != null) fallback.start();
    }

    @Override
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        for (FlipCardView c : cards) c.cancelAnimations();
        decoder.shutdownNow();
        if (fallback != null) fallback.stop();
    }
}
