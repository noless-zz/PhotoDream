package com.noam.photodream.onedrive;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Starts / stops the OneDrive sync jobs. */
public final class OneDriveScheduler {

    public static final String PERIODIC = "onedrive-sync";
    public static final String NOW = "onedrive-sync-now";
    private static final long EVERY_HOURS = 6;

    private OneDriveScheduler() { }

    /** (Re)schedule the regular sync with the current settings; first run as soon as allowed. */
    public static void schedule(Context context) {
        schedule(context, 0);
    }

    /** (Re)schedule the regular sync; the first run waits {@code delayHours}. */
    public static void schedule(Context context, long delayHours) {
        OneDrivePrefs prefs = new OneDrivePrefs(context);
        if (!OneDriveAuth.isSignedIn(context) || prefs.getFolderId() == null) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC);
            return;
        }
        boolean strict = prefs.isWifiChargingOnly();
        Constraints c = new Constraints.Builder()
                .setRequiredNetworkType(strict ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresCharging(strict)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                OneDriveSyncWorker.class, EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(c)
                .setInitialDelay(delayHours, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    /**
     * "Sync now" button: runs as soon as there is any internet connection, as a
     * foreground job with a notification. The regular sync is paused meanwhile
     * (only one may run) and re-scheduled by the worker when it is done.
     */
    public static void syncNow(Context context) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(PERIODIC);
        Constraints c = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(OneDriveSyncWorker.class)
                .setConstraints(c)
                .setInputData(new Data.Builder().putBoolean(OneDriveSyncWorker.KEY_FOREGROUND, true).build())
                .build();
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req);
    }

    /** Disconnect: stop jobs, forget tokens, folder and downloaded photos. */
    public static void disconnect(Context context) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(PERIODIC);
        wm.cancelUniqueWork(NOW);
        OneDriveAuth.signOut(context);
        new OneDrivePrefs(context).clear();
        File[] files = OneDriveSyncWorker.cacheDir(context).listFiles();
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
