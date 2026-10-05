package com.noam.photodream.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.noam.photodream.R;

/**
 * Receives (1) the moment an alarm rings and (2) every event after which the system alarm must be
 * set again: reboot, time or time-zone change, app update.
 *
 * Direct-boot ("LOCKED_BOOT_COMPLETED") is not handled: the alarms live in normal storage, which
 * is locked until the user unlocks the phone once after a restart; BOOT_COMPLETED then re-arms them.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    static final String CHANNEL = "alarms";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (AlarmScheduler.ACTION_RING.equals(action)) {
            long id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0);
            boolean snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false);
            Log.i(TAG, "Alarm " + id + " rang" + (snooze ? " (snooze)" : ""));
            try {
                AlarmService.start(context, id, snooze);
            } catch (RuntimeException e) {
                // Android refused to start the foreground service: at least show a notification
                Log.w(TAG, "Could not start the alarm service", e);
                showRingingNotification(context, id);
            }
            AlarmScheduler.onRang(context, id, snooze);
        } else {
            // BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED
            Log.i(TAG, "Re-scheduling after " + action);
            AlarmScheduler.rescheduleAll(context);
        }
    }

    /** Fallback only: the normal path is {@link AlarmService}. */
    private void showRingingNotification(Context context, long alarmId) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    context.getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Alarm alarm = new AlarmStore(context).get(alarmId);
        String text = alarm != null && !alarm.label.isEmpty() ? alarm.label : context.getString(R.string.alarm_ringing);
        android.app.PendingIntent open = android.app.PendingIntent.getActivity(context, 0,
                AlarmActivity.intent(context, alarmId),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(context, CHANNEL)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.alarm_ringing))
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (6000 + alarmId % 1000), n);
    }
}
