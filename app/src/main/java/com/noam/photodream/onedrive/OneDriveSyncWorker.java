package com.noam.photodream.onedrive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.noam.photodream.R;
import com.noam.photodream.source.CacheFolderSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Background job (WorkManager) that copies photos from the chosen OneDrive
 * folder into files/photo_cache/onedrive/, where {@link CacheFolderSource}
 * picks them up for the screensaver.
 *
 * Photos are shrunk to screen size and saved as JPEG, so 200 photos take
 * roughly 100 MB instead of ~1 GB of originals.
 *
 * If Android stops the job (time limit, charger unplugged), nothing is lost:
 * finished downloads stay, and the next run continues.
 */
public class OneDriveSyncWorker extends Worker {

    private static final String TAG = "OneDriveSync";
    private static final int MAX_LISTED = 20_000;
    private static final int SUBFOLDER_DEPTH = 3;
    private static final double ROTATE_FRACTION = 0.2;
    private static final int JPEG_QUALITY = 88;

    /** Input flag: run with a foreground notification ("Sync now"). */
    public static final String KEY_FOREGROUND = "foreground";
    /** Progress keys, read by the settings screen. */
    public static final String PROGRESS_DONE = "done";
    public static final String PROGRESS_TOTAL = "total";

    private static final String CHANNEL = "onedrive_sync";
    private static final int NOTIFICATION_ID = 4711;
    private static final ReentrantLock RUNNING = new ReentrantLock();

    public OneDriveSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static File cacheDir(Context context) {
        return new File(CacheFolderSource.cacheRoot(context), "onedrive");
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        OneDrivePrefs prefs = new OneDrivePrefs(ctx);
        if (!OneDriveConfig.isConfigured(ctx) || !OneDriveAuth.isSignedIn(ctx)
                || prefs.getFolderId() == null) {
            return Result.success();   // nothing to do
        }
        // "Sync now" and the regular sync must never run at the same time
        boolean syncNow = getInputData().getBoolean(KEY_FOREGROUND, false);
        try {
            // "Sync now" waits a moment for a cancelled regular sync to wind down
            if (!(syncNow ? RUNNING.tryLock(90, TimeUnit.SECONDS) : RUNNING.tryLock())) {
                return Result.success();
            }
        } catch (InterruptedException e) {
            return Result.retry();
        }
        Result result;
        try {
            if (syncNow) goForeground(0, 0);
            String summary = sync(ctx, prefs);
            prefs.setLastSync(System.currentTimeMillis(), summary);
            result = isStopped() ? Result.retry() : Result.success();
        } catch (OneDriveAuth.NotSignedInException e) {
            prefs.setLastSync(System.currentTimeMillis(), e.getMessage());
            result = Result.failure();   // retrying won't help until the user signs in
        } catch (IOException e) {
            if (isStopped()) {
                // Android paused us (left the app, Wi-Fi lost, charger unplugged): not an error
                prefs.setLastSync(System.currentTimeMillis(),
                        ctx.getString(R.string.onedrive_paused, timeNow()));
                result = Result.retry();
            } else {
                Log.w(TAG, "Sync failed", e);
                prefs.setLastSync(System.currentTimeMillis(), "Failed: " + e.getMessage());
                result = getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
            }
        } finally {
            RUNNING.unlock();
        }
        // "Sync now" paused the regular schedule – bring it back once we are done for good
        if (syncNow && !result.equals(Result.retry())) OneDriveScheduler.schedule(ctx, 6);
        return result;
    }

    private String sync(Context ctx, OneDrivePrefs prefs) throws IOException {
        GraphClient graph = new GraphClient(ctx);
        int depth = prefs.isIncludeSubfolders() ? SUBFOLDER_DEPTH : 0;
        List<GraphClient.DriveItem> remote = graph.listImages(prefs.getFolderId(), depth, MAX_LISTED);

        File dir = cacheDir(ctx);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);

        // what we already have: file name <-> OneDrive id
        Map<String, String> fileForId = new HashMap<>();
        List<String> remoteIds = new ArrayList<>();
        for (GraphClient.DriveItem item : remote) {
            remoteIds.add(item.id);
            fileForId.put(item.id, fileName(item.id));
        }
        Set<String> cachedIds = new HashSet<>();
        List<File> orphanFiles = new ArrayList<>();
        File[] existing = dir.listFiles();
        if (existing != null) {
            Map<String, String> idForFile = new HashMap<>();
            for (Map.Entry<String, String> e : fileForId.entrySet()) idForFile.put(e.getValue(), e.getKey());
            for (File f : existing) {
                String id = idForFile.get(f.getName());
                if (id != null) cachedIds.add(id);
                else orphanFiles.add(f);     // deleted in OneDrive, or a leftover temp file
            }
        }

