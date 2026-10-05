package com.noam.photodream.alarm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Arrays;

public class AlarmFormatTest {

    private static AlarmFormat.Until u(Duration d) { return AlarmFormat.until(d); }

    @Test
    public void hoursAndMinutes() {
        AlarmFormat.Until x = u(Duration.ofHours(7).plusMinutes(12).plusSeconds(30));
        assertEquals(AlarmFormat.UntilKind.HOURS_MINUTES, x.kind);
        assertEquals(7, x.hours);
        assertEquals(12, x.minutes);
    }

    @Test
    public void onlyMinutes() {
        AlarmFormat.Until x = u(Duration.ofMinutes(59).plusSeconds(59));
        assertEquals(AlarmFormat.UntilKind.MINUTES, x.kind);
        assertEquals(59, x.minutes);
    }

    @Test
    public void lessThanAMinute() {
        assertEquals(AlarmFormat.UntilKind.LESS_THAN_A_MINUTE, u(Duration.ofSeconds(59)).kind);
        assertEquals(AlarmFormat.UntilKind.LESS_THAN_A_MINUTE, u(Duration.ZERO).kind);
        assertEquals(AlarmFormat.UntilKind.LESS_THAN_A_MINUTE, u(Duration.ofSeconds(-5)).kind);
    }

    @Test
    public void exactHourHasZeroMinutes() {
        AlarmFormat.Until x = u(Duration.ofHours(3));
        assertEquals(AlarmFormat.UntilKind.HOURS_MINUTES, x.kind);
        assertEquals(3, x.hours);
        assertEquals(0, x.minutes);
    }

    @Test
    public void daysHoursMinutes() {
        AlarmFormat.Until x = u(Duration.ofDays(2).plusHours(5).plusMinutes(1));
        assertEquals(AlarmFormat.UntilKind.DAYS_HOURS_MINUTES, x.kind);
        assertEquals(2, x.days);
        assertEquals(5, x.hours);
        assertEquals(1, x.minutes);
        assertEquals(1, u(Duration.ofHours(24)).days);
        assertEquals(0, u(Duration.ofHours(24)).hours);
    }

    @Test
    public void daysComeOutSundayFirst() {
        int mask = Alarm.bit(DayOfWeek.SATURDAY) | Alarm.bit(DayOfWeek.MONDAY) | Alarm.bit(DayOfWeek.SUNDAY);
        assertEquals(Arrays.asList(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY), AlarmFormat.daysSundayFirst(mask));
    }

    @Test
    public void daysKinds() {
        assertEquals(AlarmFormat.DaysKind.ONCE, AlarmFormat.daysKind(0));
        assertEquals(AlarmFormat.DaysKind.EVERY_DAY, AlarmFormat.daysKind(0x7F));
        assertEquals(AlarmFormat.DaysKind.CUSTOM, AlarmFormat.daysKind(0b0000110));
    }
}
