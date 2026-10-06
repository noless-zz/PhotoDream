package com.noam.photodream.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlarmTimesTest {

    private static final ZoneId JLM = ZoneId.of("Asia/Jerusalem");

    private static ZonedDateTime at(String iso) { return ZonedDateTime.parse(iso + "[Asia/Jerusalem]"); }

    private static Alarm alarm(int h, int m, DayOfWeek... days) {
        Alarm a = new Alarm();
        a.id = 1;
        a.hour = h;
        a.minute = m;
        for (DayOfWeek d : days) a.days |= Alarm.bit(d);
        return a;
    }

    // 2026-10-04 is a Sunday

    @Test
    public void oneTimeTodayWhenStillAhead() {
        assertEquals(at("2026-10-04T07:00:00+03:00"), AlarmTimes.nextTrigger(alarm(7, 0), at("2026-10-04T06:00:00+03:00")));
    }

    @Test
    public void oneTimeTomorrowWhenAlreadyPassed() {
        assertEquals(at("2026-10-05T07:00:00+03:00"), AlarmTimes.nextTrigger(alarm(7, 0), at("2026-10-04T08:00:00+03:00")));
    }

    @Test
    public void minuteBoundaryIsStrict() {
        Alarm a = alarm(7, 0);
        assertEquals(at("2026-10-05T07:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-04T07:00:30+03:00")));
        assertEquals(at("2026-10-05T07:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-04T07:00:00+03:00")));
        assertEquals(at("2026-10-04T07:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-04T06:59:59+03:00")));
    }

    @Test
    public void repeatingPicksNextSelectedDay() {
        Alarm a = alarm(7, 0, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        // Sunday evening → Monday
        assertEquals(at("2026-10-05T07:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-04T20:00:00+03:00")));
        // Monday after 07:00 → Wednesday
        assertEquals(at("2026-10-07T07:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-05T07:01:00+03:00")));
    }

    @Test
    public void repeatingTodayCountsWhenStillAhead() {
        Alarm a = alarm(22, 30, DayOfWeek.SUNDAY);
        assertEquals(at("2026-10-04T22:30:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-04T12:00:00+03:00")));
    }

    @Test
    public void weekWrapsFromSaturdayToSunday() {
        Alarm a = alarm(6, 45, DayOfWeek.SUNDAY);
        // Saturday 2026-10-10 evening → Sunday 2026-10-11
        assertEquals(at("2026-10-11T06:45:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-10T21:00:00+03:00")));
    }

    @Test
    public void sameWeekdayRingsNextWeekWhenTodaysTimeHasPassed() {
        Alarm a = alarm(7, 0, DayOfWeek.MONDAY);
        assertEquals(at("2026-10-12T07:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-05T07:00:01+03:00")));
    }

    @Test
    public void everyDayMaskRingsTomorrow() {
        Alarm a = alarm(7, 0);
        a.days = 0x7F;
        assertEquals(at("2026-10-05T07:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-04T07:00:00+03:00")));
    }

    @Test
    public void dayBitsAreSundayFirstAndLocaleIndependent() {
        assertEquals(1, Alarm.bit(DayOfWeek.SUNDAY));
        assertEquals(2, Alarm.bit(DayOfWeek.MONDAY));
        assertEquals(64, Alarm.bit(DayOfWeek.SATURDAY));
    }

    @Test
    public void springForwardKeepsWallClockTime() {
        // Israel: clocks jump 02:00 → 03:00 on Friday 2026-03-27. A 07:00 alarm still rings at 07:00 local,
        // which is only 23 real hours after the previous day's 07:00.
        Alarm a = alarm(7, 0);
        ZonedDateTime day1 = AlarmTimes.nextTrigger(a, at("2026-03-26T08:00:00+02:00"));
        assertEquals(at("2026-03-27T07:00:00+03:00"), day1);
        assertEquals(Duration.ofHours(23), Duration.between(at("2026-03-26T07:00:00+02:00"), day1));
    }

    @Test
    public void alarmInsideTheSkippedHourRingsAtTheShiftedTime() {
        // 02:30 doesn't exist on 2026-03-27 in Israel; Java resolves it to 03:30
        Alarm a = alarm(2, 30);
        assertEquals(at("2026-03-27T03:30:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-03-26T23:00:00+02:00")));
    }

    @Test
    public void fallBackKeepsWallClockTime() {
        // clocks go back 02:00 → 01:00 on Sunday 2026-10-25: a 07:00 alarm is 25 real hours after the day before
        Alarm a = alarm(7, 0);
        ZonedDateTime t = AlarmTimes.nextTrigger(a, at("2026-10-24T08:00:00+03:00"));
        assertEquals(at("2026-10-25T07:00:00+02:00"), t);
        assertEquals(Duration.ofHours(25), Duration.between(at("2026-10-24T07:00:00+03:00"), t));
    }

    @Test
    public void ambiguousHourRingsOnlyOnce() {
        // 01:30 happens twice on 2026-10-25; the first one rings, afterwards the next ring is a day later
        Alarm a = alarm(1, 30);
        ZonedDateTime first = AlarmTimes.nextTrigger(a, at("2026-10-24T20:00:00+03:00"));
        assertEquals(at("2026-10-25T01:30:00+03:00"), first);
        ZonedDateTime after = AlarmTimes.nextTrigger(a, first);
        assertEquals(at("2026-10-26T01:30:00+02:00"), after);
    }

    @Test
    public void midnightAlarm() {
        Alarm a = alarm(0, 0, DayOfWeek.TUESDAY);
        assertEquals(at("2026-10-06T00:00:00+03:00"), AlarmTimes.nextTrigger(a, at("2026-10-05T23:59:00+03:00")));
    }

    // ---------------------------------------------------------------- earliest()

    @Test
    public void earliestPicksTheSoonestEnabledAlarm() {
        Alarm early = alarm(6, 0); early.id = 1;
        Alarm late = alarm(8, 0); late.id = 2;
        Alarm off = alarm(5, 0); off.id = 3; off.enabled = false;
        AlarmTimes.Next n = AlarmTimes.earliest(List.of(late, off, early), Collections.emptyMap(), at("2026-10-04T05:30:00+03:00"));
        assertNotNull(n);
        assertEquals(1, n.alarmId);
        assertFalse(n.snooze);
        assertEquals(at("2026-10-04T06:00:00+03:00").toInstant().toEpochMilli(), n.triggerMillis);
    }

    @Test
    public void earliestIsNullWhenNothingIsEnabled() {
        Alarm off = alarm(5, 0); off.enabled = false;
        assertNull(AlarmTimes.earliest(List.of(off), Collections.emptyMap(), at("2026-10-04T05:30:00+03:00")));
        assertNull(AlarmTimes.earliest(new ArrayList<>(), Collections.emptyMap(), at("2026-10-04T05:30:00+03:00")));
    }

    @Test
    public void snoozeCanBeTheEarliestAndIgnoresDeletedAlarms() {
        Alarm a = alarm(9, 0); a.id = 1;
        ZonedDateTime now = at("2026-10-04T07:00:00+03:00");
        Map<Long, Long> snoozes = new HashMap<>();
        snoozes.put(1L, now.plusMinutes(5).toInstant().toEpochMilli());
        snoozes.put(99L, now.plusMinutes(1).toInstant().toEpochMilli());   // alarm 99 no longer exists
        AlarmTimes.Next n = AlarmTimes.earliest(List.of(a), snoozes, now);
        assertTrue(n.snooze);
        assertEquals(1, n.alarmId);
        assertEquals(now.plusMinutes(5).toInstant().toEpochMilli(), n.triggerMillis);
    }

    @Test
    public void snoozeThatAlreadyEndedIsIgnored() {
        Alarm a = alarm(9, 0); a.id = 1;
        ZonedDateTime now = at("2026-10-04T07:00:00+03:00");
        Map<Long, Long> snoozes = new HashMap<>();
        snoozes.put(1L, now.minusMinutes(1).toInstant().toEpochMilli());
        assertFalse(AlarmTimes.earliest(List.of(a), snoozes, now).snooze);
    }
}
