package com.noam.photodream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateCheckerTest {

    private static final long DAY = 24 * 60 * 60 * 1000L;

    @Test
    public void neverCheckedIsDue() {
        assertTrue(UpdateChecker.isDue(0, 1_000_000_000_000L));
    }

    @Test
    public void checkedAnHourAgoIsNotDue() {
        long now = 1_000_000_000_000L;
        assertFalse(UpdateChecker.isDue(now - 3_600_000L, now));
    }

    @Test
    public void checkedADayAgoIsDue() {
        long now = 1_000_000_000_000L;
        assertTrue(UpdateChecker.isDue(now - DAY, now));
        assertFalse(UpdateChecker.isDue(now - DAY + 1, now));
    }

    @Test
    public void clockSetBackwardsIsDue() {
        assertTrue(UpdateChecker.isDue(2_000_000_000_000L, 1_000_000_000_000L));
    }
}
