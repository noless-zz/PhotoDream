package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class BurnInWalkTest {

    @Test
    public void startsAtHome() {
        BurnInWalk w = new BurnInWalk(24, 6, new Random(1));
        assertEquals(0f, w.x(), 0f);
        assertEquals(0f, w.y(), 0f);
    }

    @Test
    public void neverLeavesTheAllowedArea() {
        BurnInWalk w = new BurnInWalk(24, 6, new Random(2));
        for (int i = 0; i < 100_000; i++) {
            w.step();
            assertTrue(Math.abs(w.x()) <= 24f && Math.abs(w.y()) <= 24f);
        }
    }

    @Test
    public void eachStepIsSmall() {
        BurnInWalk w = new BurnInWalk(24, 6, new Random(3));
        float px = 0, py = 0;
        for (int i = 0; i < 10_000; i++) {
            w.step();
            assertTrue(Math.abs(w.x() - px) <= 6f + 0.0001f);
            assertTrue(Math.abs(w.y() - py) <= 6f + 0.0001f);
            px = w.x();
            py = w.y();
        }
    }

    @Test
    public void theClockActuallyMovesAndWandersAround() {
        BurnInWalk w = new BurnInWalk(24, 6, new Random(4));
        float minX = 0, maxX = 0;
        for (int i = 0; i < 2000; i++) {
            float before = w.x() * 1000 + w.y();
            w.step();
            assertNotEquals(before, w.x() * 1000 + w.y(), 0f);
            minX = Math.min(minX, w.x());
            maxX = Math.max(maxX, w.x());
        }
        assertTrue("explores the area", maxX - minX > 20f);
    }

    @Test
    public void sameSeedSamePath() {
        BurnInWalk a = new BurnInWalk(24, 6, new Random(9)), b = new BurnInWalk(24, 6, new Random(9));
        for (int i = 0; i < 50; i++) {
            a.step();
            b.step();
            assertEquals(a.x(), b.x(), 0f);
            assertEquals(a.y(), b.y(), 0f);
        }
    }

    @Test
    public void zeroAreaStaysPut() {
        BurnInWalk w = new BurnInWalk(0, 6, new Random(1));
        w.step();
        assertEquals(0f, w.x(), 0f);
        assertEquals(0f, w.y(), 0f);
    }
}
