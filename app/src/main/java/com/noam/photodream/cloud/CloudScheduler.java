package com.noam.photodream.cloud;

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

/** Starts / stops the sync jobs of one cloud source. */
public final class CloudScheduler {

    private static final long EVERY_HOURS = 6;

    private CloudScheduler() { }

    /** Unique WorkManager name of the regular sync. */
    public static String periodicName(CloudProvider p) { return p.id() + "-sync"; }

    /** Unique WorkManager name of "Sync now". */
    public static String nowName(CloudProvider p) { return p.id() + "-sync-now"; }

    private static Data input(CloudProvider p, boolean foreground) {
        return new Data.Builder()
                .putString(CloudSyncWorker.KEY_PROVIDER, p.id())
                .putBoolean(CloudSyncWorker.KEY_FOREGROUND, foreground)
                .build();
    }

    /** (Re)schedule the regular sync with the current settings; first run as soon as allowed. */
    public static void schedule(Context context, CloudProvider p) {
        schedule(context, p, 0);
    }

    /** (Re)schedule the regular sync; the first run waits {@code delayHours}. */
    public static void schedule(Context context, CloudProvider p, long delayHours) {
        CloudPrefs prefs = new CloudPrefs(context, p);
        if (!p.isSignedIn(context) || prefs.getFolderId() == null) {
            WorkManager.getInstance(context).cancelUniqueWork(periodicName(p));
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
                CloudSyncWorker.class, EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(c)
                .setInputData(input(p, false))
                .setInitialDelay(delayHours, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(periodicName(p), ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    /**
     * "Sync now": runs as soon as there is any internet connection, as a
     * foreground job with a notification. The regular sync is paused meanwhile
     * (only one may run) and re-scheduled by the worker when it is done.
     */
    public static void syncNow(Context context, CloudProvider p) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(periodicName(p));
        Constraints c = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(CloudSyncWorker.class)
                .setConstraints(c)
                .setInputData(input(p, true))
                .build();
        wm.enqueueUniqueWork(nowName(p), ExistingWorkPolicy.KEEP, req);
    }

    /** Disconnect: stop jobs, forget the account, folder and downloaded photos. */
    public static void disconnect(Context context, CloudProvider p) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(periodicName(p));
        wm.cancelUniqueWork(nowName(p));
        p.signOut(context);
        new CloudPrefs(context, p).clear();
        File[] files = CloudSyncWorker.cacheDir(context, p).listFiles();
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
