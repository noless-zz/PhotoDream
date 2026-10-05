package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public class PhotoDatesTest {

    private static LocalDate d(String s) { return LocalDate.parse(s); }

    // ---------------------------------------------------------------- parse

    @Test
    public void parsesExifDates() {
        assertEquals(d("2023-06-14"), PhotoDates.parse("2023:06:14 10:22:33"));
    }

    @Test
    public void parsesIsoDatesFromOneDrive() {
        assertEquals(d("2023-06-14"), PhotoDates.parse("2023-06-14T10:22:33Z"));
        assertEquals(d("2019-12-31"), PhotoDates.parse("2019-12-31T23:59:59.123+02:00"));
        assertEquals(d("2021-01-02"), PhotoDates.parse("2021-01-02"));
    }

    @Test
    public void garbageAndEmptyDatesAreNull() {
        assertNull(PhotoDates.parse(null));
        assertNull(PhotoDates.parse(""));
        assertNull(PhotoDates.parse("yesterday"));
        assertNull(PhotoDates.parse("0000:00:00 00:00:00"));
        assertNull(PhotoDates.parse("2023:13:40 10:00:00"));
        assertNull(PhotoDates.parse("2023:02:30 10:00:00"));
    }

    // ---------------------------------------------------------------- on this day

    @Test
    public void withinThreeDaysInAnEarlierYear() {
        LocalDate today = d("2026-10-05");
        assertTrue(PhotoDates.isOnThisDay(d("2023-10-05"), today));
        assertTrue(PhotoDates.isOnThisDay(d("2023-10-02"), today));
        assertTrue(PhotoDates.isOnThisDay(d("2020-10-08"), today));
        assertFalse(PhotoDates.isOnThisDay(d("2023-10-01"), today));
        assertFalse(PhotoDates.isOnThisDay(d("2023-10-09"), today));
    }

    @Test
    public void thisYearOrFutureIsNeverOnThisDay() {
        LocalDate today = d("2026-10-05");
        assertFalse(PhotoDates.isOnThisDay(d("2026-10-04"), today));
        assertFalse(PhotoDates.isOnThisDay(d("2027-10-05"), today));
        assertFalse(PhotoDates.isOnThisDay(null, today));
    }

    @Test
    public void worksAcrossNewYear() {
        assertTrue(PhotoDates.isOnThisDay(d("2020-12-31"), d("2026-01-02")));
        assertTrue(PhotoDates.isOnThisDay(d("2020-01-01"), d("2026-12-30")));
        assertFalse(PhotoDates.isOnThisDay(d("2020-12-25"), d("2026-01-02")));
    }

    @Test
    public void leapDayCountsAsFebruary28InOtherYears() {
        assertTrue(PhotoDates.isOnThisDay(d("2020-02-29"), d("2026-02-28")));
        assertTrue(PhotoDates.isOnThisDay(d("2020-02-29"), d("2026-03-03")));
        assertFalse(PhotoDates.isOnThisDay(d("2020-02-29"), d("2026-03-04")));
        // and today is a leap day
        assertTrue(PhotoDates.isOnThisDay(d("2023-03-02"), d("2028-02-29")));
    }

    // ---------------------------------------------------------------- years ago

    @Test
    public void wholeYearsAgo() {
        LocalDate today = d("2026-10-05");
        assertEquals(3, PhotoDates.yearsAgo(d("2023-06-14"), today));
        assertEquals(3, PhotoDates.yearsAgo(d("2023-10-05"), today));
        assertEquals(2, PhotoDates.yearsAgo(d("2023-10-06"), today));      // not three full years yet
        assertEquals(0, PhotoDates.yearsAgo(d("2026-03-01"), today));
        assertEquals(0, PhotoDates.yearsAgo(d("2030-01-01"), today));      // future date: never negative
        assertEquals(0, PhotoDates.yearsAgo(null, today));
    }
}
