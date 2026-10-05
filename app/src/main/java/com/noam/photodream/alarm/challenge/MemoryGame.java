package com.noam.photodream.alarm.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Rules of "Memory pairs": 2×N cards, every picture twice. Flip two; a match stays face up, a
 * mismatch is shown and then turned back by the view after about a second.
 * Pure Java (seeded Random for the deal), unit-tested.
 */
public final class MemoryGame {

    public enum Result { IGNORED, FIRST, MATCH, MISMATCH }

    private final int pairs;
    private final int[] pairIdAt;
    private final boolean[] matched;
    private int first = -1;                 // first card of the pair being tried
    private int[] mismatch;                 // the two cards of a mismatch waiting to be turned back
    private int pairsFound;

    /** Deals {@code pairs} pairs: {@code pairIdAt(i)} says which picture card i shows (0..pairs-1). */
    public MemoryGame(int pairs, Random random) {
        this.pairs = pairs;
        List<Integer> ids = new ArrayList<>();
        for (int p = 0; p < pairs; p++) {
            ids.add(p);
            ids.add(p);
        }
        Collections.shuffle(ids, random);
        pairIdAt = new int[ids.size()];
        for (int i = 0; i < pairIdAt.length; i++) pairIdAt[i] = ids.get(i);
        matched = new boolean[pairIdAt.length];
    }

    public int pairs() { return pairs; }

    public int cards() { return pairIdAt.length; }

    public int pairIdAt(int card) { return pairIdAt[card]; }

    public int pairsFound() { return pairsFound; }

    public boolean isSolved() { return pairsFound == pairs; }

    public boolean isMatched(int card) { return matched[card]; }

    /** True while a mismatch is on the table: further taps are ignored until {@link #resolveMismatch()}. */
    public boolean isLocked() { return mismatch != null; }

    /** The two cards of the current mismatch (to flip back), or null. */
    public int[] mismatchedCards() { return mismatch == null ? null : mismatch.clone(); }

    /** The view calls this after the mismatch has been shown (about 1 s). */
    public void resolveMismatch() { mismatch = null; }

    public Result select(int card) {
        if (isSolved() || isLocked() || card < 0 || card >= pairIdAt.length || matched[card] || card == first) {
            return Result.IGNORED;
        }
        if (first < 0) {
            first = card;
            return Result.FIRST;
        }
        int other = first;
        first = -1;
        if (pairIdAt[other] == pairIdAt[card]) {
            matched[other] = true;
            matched[card] = true;
            pairsFound++;
            return Result.MATCH;
        }
        mismatch = new int[]{other, card};
        return Result.MISMATCH;
    }
}
