package com.noam.photodream;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date helpers for "Show photo date" and "On this day". Pure Java, unit-tested.
 * Only the calendar day matters, so everything is a {@link LocalDate}.
 */
public final class PhotoDates {

    /** Photos taken within this many days of today's date (in an earlier year) count as "on this day". */
    public static final int ON_THIS_DAY_DAYS = 3;
    /** How much more often those photos come up. */
    public static final double ON_THIS_DAY_WEIGHT = 4.0;

    private static final Pattern DATE = Pattern.compile("^\\s*(\\d{4})[:\\-](\\d{2})[:\\-](\\d{2})");

    private PhotoDates() { }

    /**
     * Reads the day from the texts our sources give us: EXIF "2023:06:14 10:22:33", ISO
     * "2023-06-14T10:22:33Z" (OneDrive) or just "2023-06-14". Null for anything else.
     */
    public static LocalDate parse(String text) {
        if (text == null) return null;
        Matcher m = DATE.matcher(text);
        if (!m.find()) return null;
        try {
            int year = Integer.parseInt(m.group(1));
            if (year < 1900) return null;                  // "0000:00:00 00:00:00" and friends: camera had no clock
            return LocalDate.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * True if the photo was taken in an earlier year within ±3 days of today's month and day.
     * Works across New Year (Dec 31 vs Jan 2) and for Feb 29 (counts as Feb 28 in other years).
     */
    public static boolean isOnThisDay(LocalDate photo, LocalDate today) {
        if (photo == null || photo.getYear() >= today.getYear()) return false;
        MonthDay md = MonthDay.from(photo);
        for (int y = today.getYear() - 1; y <= today.getYear() + 1; y++) {
            long distance = Math.abs(ChronoUnit.DAYS.between(md.atYear(y), today));
            if (distance <= ON_THIS_DAY_DAYS) return true;
        }
        return false;
    }

    /** Whole years between the photo and today (0 for this year or a date in the future). */
    public static int yearsAgo(LocalDate photo, LocalDate today) {
        if (photo == null || photo.isAfter(today)) return 0;
        return Period.between(photo, today).getYears();
    }
}
