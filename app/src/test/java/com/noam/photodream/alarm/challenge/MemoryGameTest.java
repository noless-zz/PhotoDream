package com.noam.photodream.alarm.challenge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class MemoryGameTest {

    /** Finds the other card with the same picture. */
    private static int partner(MemoryGame g, int card) {
        for (int i = 0; i < g.cards(); i++) if (i != card && g.pairIdAt(i) == g.pairIdAt(card)) return i;
        throw new AssertionError("no partner");
    }

    /** Finds a card with a different picture. */
    private static int stranger(MemoryGame g, int card) {
        for (int i = 0; i < g.cards(); i++) if (g.pairIdAt(i) != g.pairIdAt(card)) return i;
        throw new AssertionError("no stranger");
    }

    @Test
    public void dealHasEveryPictureExactlyTwice() {
        for (int pairs : new int[]{3, 4, 6}) {
            MemoryGame g = new MemoryGame(pairs, new Random(pairs));
            assertEquals(pairs * 2, g.cards());
            int[] count = new int[pairs];
            for (int i = 0; i < g.cards(); i++) count[g.pairIdAt(i)]++;
            for (int c : count) assertEquals(2, c);
        }
    }

    @Test
    public void sameSeedDealsTheSameWay() {
        MemoryGame a = new MemoryGame(6, new Random(42)), b = new MemoryGame(6, new Random(42));
        for (int i = 0; i < a.cards(); i++) assertEquals(a.pairIdAt(i), b.pairIdAt(i));
    }

    @Test
    public void differentSeedsShuffleDifferently() {
        MemoryGame a = new MemoryGame(6, new Random(1)), b = new MemoryGame(6, new Random(2));
        boolean differ = false;
        for (int i = 0; i < a.cards(); i++) differ |= a.pairIdAt(i) != b.pairIdAt(i);
        assertTrue(differ);
    }

    @Test
    public void firstSelectionJustFlips() {
        MemoryGame g = new MemoryGame(3, new Random(1));
        assertEquals(MemoryGame.Result.FIRST, g.select(0));
        assertFalse(g.isLocked());
    }

    @Test
    public void matchStaysAndCounts() {
        MemoryGame g = new MemoryGame(3, new Random(1));
        g.select(0);
        assertEquals(MemoryGame.Result.MATCH, g.select(partner(g, 0)));
        assertTrue(g.isMatched(0));
        assertEquals(1, g.pairsFound());
        assertFalse(g.isLocked());
    }

    @Test
    public void mismatchLocksUntilResolved() {
        MemoryGame g = new MemoryGame(3, new Random(1));
        int other = stranger(g, 0);
        g.select(0);
        assertEquals(MemoryGame.Result.MISMATCH, g.select(other));
        assertTrue(g.isLocked());
        assertArrayEquals(new int[]{0, other}, g.mismatchedCards());
        assertEquals(MemoryGame.Result.IGNORED, g.select(partner(g, 0)));   // too early
        g.resolveMismatch();
        assertFalse(g.isLocked());
        assertNull(g.mismatchedCards());
        assertEquals(MemoryGame.Result.FIRST, g.select(0));                  // can try again
        assertFalse(g.isMatched(0));
    }

    @Test
    public void tappingTheSameCardTwiceOrAMatchedCardIsIgnored() {
        MemoryGame g = new MemoryGame(3, new Random(1));
        g.select(0);
        assertEquals(MemoryGame.Result.IGNORED, g.select(0));
        g.select(partner(g, 0));
        assertEquals(MemoryGame.Result.IGNORED, g.select(0));
        assertEquals(MemoryGame.Result.IGNORED, g.select(-1));
        assertEquals(MemoryGame.Result.IGNORED, g.select(99));
    }

    @Test
    public void findingAllPairsSolvesIt() {
        MemoryGame g = new MemoryGame(4, new Random(7));
        for (int i = 0; i < g.cards(); i++) {
            if (g.isMatched(i)) continue;
            g.select(i);
            g.select(partner(g, i));
        }
        assertTrue(g.isSolved());
        assertEquals(4, g.pairsFound());
        assertEquals(MemoryGame.Result.IGNORED, g.select(0));
    }

    @Test
    public void aStrugglingPlayerStillFinishes() {
        MemoryGame g = new MemoryGame(3, new Random(5));
        for (int round = 0; round < 3; round++) {          // three wrong guesses first
            g.select(0);
            g.select(stranger(g, 0));
            g.resolveMismatch();
        }
        for (int i = 0; i < g.cards(); i++) {
            if (g.isMatched(i)) continue;
            g.select(i);
            g.select(partner(g, i));
        }
        assertTrue(g.isSolved());
    }
}
