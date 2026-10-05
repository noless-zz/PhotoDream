package com.noam.photodream;

import java.time.Duration;
import java.time.LocalTime;

/**
 * "Is it night?" for the night-mode schedule. Pure Java ({@code java.time}), unit-tested, including
 * ranges that cross midnight (22:00 → 06:30). A range whose start equals its end is empty (never night).
 */
public final class NightSchedule {

    private NightSchedule() { }

    /** Night is from {@code from} (inclusive) up to {@code to} (exclusive). */
    public static boolean isNight(LocalTime now, LocalTime from, LocalTime to) {
        if (from.equals(to)) return false;
        if (from.isBefore(to)) return !now.isBefore(from) && now.isBefore(to);
        return !now.isBefore(from) || now.isBefore(to);              // crosses midnight
    }

    /**
     * How long until night starts or ends – whichever is next – so the app can sleep until then
     * instead of checking every second. Null for an empty range (nothing to switch).
     */
    public static Duration untilNextSwitch(LocalTime now, LocalTime from, LocalTime to) {
        if (from.equals(to)) return null;
        LocalTime target = isNight(now, from, to) ? to : from;
        long seconds = Duration.between(now, target).getSeconds();
        if (seconds <= 0) seconds += 24 * 60 * 60;                    // the target is "tomorrow"
        return Duration.ofSeconds(seconds);
    }
}
