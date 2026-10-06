package com.noam.photodream.alarm;

import android.content.Context;
import android.text.format.DateFormat;

import com.noam.photodream.R;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Turns alarm data into the text the user reads (translated, follows the phone's 12/24-hour setting). */
final class AlarmText {

    private AlarmText() { }

    /** "07:05" or "7:05 AM", whichever the phone is set to. */
    static String time(Context c, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        return DateFormat.getTimeFormat(c).format(cal.getTime());
    }

    /** "Once", "Every day" or "Sun, Tue, Thu" in the user's language. */
    static String days(Context c, int mask) {
        switch (AlarmFormat.daysKind(mask)) {
            case ONCE: return c.getString(R.string.alarm_once);
            case EVERY_DAY: return c.getString(R.string.alarm_every_day);
            default:
                List<String> names = new ArrayList<>();
                for (DayOfWeek d : AlarmFormat.daysSundayFirst(mask)) {
                    names.add(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
                }
                return String.join(", ", names);
        }
    }

    /** "Alarm in 7 h 12 min" for the toast after saving. */
    static String until(Context c, Alarm alarm) {
        ZonedDateTime now = ZonedDateTime.now();
        Duration left = Duration.between(now, AlarmTimes.nextTrigger(alarm, now));
        AlarmFormat.Until u = AlarmFormat.until(left);
        switch (u.kind) {
            case LESS_THAN_A_MINUTE: return c.getString(R.string.alarm_in_less_than_minute);
            case MINUTES: return c.getString(R.string.alarm_in_minutes, u.minutes);
            case HOURS_MINUTES: return c.getString(R.string.alarm_in_hours_minutes, u.hours, u.minutes);
            default: return c.getString(R.string.alarm_in_days_hours_minutes, u.days, u.hours, u.minutes);
        }
    }

    /** Name of the challenge chosen for an alarm ("Random" if it is not in the registry). */
    static String challenge(Context c, String id) {
        for (Challenges.Info i : Challenges.all()) if (i.id.equals(id)) return c.getString(i.nameRes);
        return c.getString(R.string.challenge_random);
    }
}
