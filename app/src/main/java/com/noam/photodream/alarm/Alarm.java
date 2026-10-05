package com.noam.photodream.alarm;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

/**
 * One wake-up alarm. A plain value holder with no Android imports, so the rules around it
 * (days mask, converting to/from a map for storage) are unit-tested.
 *
 * <p>The days mask has one bit per weekday, Sunday = bit 0 … Saturday = bit 6 (the Israeli week
 * shown in the UI). An empty mask means "ring once": the next time the clock shows hour:minute.
 */
public final class Alarm {

    public enum Difficulty { EASY, MEDIUM, HARD }

    /** Challenge ids are defined by the challenge registry (issue #15); "random" picks one. */
    public static final String CHALLENGE_RANDOM = "random";

    public static final int DEFAULT_SNOOZE_MINUTES = 5;
    public static final int DEFAULT_RAMP_SECONDS = 30;

    /** 0 = not saved yet; {@link AlarmStore} assigns the real id. */
    public long id;
    public int hour = 7;
    public int minute = 0;
    /** Bit 0 = Sunday … bit 6 = Saturday; 0 = one-time alarm. */
    public int days;
    public boolean enabled = true;
    public String label = "";
    public String challenge = CHALLENGE_RANDOM;
    public Difficulty difficulty = Difficulty.MEDIUM;
    /** Ringtone Uri as text; null = the phone's default alarm sound. */
    public String soundUri;
    public boolean vibrate = true;
    public int snoozeMinutes = DEFAULT_SNOOZE_MINUTES;
    public int rampSeconds = DEFAULT_RAMP_SECONDS;

    public boolean isRepeating() { return days != 0; }

    public boolean hasDay(DayOfWeek d) { return (days & bit(d)) != 0; }

    /** Sunday → bit 0, Monday → bit 1 … Saturday → bit 6. Independent of the phone's locale. */
    public static int bit(DayOfWeek d) { return 1 << (d.getValue() % 7); }

    public Alarm copy() {
        return fromMap(toMap());
    }

    // ---------------------------------------------------------------- storage helpers

    /** Flat map of strings / numbers / booleans, ready for JSON. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("hour", hour);
        m.put("minute", minute);
        m.put("days", days);
        m.put("enabled", enabled);
        m.put("label", label);
        m.put("challenge", challenge);
        m.put("difficulty", difficulty.name());
        if (soundUri != null) m.put("sound", soundUri);
        m.put("vibrate", vibrate);
        m.put("snooze", snoozeMinutes);
        m.put("ramp", rampSeconds);
        return m;
    }

    /** Reads a map back; missing or odd values fall back to the defaults, never crash. */
    public static Alarm fromMap(Map<String, ?> m) {
        Alarm a = new Alarm();
        a.id = num(m.get("id"), 0).longValue();
        a.hour = clamp(num(m.get("hour"), a.hour).intValue(), 0, 23);
        a.minute = clamp(num(m.get("minute"), a.minute).intValue(), 0, 59);
        a.days = num(m.get("days"), 0).intValue() & 0x7F;
        a.enabled = bool(m.get("enabled"), true);
        a.label = str(m.get("label"), "");
        a.challenge = str(m.get("challenge"), CHALLENGE_RANDOM);
        try {
            a.difficulty = Difficulty.valueOf(str(m.get("difficulty"), Difficulty.MEDIUM.name()));
        } catch (IllegalArgumentException e) {
            a.difficulty = Difficulty.MEDIUM;
        }
        Object sound = m.get("sound");
        a.soundUri = sound instanceof String && !((String) sound).isEmpty() ? (String) sound : null;
        a.vibrate = bool(m.get("vibrate"), true);
        a.snoozeMinutes = clamp(num(m.get("snooze"), DEFAULT_SNOOZE_MINUTES).intValue(), 1, 60);
        a.rampSeconds = clamp(num(m.get("ramp"), DEFAULT_RAMP_SECONDS).intValue(), 0, 300);
        return a;
    }

    private static Number num(Object o, Number fallback) { return o instanceof Number ? (Number) o : fallback; }
    private static boolean bool(Object o, boolean fallback) { return o instanceof Boolean ? (Boolean) o : fallback; }
    private static String str(Object o, String fallback) { return o instanceof String ? (String) o : fallback; }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
