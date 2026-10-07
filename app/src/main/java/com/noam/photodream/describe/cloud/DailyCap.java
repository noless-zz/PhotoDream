package com.noam.photodream.describe.cloud;

/**
 * Limits how many photos may be sent to the cloud per calendar day, across all services.
 * Pure logic: the caller stores the day and count (in Prefs) and passes today's date.
 */
public final class DailyCap {

    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 1000;

    private final String day;
    private int used;
    private final int limit;

    /** @param storedDay the day {@code storedUsed} was counted on; a different day means a fresh count */
    public DailyCap(String today, String storedDay, int storedUsed, int limit) {
        this.day = today;
        this.used = today.equals(storedDay) ? Math.max(0, storedUsed) : 0;
        this.limit = clampLimit(limit);
    }

    public static int clampLimit(int limit) { return Math.max(0, Math.min(MAX_LIMIT, limit)); }

    public boolean allows() { return used < limit; }

    public int remaining() { return Math.max(0, limit - used); }

    /** Counts one photo sent. */
    public void record() { used++; }

    public int used() { return used; }

    public String day() { return day; }
}
