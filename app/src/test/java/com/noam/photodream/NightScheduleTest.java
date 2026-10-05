package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalTime;

public class NightScheduleTest {

    private static final LocalTime FROM = LocalTime.of(22, 0), TO = LocalTime.of(6, 30);   // the default

    private static boolean night(String now) { return NightSchedule.isNight(LocalTime.parse(now), FROM, TO); }

    @Test
    public void acrossMidnightRange() {
        assertTrue(night("22:00"));
        assertTrue(night("23:59"));
        assertTrue(night("00:00"));
        assertTrue(night("03:15"));
        assertTrue(night("06:29"));
        assertFalse(night("06:30"));
        assertFalse(night("12:00"));
        assertFalse(night("21:59"));
    }

    @Test
    public void rangeInsideOneDay() {
        LocalTime from = LocalTime.of(13, 0), to = LocalTime.of(15, 0);
        assertFalse(NightSchedule.isNight(LocalTime.of(12, 59), from, to));
        assertTrue(NightSchedule.isNight(LocalTime.of(13, 0), from, to));
        assertTrue(NightSchedule.isNight(LocalTime.of(14, 59), from, to));
        assertFalse(NightSchedule.isNight(LocalTime.of(15, 0), from, to));
    }

    @Test
    public void emptyRangeIsNeverNight() {
        LocalTime t = LocalTime.of(8, 0);
        assertFalse(NightSchedule.isNight(LocalTime.of(8, 0), t, t));
        assertFalse(NightSchedule.isNight(LocalTime.of(20, 0), t, t));
        assertNull(NightSchedule.untilNextSwitch(LocalTime.NOON, t, t));
    }

    @Test
    public void rangesThatTouchMidnight() {
        // evening until midnight
        LocalTime from = LocalTime.of(18, 0);
        assertTrue(NightSchedule.isNight(LocalTime.of(23, 0), from, LocalTime.MIDNIGHT));
        assertFalse(NightSchedule.isNight(LocalTime.MIDNIGHT, from, LocalTime.MIDNIGHT));
        assertFalse(NightSchedule.isNight(LocalTime.of(17, 59), from, LocalTime.MIDNIGHT));
        // midnight until early morning
        LocalTime to = LocalTime.of(6, 0);
        assertTrue(NightSchedule.isNight(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, to));
        assertTrue(NightSchedule.isNight(LocalTime.of(5, 59), LocalTime.MIDNIGHT, to));
        assertFalse(NightSchedule.isNight(LocalTime.of(6, 0), LocalTime.MIDNIGHT, to));
    }

    @Test
    public void nextSwitchWhileDayIsTheStartOfNight() {
        assertEquals(Duration.ofHours(10), NightSchedule.untilNextSwitch(LocalTime.of(12, 0), FROM, TO));
        assertEquals(Duration.ofMinutes(1), NightSchedule.untilNextSwitch(LocalTime.of(21, 59), FROM, TO));
    }

    @Test
    public void nextSwitchWhileNightIsTheEndOfNight() {
        assertEquals(Duration.ofHours(8).plusMinutes(30), NightSchedule.untilNextSwitch(LocalTime.of(22, 0), FROM, TO));
        assertEquals(Duration.ofHours(3).plusMinutes(30), NightSchedule.untilNextSwitch(LocalTime.of(3, 0), FROM, TO));
        assertEquals(Duration.ofMinutes(30), NightSchedule.untilNextSwitch(LocalTime.of(6, 0), FROM, TO));
    }

    @Test
    public void exactlyAtTheSwitchLooksToTheNextOne() {
        // at 06:30 night just ended: the next switch is 22:00 (15 h 30 min away), not "now"
        assertEquals(Duration.ofHours(15).plusMinutes(30), NightSchedule.untilNextSwitch(LocalTime.of(6, 30), FROM, TO));
    }
}
