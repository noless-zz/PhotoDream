package com.noam.photodream.alarm.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Rules of "Flip them all": every card starts face down; tap to flip it up. When all are face up at
 * the same time the game is solved. On Medium/Hard a face-up card flips back after a while, so you
 * have to be quick. Pure Java with an injectable clock so the timing is unit-tested.
 */
public final class FlipGame {

    private final boolean[] faceUp;
    private final long[] flippedAt;
    private final long flipBackMs;          // 0 = never flips back (Easy)
    private final LongSupplier clock;
    private boolean solved;

    public FlipGame(int cards, long flipBackMs, LongSupplier clock) {
        this.faceUp = new boolean[cards];
        this.flippedAt = new long[cards];
        this.flipBackMs = flipBackMs;
        this.clock = clock;
    }

    public int size() { return faceUp.length; }

    public boolean isFaceUp(int i) { return faceUp[i]; }

    public boolean isSolved() { return solved; }

    public int faceUpCount() {
        int n = 0;
        for (boolean b : faceUp) if (b) n++;
        return n;
    }

    /** Tap on a card. Returns true if it turned face up (false: already up, solved, or bad index). */
    public boolean flip(int i) {
        if (solved || i < 0 || i >= faceUp.length || faceUp[i]) return false;
        faceUp[i] = true;
        flippedAt[i] = clock.getAsLong();
        if (faceUpCount() == faceUp.length) solved = true;
        return true;
    }

    /** Call regularly. Returns the cards that flipped back face down just now. */
    public List<Integer> tick() {
        List<Integer> back = new ArrayList<>();
        if (solved || flipBackMs <= 0) return back;
        long now = clock.getAsLong();
        for (int i = 0; i < faceUp.length; i++) {
            if (faceUp[i] && now - flippedAt[i] >= flipBackMs) {
                faceUp[i] = false;
                back.add(i);
            }
        }
        return back;
    }
}
