package com.noam.photodream.alarm.challenge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.noam.photodream.alarm.Alarm;

import org.junit.Test;

import java.util.Random;

public class RunawayRulesTest {

    private static RunawayRules rules(Alarm.Difficulty d, long seed) {
        return new RunawayRules(d, new Random(seed));
    }

    @Test
    public void catchesNeededPerDifficulty() {
        assertEquals(3, rules(Alarm.Difficulty.EASY, 1).catchesNeeded());
        assertEquals(5, rules(Alarm.Difficulty.MEDIUM, 1).catchesNeeded());
        assertEquals(7, rules(Alarm.Difficulty.HARD, 1).catchesNeeded());
        assertEquals(0, RunawayRules.decoys(Alarm.Difficulty.MEDIUM));
        assertEquals(2, RunawayRules.decoys(Alarm.Difficulty.HARD));
    }

    @Test
    public void touchesFarFromTheCardNeverTriggerADodge() {
        RunawayRules r = rules(Alarm.Difficulty.HARD, 1);
        for (int i = 0; i < 1000; i++) assertFalse(r.onTouchDown(false));
    }

    @Test
    public void neverMoreThanFourDodgesInARow() {
        for (Alarm.Difficulty d : Alarm.Difficulty.values()) {
            RunawayRules r = rules(d, 7);
            int streak = 0, longest = 0;
            for (int i = 0; i < 20_000; i++) {
                if (r.onTouchDown(true)) {
                    streak++;
                    longest = Math.max(longest, streak);
                } else {
                    streak = 0;
                }
            }
            assertTrue(d + " longest streak " + longest, longest <= RunawayRules.MAX_DODGES_IN_A_ROW);
        }
    }

    @Test
    public void fifthTouchAlwaysLandsEvenWhenTheDiceSayDodge() {
        // a Random that always rolls "dodge" (0.0 < any probability)
        RunawayRules r = new RunawayRules(Alarm.Difficulty.HARD, new Random() {
            @Override public double nextDouble() { return 0.0; }
        });
        for (int i = 0; i < 4; i++) assertTrue("dodge " + i, r.onTouchDown(true));
        assertFalse("5th touch is fair", r.onTouchDown(true));
        assertTrue("then it starts dodging again", r.onTouchDown(true));
    }

    @Test
    public void dodgeRateIsRoughlyTheConfiguredProbability() {
        RunawayRules r = rules(Alarm.Difficulty.EASY, 3);     // 50%, fairness cap lowers it only a little
        int dodges = 0;
        for (int i = 0; i < 20_000; i++) if (r.onTouchDown(true)) dodges++;
        double rate = dodges / 20_000.0;
        assertTrue("rate " + rate, rate > 0.42 && rate < 0.52);
    }

    @Test
    public void hardDodgesMoreThanEasy() {
        int easy = 0, hard = 0;
        RunawayRules e = rules(Alarm.Difficulty.EASY, 5), h = rules(Alarm.Difficulty.HARD, 5);
        for (int i = 0; i < 10_000; i++) {
            if (e.onTouchDown(true)) easy++;
            if (h.onTouchDown(true)) hard++;
        }
        assertTrue(hard > easy);
    }

    @Test
    public void catchCountsOnlyInsideTheCurrentBounds() {
        RunawayRules r = rules(Alarm.Difficulty.EASY, 1);
        assertFalse(r.onTouchUp(false));
        assertEquals(0, r.catches());
        assertTrue(r.onTouchUp(true));
        assertEquals(1, r.catches());
    }

    @Test
    public void solvedAfterTheNeededCatches() {
        RunawayRules r = rules(Alarm.Difficulty.EASY, 1);
        r.onTouchUp(true);
        r.onTouchUp(true);
        assertFalse(r.isSolved());
        r.onTouchUp(true);
        assertTrue(r.isSolved());
        assertFalse(r.onTouchUp(true));       // no more catches after solving
        assertEquals(3, r.catches());
    }

    @Test
    public void cardShrinksTenPercentPerCatchAndGetsFaster() {
        RunawayRules r = rules(Alarm.Difficulty.HARD, 1);
        assertEquals(1.0f, r.scale(), 0.0001f);
        assertEquals(200, r.dodgeMillis());
        r.onTouchUp(true);
        assertEquals(0.9f, r.scale(), 0.0001f);
        assertEquals(180, r.dodgeMillis());
        for (int i = 0; i < 6; i++) r.onTouchUp(true);
        assertTrue(r.scale() >= 0.45f);
        assertTrue(r.dodgeMillis() >= 90);
    }

    @Test
    public void nextCenterIsAlwaysFullyOnScreen() {
        RunawayRules r = rules(Alarm.Difficulty.MEDIUM, 11);
        for (int i = 0; i < 2000; i++) {
            float[] c = r.nextCenter(200, 260, 1000, 1400, 500, 700);
            assertTrue(c[0] >= 100 && c[0] <= 900);
            assertTrue(c[1] >= 130 && c[1] <= 1270);
        }
    }

    @Test
    public void nextCenterUsuallyMovesAwayFromTheCurrentSpot() {
        RunawayRules r = rules(Alarm.Difficulty.MEDIUM, 13);
        int far = 0;
        for (int i = 0; i < 500; i++) {
            float[] c = r.nextCenter(200, 260, 1000, 1400, 500, 700);
            if (Math.hypot(c[0] - 500, c[1] - 700) >= 350) far++;
        }
        assertTrue("far " + far, far > 450);
    }

    @Test
    public void cardBiggerThanTheAreaStillGetsAValidCentre() {
        RunawayRules r = rules(Alarm.Difficulty.EASY, 1);
        float[] c = r.nextCenter(2000, 2000, 1000, 1000, 500, 500);
        assertEquals(1000f, c[0], 0.01f);      // pinned to the middle, no crash
    }
}
