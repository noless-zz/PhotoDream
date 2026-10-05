package com.noam.photodream.alarm;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * The time math of the alarm clock. Pure Java ({@code java.time}), no Android, so every rule
 * – repeating days, one-time alarms, the minute boundary, daylight-saving changes – is unit-tested.
 */
public final class AlarmTimes {

    /** The alarm (or snooze) that rings next, and when. */
    public static final class Next {
        public final long alarmId;
        public final long triggerMillis;
        public final boolean snooze;

        Next(long alarmId, long triggerMillis, boolean snooze) {
            this.alarmId = alarmId;
            this.triggerMillis = triggerMillis;
            this.snooze = snooze;
        }
    }

    private AlarmTimes() { }

    /**
     * The first moment <b>strictly after</b> {@code now} at which this alarm rings.
     *
     * <ul>
     *   <li>One-time alarm: today if hour:minute is still ahead, otherwise tomorrow.</li>
     *   <li>Repeating: the next selected weekday (today counts if the time is still ahead).</li>
     *   <li>Strictly after: alarm 07:00 and now 07:00:30 → the next day, never "right now".</li>
     *   <li>Daylight saving: a wall-clock time that doesn't exist (clocks jump forward) rings at the
     *       shifted time; an ambiguous one (clocks go back) rings at the first occurrence.</li>
     * </ul>
     */
    public static ZonedDateTime nextTrigger(Alarm alarm, ZonedDateTime now) {
        ZoneId zone = now.getZone();
        LocalDate today = now.toLocalDate();
        LocalTime time = LocalTime.of(alarm.hour, alarm.minute);
        for (int d = 0; d <= 8; d++) {                    // 8 days is always enough to see every weekday
            LocalDate date = today.plusDays(d);
            if (alarm.isRepeating() && !alarm.hasDay(date.getDayOfWeek())) continue;
            ZonedDateTime candidate = ZonedDateTime.of(date, time, zone);
            if (candidate.isAfter(now)) return candidate;
        }
        throw new IllegalStateException("No trigger found for alarm " + alarm.id);   // cannot happen
    }

    /**
     * Picks what the phone's alarm manager should be set to: the earliest of all enabled alarms and
     * all pending snoozes (alarm id → snooze end, epoch millis). Snoozes of deleted alarms are ignored.
     * Returns null when nothing is scheduled.
     */
    public static Next earliest(List<Alarm> alarms, Map<Long, Long> snoozes, ZonedDateTime now) {
        Next best = null;
        for (Alarm a : alarms) {
            if (!a.enabled) continue;
            long t = nextTrigger(a, now).toInstant().toEpochMilli();
            if (best == null || t < best.triggerMillis) best = new Next(a.id, t, false);
        }
        long nowMs = now.toInstant().toEpochMilli();
        for (Map.Entry<Long, Long> s : snoozes.entrySet()) {
            boolean exists = false;
            for (Alarm a : alarms) if (a.id == s.getKey()) exists = true;
            if (!exists || s.getValue() <= nowMs) continue;
            if (best == null || s.getValue() < best.triggerMillis) best = new Next(s.getKey(), s.getValue(), true);
        }
        return best;
    }
}
