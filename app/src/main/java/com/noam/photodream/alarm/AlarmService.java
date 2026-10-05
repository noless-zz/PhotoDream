package com.noam.photodream.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.noam.photodream.R;

import java.io.IOException;

/**
 * Plays the alarm. A foreground service (type mediaPlayback) so Android keeps it alive and the
 * sound goes on even when the user leaves the ringing screen.
 *
 * Sound: USAGE_ALARM, so it rings even in silent / Do-not-disturb, like a normal alarm clock.
 * The volume ramps from 10% up (see {@link AlarmPolicy}); while the user touches the ringing
 * screen it drops to 30% so they can concentrate, and rises again after 15 s without touches.
 * After 15 minutes it stops by itself and leaves a "Missed alarm" notification.
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";
    private static final String ACTION_START = "com.noam.photodream.alarm.START";
    private static final String ACTION_STOP = "com.noam.photodream.alarm.STOP";
    private static final String ACTION_SNOOZE = "com.noam.photodream.alarm.SNOOZE";
    private static final String CHANNEL_RINGING = "alarm_ringing";
    private static final String CHANNEL_MISSED = "alarm_missed";
    private static final int NOTIFICATION_ID = 7001;
    private static final int MISSED_ID = 7002;
    private static final long TICK_MS = 250;

    /** The alarm that is ringing right now (0 = none). The ringing screen reads it. */
    private static volatile long ringingAlarmId;
    private static volatile long ringingSinceElapsed;
    private static volatile long lastTouchElapsed = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private Alarm alarm;
    private float volume = AlarmPolicy.START_VOLUME;
    private long lastTick;

    // ---------------------------------------------------------------- API for the rest of the app

    public static void start(Context context, long alarmId, boolean fromSnooze) {
        Intent i = new Intent(context, AlarmService.class).setAction(ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_SNOOZE, fromSnooze);
        ContextCompat.startForegroundService(context, i);
    }

    /** Solved (or given up with the code): silence everything. */
    public static void stop(Context context) {
        context.startService(new Intent(context, AlarmService.class).setAction(ACTION_STOP));
    }

    /** Snooze: stop now, ring again after the alarm's snooze minutes. */
    public static void snooze(Context context) {
        context.startService(new Intent(context, AlarmService.class).setAction(ACTION_SNOOZE));
    }

    /** The user touched the ringing screen: lower the volume for a while. */
    public static void userTouched() { lastTouchElapsed = SystemClock.elapsedRealtime(); }

    public static boolean isRinging() { return ringingAlarmId != 0; }
    public static long ringingAlarmId() { return ringingAlarmId; }

    /** Milliseconds the current alarm has been ringing (0 if none). */
    public static long ringingMillis() {
        return isRinging() ? SystemClock.elapsedRealtime() - ringingSinceElapsed : 0;
    }

    // ---------------------------------------------------------------- service

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            begin(intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0),
                    intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false));
        } else if (ACTION_SNOOZE.equals(action)) {
            doSnooze();
        } else if (ACTION_STOP.equals(action)) {
            finishRinging();
        } else if (!isRinging()) {
            stopSelf();      // restarted by the system with no alarm: nothing to do
        }
        return START_NOT_STICKY;
    }

    private void begin(long alarmId, boolean fromSnooze) {
        AlarmStore store = new AlarmStore(this);
        Alarm a = store.get(alarmId);
        if (a == null) {                               // deleted while the snooze was pending
            stopSelf();
            return;
        }
        if (isRinging() && ringingAlarmId == alarmId) return;   // already ringing this one
        if (isRinging()) releaseOutputs();             // another alarm takes over

        alarm = a;
        if (!fromSnooze) store.resetSnoozesUsed(alarmId);
        ringingAlarmId = alarmId;
        ringingSinceElapsed = SystemClock.elapsedRealtime();
        lastTouchElapsed = -1;
        volume = AlarmPolicy.rampVolume(0, a.rampSeconds);

        startForeground(NOTIFICATION_ID, buildNotification(a), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "photodream:alarm");
        wakeLock.acquire(AlarmPolicy.AUTO_STOP_MS + 60_000L);

        startSound(a);
        if (a.vibrate) startVibration();
        lastTick = SystemClock.elapsedRealtime();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);

        // also open the screen right away when we are allowed to (the full-screen intent covers the lock screen)
        try {
            startActivity(AlarmActivity.intent(this, alarmId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.i(TAG, "Could not open the ringing screen directly; the notification will", e);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isRinging()) return;
            long now = SystemClock.elapsedRealtime();
            long ringing = now - ringingSinceElapsed;
            if (AlarmPolicy.shouldAutoStop(ringing)) {
                showMissedNotification();
                finishRinging();
                return;
            }
            long sinceTouch = lastTouchElapsed < 0 ? -1 : now - lastTouchElapsed;
            float target = AlarmPolicy.targetVolume(ringing, alarm.rampSeconds, sinceTouch);
            volume = AlarmPolicy.stepVolume(volume, target, now - lastTick);
            lastTick = now;
            if (player != null) {
                try {
                    player.setVolume(volume, volume);
                } catch (IllegalStateException ignored) {
                    // player already released
                }
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private void doSnooze() {
        if (alarm == null) {
            finishRinging();
            return;
        }
        AlarmStore store = new AlarmStore(this);
        if (AlarmPolicy.canSnooze(store.snoozesUsed(alarm.id))) {
            store.addSnoozeUsed(alarm.id);
            AlarmScheduler.snooze(this, alarm, alarm.snoozeMinutes);
        }
        finishRinging();
    }

    private void finishRinging() {
        releaseOutputs();
        ringingAlarmId = 0;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseOutputs() {
        handler.removeCallbacks(tick);
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // never started
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        releaseOutputs();
        ringingAlarmId = 0;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- sound, vibration

    private void startSound(Alarm a) {
        Uri[] candidates = {
                a.soundUri == null ? null : Uri.parse(a.soundUri),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)};
        for (Uri uri : candidates) {
            if (uri == null) continue;
            MediaPlayer p = new MediaPlayer();
            try {
                p.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                p.setDataSource(this, uri);
                p.setLooping(true);
                p.setVolume(volume, volume);
                p.prepare();
                p.start();
                player = p;
                return;
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Cannot play " + uri + ", trying the next sound", e);
                p.release();
            }
        }
        Log.w(TAG, "No sound could be played – vibration only");
    }

    private void startVibration() {
        if (Build.VERSION.SDK_INT >= 31) {
            vibrator = getSystemService(VibratorManager.class).getDefaultVibrator();
        } else {
            vibrator = getSystemService(Vibrator.class);
        }
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 600, 400, 600, 1400};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
    }

    // ---------------------------------------------------------------- notifications

    private Notification buildNotification(Alarm a) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_RINGING) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_RINGING,
                    getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, AlarmActivity.intent(this, a.id),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_RINGING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.alarm_ringing))
                .setContentText(a.label.isEmpty() ? getString(R.string.alarm_tap_to_open) : a.label)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .build();
    }

    private void showMissedNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_MISSED) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_MISSED,
                    getString(R.string.alarm_missed_channel), NotificationManager.IMPORTANCE_DEFAULT));
        }
        String time = String.format(java.util.Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute);
        nm.notify(MISSED_ID, new Notification.Builder(this, CHANNEL_MISSED)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.alarm_missed))
                .setContentText(getString(R.string.alarm_missed_text, time))
                .setAutoCancel(true)
                .build());
    }
}
