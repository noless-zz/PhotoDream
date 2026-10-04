package com.noam.photodream.onedrive;

import android.content.Context;

import androidx.work.Constraints;
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

    /** (Re)schedule the regular sync with the current settings. */
    public static void schedule(Context context) {
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
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    /** "Sync now" button: runs as soon as there is any internet connection. */
    public static void syncNow(Context context) {
        Constraints c = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(OneDriveSyncWorker.class)
                .setConstraints(c)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req);
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
