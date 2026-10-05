package com.noam.photodream.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class AlarmPolicyTest {

    @Test
    public void snoozeIsLimitedToThree() {
        assertTrue(AlarmPolicy.canSnooze(0));
        assertTrue(AlarmPolicy.canSnooze(2));
        assertFalse(AlarmPolicy.canSnooze(3));
        assertFalse(AlarmPolicy.canSnooze(10));
    }

    @Test
    public void fallbackAppearsAfterThreeMinutesAndAutoStopAfterFifteen() {
        assertFalse(AlarmPolicy.showFallback(179_999));
        assertTrue(AlarmPolicy.showFallback(180_000));
        assertFalse(AlarmPolicy.shouldAutoStop(14 * 60_000L));
        assertTrue(AlarmPolicy.shouldAutoStop(15 * 60_000L));
    }

    @Test
    public void rampGoesFromTenToHundredPercentInAStraightLine() {
        assertEquals(0.10f, AlarmPolicy.rampVolume(0, 30), 0.0001f);
        assertEquals(0.55f, AlarmPolicy.rampVolume(15_000, 30), 0.0001f);
        assertEquals(1.0f, AlarmPolicy.rampVolume(30_000, 30), 0.0001f);
        assertEquals(1.0f, AlarmPolicy.rampVolume(999_000, 30), 0.0001f);
    }

    @Test
    public void zeroRampMeansFullVolumeImmediately() {
        assertEquals(1.0f, AlarmPolicy.rampVolume(0, 0), 0f);
    }

    @Test
    public void rampNeverGoesBelowStartOrAboveOne() {
        assertEquals(0.10f, AlarmPolicy.rampVolume(-500, 30), 0.0001f);
        assertTrue(AlarmPolicy.rampVolume(1, 1) <= 1f);
    }

    @Test
    public void whileSolvingVolumeIsCappedAtThirtyPercent() {
        assertEquals(0.30f, AlarmPolicy.targetVolume(60_000, 30, 2_000), 0.0001f);   // full ramp, but touched 2 s ago
        assertEquals(0.10f, AlarmPolicy.targetVolume(0, 30, 2_000), 0.0001f);         // quiet already: stays quiet
    }

    @Test
    public void volumeRisesAgainAfterFifteenSecondsWithoutTouches() {
        assertEquals(0.30f, AlarmPolicy.targetVolume(60_000, 30, 14_999), 0.0001f);
        assertEquals(1.0f, AlarmPolicy.targetVolume(60_000, 30, 15_000), 0.0001f);
    }

    @Test
    public void noTouchYetMeansNotSolving() {
        assertEquals(1.0f, AlarmPolicy.targetVolume(60_000, 30, -1), 0.0001f);
    }

    @Test
    public void volumeDropsAtOnceButRisesGently() {
        assertEquals(0.3f, AlarmPolicy.stepVolume(1.0f, 0.3f, 100), 0.0001f);
        assertEquals(0.31f, AlarmPolicy.stepVolume(0.3f, 1.0f, 100), 0.0001f);       // +0.1 per second
        assertEquals(1.0f, AlarmPolicy.stepVolume(0.99f, 1.0f, 5_000), 0.0001f);     // never overshoots
    }

    @Test
    public void fallbackCodeIsFourDigitsWithLeadingZeros() {
        Random r = new Random(1);
        for (int i = 0; i < 500; i++) {
            String c = AlarmPolicy.newFallbackCode(r);
            assertEquals(4, c.length());
            assertTrue(c.matches("[0-9]{4}"));
        }
        assertEquals("0042", String.format(java.util.Locale.ROOT, "%04d", 42));
    }

    @Test
    public void codeCheckerIgnoresSurroundingSpacesButNothingElse() {
        assertTrue(AlarmPolicy.codeMatches("0420", "0420"));
        assertTrue(AlarmPolicy.codeMatches("0420", " 0420 "));
        assertFalse(AlarmPolicy.codeMatches("0420", "420"));
        assertFalse(AlarmPolicy.codeMatches("0420", ""));
        assertFalse(AlarmPolicy.codeMatches("0420", null));
        assertFalse(AlarmPolicy.codeMatches(null, "0420"));
    }
}