        SyncPlanner.Plan plan = SyncPlanner.plan(remoteIds, cachedIds, prefs.getMaxPhotos(),
                ROTATE_FRACTION, new Random());

        // photos deleted from OneDrive go right away
        for (File f : orphanFiles) delete(f);

        int added = 0, failed = 0;
        int total = plan.download.size();
        int targetLongSide = screenLongSide(ctx);
        for (String id : plan.download) {
            if (isStopped()) break;
            reportProgress(added + failed, total);
            File tmp = new File(dir, fileForId.get(id) + ".part");
            try {
                graph.download(id, tmp);
                if (shrink(tmp, new File(dir, fileForId.get(id)), targetLongSide)) added++;
                else failed++;
            } catch (OneDriveAuth.NotSignedInException e) {
                throw e;
            } catch (IOException e) {
                if (isStopped()) break;
                Log.w(TAG, "Download failed for " + id, e);
                failed++;
            } finally {
                delete(tmp);
            }
        }

        // rotated-out photos leave only as their replacements arrive,
        // so an interrupted sync never shrinks the collection
        int toDelete = isStopped() ? Math.min(added, plan.delete.size()) : plan.delete.size();
        int deleted = 0;
        for (String id : plan.delete) {
            if (deleted++ >= toDelete) break;
            delete(new File(dir, fileForId.get(id)));
        }

        int onPhone = countPhotos(dir);
        StringBuilder s = new StringBuilder();
        s.append(timeNow()).append(" – ").append(onPhone).append(" photos on phone (")
                .append(added).append(" new");
        if (failed > 0) s.append(", ").append(failed).append(" skipped");
        s.append(")");
        if (isStopped()) s.append(" – paused, will continue");
        return s.toString();
    }

    // ---------------------------------------------------------------- progress / foreground

    private void reportProgress(int done, int total) {
        setProgressAsync(new Data.Builder().putInt(PROGRESS_DONE, done).putInt(PROGRESS_TOTAL, total).build());
        if (getInputData().getBoolean(KEY_FOREGROUND, false)) goForeground(done, total);
    }

    /**
     * Runs the job as a foreground service with a notification. Android then
     * keeps its internet access when the user leaves the app. Only allowed
     * when started from the app (the "Sync now" button), so failures are ignored.
     */
    private void goForeground(int done, int total) {
        try {
            setForegroundAsync(foregroundInfo(getApplicationContext(), done, total)).get();
        } catch (Exception e) {
            Log.i(TAG, "Running without foreground notification: " + e);
        }
    }

    private static ForegroundInfo foregroundInfo(Context ctx, int done, int total) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    ctx.getString(R.string.onedrive_channel), NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(ctx.getString(R.string.onedrive_notification_title))
                .setContentText(total > 0 ? ctx.getString(R.string.onedrive_progress, done, total)
                        : ctx.getString(R.string.onedrive_listing))
                .setProgress(total, done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        return new ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    private static int countPhotos(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        return files == null ? 0 : files.length;
    }

    private static String timeNow() {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
    }

    /** Decode at ~screen size and save as JPEG. Returns false for files Android can't read. */
    private static boolean shrink(File original, File target, int targetLongSide) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(original);
            Bitmap bmp = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);   // needed for compress()
                Size size = info.getSize();
                int longSide = Math.max(size.getWidth(), size.getHeight());
                if (longSide > targetLongSide) {
                    float scale = (float) targetLongSide / longSide;
                    decoder.setTargetSize(Math.max(1, Math.round(size.getWidth() * scale)),
                            Math.max(1, Math.round(size.getHeight() * scale)));
                }
            });
            File tmp = new File(target.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            } finally {
                bmp.recycle();
            }
            return tmp.renameTo(target);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Cannot decode " + original.getName(), e);
            return false;
        }
    }

    /** OneDrive ids can contain characters like '!' – make a safe, stable file name. */
    static String fileName(String id) {
        return "od_" + id.replaceAll("[^A-Za-z0-9._-]", "_") + ".jpg";
    }

    private static int screenLongSide(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return Math.max(1280, Math.max(dm.widthPixels, dm.heightPixels));
    }

    private static void delete(File f) {
        if (f.exists() && !f.delete()) Log.w(TAG, "Could not delete " + f);
    }
}
