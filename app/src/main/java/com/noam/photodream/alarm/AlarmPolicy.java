package com.noam.photodream.alarm;

import java.util.Random;

/**
 * The safety rules and the sound curve of a ringing alarm, in one pure class (no Android),
 * so they are unit-tested. The idea: the alarm must really wake you up, but it must never trap you.
 */
public final class AlarmPolicy {

    public static final int MAX_SNOOZES = 3;
    /** After this long ringing, "Can't solve it?" appears. */
    public static final long FALLBACK_AFTER_MS = 3 * 60_000L;
    /** After this long the alarm stops by itself and a "Missed alarm" notification stays. */
    public static final long AUTO_STOP_MS = 15 * 60_000L;
    /** While the user touches the screen the volume drops; this long without touches it rises again. */
    public static final long QUIET_AFTER_TOUCH_MS = 15_000L;

    public static final float START_VOLUME = 0.10f;
    public static final float SOLVING_VOLUME = 0.30f;
    /** How fast the volume may rise after a quiet spell, per second (a jump would startle you). */
    private static final float RISE_PER_SECOND = 0.10f;

    private AlarmPolicy() { }

    public static boolean canSnooze(int snoozesUsed) { return snoozesUsed < MAX_SNOOZES; }

    public static boolean showFallback(long ringingMs) { return ringingMs >= FALLBACK_AFTER_MS; }

    public static boolean shouldAutoStop(long ringingMs) { return ringingMs >= AUTO_STOP_MS; }

    /** Volume 10% → 100%, rising in a straight line over {@code rampSeconds}. 0 seconds = full volume at once. */
    public static float rampVolume(long ringingMs, int rampSeconds) {
        if (rampSeconds <= 0) return 1f;
        float progress = Math.min(1f, Math.max(0f, ringingMs / (rampSeconds * 1000f)));
        return START_VOLUME + (1f - START_VOLUME) * progress;
    }

    /**
     * The volume we <i>want</i> now: the ramp, but at most 30% while the user is actively solving.
     * {@code msSinceTouch} is negative when there was no touch yet.
     */
    public static float targetVolume(long ringingMs, int rampSeconds, long msSinceTouch) {
        float ramp = rampVolume(ringingMs, rampSeconds);
        boolean solving = msSinceTouch >= 0 && msSinceTouch < QUIET_AFTER_TOUCH_MS;
        return solving ? Math.min(ramp, SOLVING_VOLUME) : ramp;
    }

    /** Moves the real volume toward the target: down at once, up gently. */
    public static float stepVolume(float current, float target, long dtMs) {
        if (target <= current) return target;
        return Math.min(target, current + RISE_PER_SECOND * dtMs / 1000f);
    }

    /** A random 4-digit code (leading zeros allowed) shown when the user can't solve the challenge. */
    public static String newFallbackCode(Random random) {
        return String.format(java.util.Locale.ROOT, "%04d", random.nextInt(10_000));
    }

    public static boolean codeMatches(String code, String typed) {
        return code != null && typed != null && code.equals(typed.trim());
    }
}
