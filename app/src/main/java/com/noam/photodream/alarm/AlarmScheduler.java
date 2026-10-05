package com.noam.photodream.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.noam.photodream.SettingsActivity;

import java.time.ZonedDateTime;

/**
 * Tells Android when the next alarm rings. Only the <b>earliest</b> enabled alarm (or snooze) is
 * registered; after it rings, {@link #rescheduleAll} picks the next one.
 *
 * Uses {@code setAlarmClock}: exact even in Doze, and Android shows the alarm icon in the status bar.
 */
public final class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";
    static final String ACTION_RING = "com.noam.photodream.alarm.RING";
    static final String EXTRA_ALARM_ID = "alarm_id";
    static final String EXTRA_SNOOZE = "snooze";
    private static final int REQUEST_RING = 1;
    private static final int REQUEST_SHOW = 2;

    private AlarmScheduler() { }

    /** False on Android 12+ when the user has switched "Alarms & reminders" off for the app. */
    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    /** False on Android 14+ when "full-screen notifications" are off: the alarm then only shows a heads-up. */
    public static boolean canUseFullScreenIntent(Context context) {
        if (Build.VERSION.SDK_INT < 34) return true;
        return context.getSystemService(android.app.NotificationManager.class).canUseFullScreenIntent();
    }

    /** Re-arm (or cancel) the single system alarm from what is in the store. Safe to call any time. */
    public static void rescheduleAll(Context context) {
        Context app = context.getApplicationContext();
        AlarmStore store = new AlarmStore(app);
        AlarmManager am = app.getSystemService(AlarmManager.class);
        AlarmTimes.Next next = AlarmTimes.earliest(store.list(), store.snoozes(), ZonedDateTime.now());

        PendingIntent ring = ringIntent(app, next);
        if (next == null) {
            am.cancel(ringIntent(app, null));
            return;
        }
        PendingIntent show = PendingIntent.getActivity(app, REQUEST_SHOW,
                new Intent(app, SettingsActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(next.triggerMillis, show), ring);
        } catch (SecurityException e) {
            // exact alarms switched off: the UI shows a banner (see canScheduleExactAlarms)
            Log.w(TAG, "Not allowed to set exact alarms", e);
        }
    }

    /** Called by the receiver when an alarm (or snooze) fired: one-time alarms switch themselves off. */
    public static void onRang(Context context, long alarmId, boolean wasSnooze) {
        AlarmStore store = new AlarmStore(context);
        if (wasSnooze) store.clearSnooze(alarmId);
        Alarm a = store.get(alarmId);
        if (a != null && !wasSnooze && !a.isRepeating()) {
            a.enabled = false;               // one-time alarm: done
            store.save(a);                   // save() re-schedules
        } else {
            rescheduleAll(context);
        }
    }

    /** Ring again in {@code minutes} (the Snooze button, issue #15). */
    public static void snooze(Context context, Alarm alarm, int minutes) {
        new AlarmStore(context).setSnooze(alarm.id, System.currentTimeMillis() + minutes * 60_000L);
    }

    private static PendingIntent ringIntent(Context app, AlarmTimes.Next next) {
        Intent i = new Intent(app, AlarmReceiver.class).setAction(ACTION_RING);
        if (next != null) {
            i.putExtra(EXTRA_ALARM_ID, next.alarmId);
            i.putExtra(EXTRA_SNOOZE, next.snooze);
        }
        // same request code + action = the same PendingIntent, so a new one replaces the old
        return PendingIntent.getBroadcast(app, REQUEST_RING, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
