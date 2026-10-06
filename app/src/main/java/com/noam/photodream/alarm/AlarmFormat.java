package com.noam.photodream.alarm;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Text helpers for the alarm screens, split into a pure "what to say" part (tested) and the
 * Android part that picks the translated string.
 */
public final class AlarmFormat {

    private AlarmFormat() { }

    /** How the "Alarm in …" toast is worded. */
    public enum UntilKind { LESS_THAN_A_MINUTE, MINUTES, HOURS_MINUTES, DAYS_HOURS_MINUTES }

    /** The time left until an alarm, rounded down to whole minutes. */
    public static final class Until {
        public final UntilKind kind;
        public final int days, hours, minutes;

        Until(UntilKind kind, int days, int hours, int minutes) {
            this.kind = kind;
            this.days = days;
            this.hours = hours;
            this.minutes = minutes;
        }
    }

    /** "7 h 12 min" for 7:12:30 left; "1 d 2 h 0 min" for a day and two hours; "less than a minute" under 60 s. */
    public static Until until(Duration d) {
        long totalMinutes = Math.max(0, d.toMinutes());
        if (totalMinutes == 0) return new Until(UntilKind.LESS_THAN_A_MINUTE, 0, 0, 0);
        int days = (int) (totalMinutes / (24 * 60));
        int hours = (int) (totalMinutes / 60 % 24);
        int minutes = (int) (totalMinutes % 60);
        if (days > 0) return new Until(UntilKind.DAYS_HOURS_MINUTES, days, hours, minutes);
        if (hours > 0) return new Until(UntilKind.HOURS_MINUTES, 0, hours, minutes);
        return new Until(UntilKind.MINUTES, 0, 0, minutes);
    }

    /** The selected weekdays in the order the UI shows them: Sunday first … Saturday last. */
    public static List<DayOfWeek> daysSundayFirst(int mask) {
        List<DayOfWeek> out = new ArrayList<>();
        DayOfWeek[] order = {DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};
        for (DayOfWeek d : order) if ((mask & Alarm.bit(d)) != 0) out.add(d);
        return out;
    }

    /** ONCE (no days), EVERY_DAY (all seven) or CUSTOM (list the days). */
    public enum DaysKind { ONCE, EVERY_DAY, CUSTOM }

    public static DaysKind daysKind(int mask) {
        int m = mask & 0x7F;
        if (m == 0) return DaysKind.ONCE;
        if (m == 0x7F) return DaysKind.EVERY_DAY;
        return DaysKind.CUSTOM;
    }
}
