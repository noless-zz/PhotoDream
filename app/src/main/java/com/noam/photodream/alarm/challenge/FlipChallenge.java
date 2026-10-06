package com.noam.photodream.alarm.challenge;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
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
 * "Flip them all": photos lie face down; tap each one to turn it up. Medium and Hard turn cards
 * back after a while, so you have to be awake and quick. See {@link FlipGame} for the rules.
 */
public class FlipChallenge implements AlarmChallenge {

    private static final long TICK_MS = 400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final List<FlipCardView> cards = new ArrayList<>();

    private Context context;
    private Listener listener;
    private FlipGame game;
    private FrameLayout table;
    private List<Photo> photos;
    private int cardCount;
    private boolean running, built;

    @Override
    public View createView(Context ctx, List<Photo> photoList, Alarm.Difficulty difficulty, Listener l) {
        context = ctx;
        listener = l;
        photos = photoList;
        switch (difficulty) {
            case EASY: cardCount = 6; break;
            case HARD: cardCount = 12; break;
            default: cardCount = 9;
        }
        long flipBackMs = difficulty == Alarm.Difficulty.HARD ? 4000 : difficulty == Alarm.Difficulty.MEDIUM ? 8000 : 0;
        game = new FlipGame(cardCount, flipBackMs, SystemClock::elapsedRealtime);

        table = new FrameLayout(ctx);
        // the size is only known after layout: place the cards then
        table.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            if (!built && right - left > 0 && bottom - top > 0) {
                built = true;
                buildCards(right - left, bottom - top);
            }
        });
        l.onProgress(0, cardCount);
        return table;
    }

    private void buildCards(int w, int h) {
        List<CardGrid.Slot> slots = CardGrid.layout(cardCount, w, h, 0.75f, 0.8f, random);
        for (int i = 0; i < cardCount; i++) {
            final int index = i;
            CardGrid.Slot s = slots.get(i);
            FlipCardView card = new FlipCardView(context);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(s.w), Math.round(s.h));
            lp.leftMargin = Math.round(s.x);
            lp.topMargin = Math.round(s.y);
            card.setLayoutParams(lp);
            card.setRotation((random.nextFloat() - 0.5f) * 8f);       // a slight tilt, like prints on a table
            card.setContentDescription(context.getString(com.noam.photodream.R.string.flip_card, i + 1));
            card.setOnClickListener(v -> onTap(index));
            table.addView(card);
            cards.add(card);
            loadPhoto(card, photos.get(i % photos.size()), Math.round(Math.max(s.w, s.h)));   // fewer photos than cards: repeat
        }
    }

    private void loadPhoto(FlipCardView card, Photo photo, int longSide) {
        decoder.execute(() -> {
            Bitmap bmp = BitmapLoader.load(context, photo.uri, longSide);
            handler.post(() -> {
                if (bmp != null && running) card.setPhoto(bmp);
            });
        });
    }

    private void onTap(int index) {
        if (!running || !game.flip(index)) return;
        cards.get(index).flipTo(true);
        listener.onProgress(game.faceUpCount(), cardCount);
        if (game.isSolved()) handler.postDelayed(this::flyAway, 500);
    }

    /** Solved: the cards fly off the table, then the alarm stops. */
    private void flyAway() {
        for (FlipCardView c : cards) {
            float dx = (random.nextFloat() - 0.5f) * table.getWidth() * 2f;
            float dy = -table.getHeight() * (0.6f + random.nextFloat() * 0.6f);
            c.animate().translationX(dx).translationY(dy).rotationBy((random.nextFloat() - 0.5f) * 120f)
                    .alpha(0f).setDuration(600).start();
        }
        handler.postDelayed(() -> listener.onSolved(), 650);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            for (int i : game.tick()) cards.get(i).flipTo(false);      // too slow: it turns back
            listener.onProgress(game.faceUpCount(), cardCount);
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void start() {
        running = true;
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        for (FlipCardView c : cards) c.cancelAnimations();
        decoder.shutdownNow();
    }
}
