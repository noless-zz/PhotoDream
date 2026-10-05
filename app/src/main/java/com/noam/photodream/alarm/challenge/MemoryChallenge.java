package com.noam.photodream.alarm.challenge;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.noam.photodream.BitmapLoader;
import com.noam.photodream.Photo;
import com.noam.photodream.R;
import com.noam.photodream.alarm.Alarm;
import com.noam.photodream.alarm.AlarmChallenge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Memory pairs" with your own photos: Easy 3 pairs, Medium 4, Hard 6. Needs distinct photos; with
 * fewer than the difficulty asks for, it uses as many pairs as there are photos (never fewer than 3,
 * which the registry guarantees).
 */
public class MemoryChallenge implements AlarmChallenge {

    private static final long MISMATCH_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final List<FlipCardView> cards = new ArrayList<>();

    private Context context;
    private Listener listener;
    private MemoryGame game;
    private FrameLayout table;
    private List<Photo> pictures;            // distinct photos, one per pair
    private boolean built, running;

    @Override
    public View createView(Context ctx, List<Photo> photos, Alarm.Difficulty difficulty, Listener l) {
        context = ctx;
        listener = l;
        int wanted = difficulty == Alarm.Difficulty.HARD ? 6 : difficulty == Alarm.Difficulty.MEDIUM ? 4 : 3;
        pictures = distinct(photos, wanted);
        game = new MemoryGame(pictures.size(), random);

        table = new FrameLayout(ctx);
        table.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            if (!built && right - left > 0 && bottom - top > 0) {
                built = true;
                buildCards(right - left, bottom - top);
            }
        });
        l.onProgress(0, game.pairs());
        return table;
    }

    /** Up to {@code max} photos that are really different (same key = same photo). */
    private static List<Photo> distinct(List<Photo> photos, int max) {
        Map<String, Photo> byKey = new LinkedHashMap<>();
        for (Photo p : photos) {
            byKey.putIfAbsent(p.key(), p);
            if (byKey.size() == max) break;
        }
        return new ArrayList<>(byKey.values());
    }

    private void buildCards(int w, int h) {
        List<CardGrid.Slot> slots = CardGrid.layout(game.cards(), w, h, 0.75f, 0.6f, random);
        for (int i = 0; i < game.cards(); i++) {
            final int index = i;
            CardGrid.Slot s = slots.get(i);
            FlipCardView card = new FlipCardView(context);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(s.w), Math.round(s.h));
            lp.leftMargin = Math.round(s.x);
            lp.topMargin = Math.round(s.y);
            card.setLayoutParams(lp);
            card.setRotation((random.nextFloat() - 0.5f) * 6f);
            card.setContentDescription(context.getString(R.string.flip_card, i + 1));
            card.setOnClickListener(v -> onTap(index));
            table.addView(card);
            cards.add(card);
            Photo photo = pictures.get(game.pairIdAt(i));
            int longSide = Math.round(Math.max(s.w, s.h));
            decoder.execute(() -> {
                Bitmap bmp = BitmapLoader.load(context, photo.uri, longSide);
                handler.post(() -> {
                    if (bmp != null && running) card.setPhoto(bmp);
                });
            });
        }
    }

    private void onTap(int index) {
        if (!running) return;
        MemoryGame.Result r = game.select(index);
        if (r == MemoryGame.Result.IGNORED) return;
        cards.get(index).flipTo(true);
        switch (r) {
            case MATCH:
                listener.onProgress(game.pairsFound(), game.pairs());
                if (game.isSolved()) handler.postDelayed(this::flyAway, 600);
                break;
            case MISMATCH:
                handler.postDelayed(() -> {            // show the mismatch for a second, then turn both back
                    int[] pair = game.mismatchedCards();
                    if (pair == null) return;
                    for (int c : pair) cards.get(c).flipTo(false);
                    game.resolveMismatch();
                }, MISMATCH_MS);
                break;
            default:
                break;
        }
    }

    private void flyAway() {
        for (FlipCardView c : cards) {
            float dx = (random.nextFloat() - 0.5f) * table.getWidth() * 2f;
            float dy = table.getHeight() * (0.6f + random.nextFloat() * 0.6f);
            c.animate().translationX(dx).translationY(dy).rotationBy((random.nextFloat() - 0.5f) * 120f)
                    .alpha(0f).setDuration(600).start();
        }
        handler.postDelayed(() -> listener.onSolved(), 650);
    }

    @Override
    public void start() { running = true; }

    @Override
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        for (FlipCardView c : cards) c.cancelAnimations();
        decoder.shutdownNow();
    }
}
