package com.noam.photodream.alarm.challenge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class FlipGameTest {

    private long now;

    private FlipGame game(int cards, long flipBackMs) {
        now = 0;
        return new FlipGame(cards, flipBackMs, () -> now);
    }

    @Test
    public void startsFaceDownAndUnsolved() {
        FlipGame g = game(6, 0);
        assertEquals(6, g.size());
        assertEquals(0, g.faceUpCount());
        assertFalse(g.isSolved());
    }

    @Test
    public void flippingTurnsACardOnceOnly() {
        FlipGame g = game(3, 0);
        assertTrue(g.flip(1));
        assertFalse(g.flip(1));
        assertTrue(g.isFaceUp(1));
        assertEquals(1, g.faceUpCount());
    }

    @Test
    public void badIndexIsIgnored() {
        FlipGame g = game(3, 0);
        assertFalse(g.flip(-1));
        assertFalse(g.flip(3));
    }

    @Test
    public void allFaceUpSolvesIt() {
        FlipGame g = game(3, 0);
        g.flip(0);
        g.flip(1);
        assertFalse(g.isSolved());
        g.flip(2);
        assertTrue(g.isSolved());
    }

    @Test
    public void easyNeverFlipsBack() {
        FlipGame g = game(4, 0);
        g.flip(0);
        now = 10 * 60_000;
        assertEquals(Collections.emptyList(), g.tick());
        assertTrue(g.isFaceUp(0));
    }

    @Test
    public void mediumFlipsBackAfterEightSeconds() {
        FlipGame g = game(4, 8000);
        g.flip(0);
        now = 7_999;
        assertEquals(Collections.emptyList(), g.tick());
        now = 8_000;
        assertEquals(Arrays.asList(0), g.tick());
        assertFalse(g.isFaceUp(0));
        assertEquals(0, g.faceUpCount());
    }

    @Test
    public void eachCardHasItsOwnTimer() {
        FlipGame g = game(4, 4000);
        g.flip(0);
        now = 3_000;
        g.flip(1);
        now = 4_000;
        assertEquals(Arrays.asList(0), g.tick());      // card 1 has only been up for 1 s
        assertTrue(g.isFaceUp(1));
        now = 7_000;
        assertEquals(Arrays.asList(1), g.tick());
    }

    @Test
    public void aCardCanBeFlippedAgainAfterItFlippedBack() {
        FlipGame g = game(3, 4000);
        g.flip(0);
        now = 4_000;
        g.tick();
        assertTrue(g.flip(0));
        assertTrue(g.isFaceUp(0));
    }

    @Test
    public void solvedGameFreezes() {
        FlipGame g = game(2, 4000);
        g.flip(0);
        g.flip(1);
        assertTrue(g.isSolved());
        now = 60_000;
        assertEquals(Collections.emptyList(), g.tick());
        assertEquals(2, g.faceUpCount());
        assertFalse(g.flip(0));
    }

    @Test
    public void youMustBeQuickOnHard() {
        // the last card is flipped only after the first one already turned back: not solved
        FlipGame g = game(3, 4000);
        g.flip(0);
        now = 2_000;
        g.flip(1);
        now = 4_500;
        g.tick();                                      // card 0 is back down
        g.flip(2);
        assertFalse(g.isSolved());
        assertEquals(2, g.faceUpCount());
    }
}
