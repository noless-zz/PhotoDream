package com.noam.photodream.cloud;

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

import com.noam.photodream.PhotoDateIndex;
import com.noam.photodream.PhotoDates;
import com.noam.photodream.PhotoMarksStore;
import com.noam.photodream.R;
import com.noam.photodream.source.CacheFolderSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Background job (WorkManager) that copies photos from the chosen cloud
 * folder (OneDrive, Google Drive, …) into files/photo_cache/&lt;provider&gt;/,
 * where {@link CacheFolderSource} picks them up for the screensaver.
 * Input data says which provider.
 *
 * Photos are shrunk to screen size and saved as JPEG, so 200 photos take
 * roughly 100 MB instead of ~1 GB of originals.
 *
 * If Android stops the job (time limit, charger unplugged), nothing is lost:
 * finished downloads stay, and the next run continues.
 */
public class CloudSyncWorker extends Worker {

    private static final String TAG = "CloudSync";
    private static final int MAX_LISTED = 20_000;
    private static final int SUBFOLDER_DEPTH = 3;
    private static final double ROTATE_FRACTION = 0.2;
    private static final int JPEG_QUALITY = 88;

    /** Input: id of the {@link CloudProvider} to sync. */
    public static final String KEY_PROVIDER = "provider";
    /** Input flag: run with a foreground notification ("Sync now"). */
    public static final String KEY_FOREGROUND = "foreground";
    /** Progress keys, read by the settings screen. */
    public static final String PROGRESS_DONE = "done";
    public static final String PROGRESS_TOTAL = "total";

    private static final String CHANNEL = "cloud_sync";
    private static final Map<String, ReentrantLock> LOCKS = new HashMap<>();

    private static synchronized ReentrantLock lockFor(String providerId) {
        ReentrantLock l = LOCKS.get(providerId);
        if (l == null) {
            l = new ReentrantLock();
            LOCKS.put(providerId, l);
        }
        return l;
    }

    public CloudSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static File cacheDir(Context context, CloudProvider provider) {
        return new File(CacheFolderSource.cacheRoot(context), provider.id());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        CloudProvider provider;
        try {
            provider = CloudProviders.get(getInputData().getString(KEY_PROVIDER));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Result.failure();
        }
        CloudPrefs prefs = new CloudPrefs(ctx, provider);
        if (!provider.isConfigured(ctx) || !provider.isSignedIn(ctx) || prefs.getFolderId() == null) {
            return Result.success();   // nothing to do
        }
        final ReentrantLock running = lockFor(provider.id());
        boolean syncNow = getInputData().getBoolean(KEY_FOREGROUND, false);
        try {
            // "Sync now" waits a moment for a cancelled regular sync to wind down
            if (!(syncNow ? running.tryLock(90, TimeUnit.SECONDS) : running.tryLock())) {
                return Result.success();
            }
        } catch (InterruptedException e) {
            return Result.retry();
        }
        Result result;
        try {
            if (syncNow) goForeground(provider, 0, 0);
            String summary = sync(ctx, provider, prefs);
            prefs.setLastSync(System.currentTimeMillis(), summary);
            result = isStopped() ? Result.retry() : Result.success();
        } catch (NotSignedInException e) {
            prefs.setLastSync(System.currentTimeMillis(), e.getMessage());
            result = Result.failure();   // retrying won't help until the user signs in
        } catch (IOException e) {
            if (isStopped()) {
                // Android paused us (left the app, Wi-Fi lost, charger unplugged): not an error
                prefs.setLastSync(System.currentTimeMillis(),
                        ctx.getString(R.string.cloud_paused, timeNow()));
                result = Result.retry();
            } else {
                Log.w(TAG, "Sync failed", e);
                prefs.setLastSync(System.currentTimeMillis(), "Failed: " + e.getMessage());
                result = getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
            }
        } finally {
            running.unlock();
        }
        // new photos arrived: describe them while charging (labels, on the phone)
        com.noam.photodream.describe.DescriptionWorker.schedule(ctx, androidx.work.ExistingWorkPolicy.KEEP);
        // "Sync now" paused the regular schedule – bring it back once we are done for good
        if (syncNow && !result.equals(Result.retry())) CloudScheduler.schedule(ctx, provider, 6);
        return result;
    }

    private String sync(Context ctx, CloudProvider provider, CloudPrefs prefs) throws IOException {
        int depth = prefs.isIncludeSubfolders() ? SUBFOLDER_DEPTH : 0;
        List<CloudItem> remote = new ArrayList<>();
        collectImages(ctx, provider, prefs.getFolderId(), depth, remote);

        File dir = cacheDir(ctx, provider);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);

        // the shrunken copies lose their EXIF, so remember when each photo was taken, as the cloud says
        PhotoDateIndex dateIndex = PhotoDateIndex.get(ctx);
        java.util.Set<String> keysInCloud = new HashSet<>();
        for (CloudItem item : remote) {
            String key = provider.id() + ":" + fileName(provider, item.id);
            keysInCloud.add(key);
            if (item.takenDate != null) dateIndex.put(key, PhotoDates.parse(item.takenDate));
        }
        dateIndex.retainOnly(provider.id(), keysInCloud);
        dateIndex.save();

        // what we already have: file name <-> cloud id
        Map<String, String> fileForId = new HashMap<>();
        List<String> remoteIds = new ArrayList<>();
        for (CloudItem item : remote) {
            remoteIds.add(item.id);
            fileForId.put(item.id, fileName(provider, item.id));
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
                else if (!f.equals(failuresFile(dir))) orphanFiles.add(f);     // deleted in the cloud, or a leftover temp file
            }
        }

        // photos Android can't decode: don't download them again and again
        DecodeFailures failures = loadFailures(dir);
        failures.retainOnly(new HashSet<>(remoteIds));
        Set<String> unsupported = failures.excluded();

        // photos the user marked as favorite stay in the cache (marks are stored by file name)
        Set<String> favoriteNames = PhotoMarksStore.get(ctx).marks().favoriteNamesIn(provider.id());
        Set<String> favoriteIds = new HashSet<>();
        for (Map.Entry<String, String> e : fileForId.entrySet()) {
            if (favoriteNames.contains(e.getValue())) favoriteIds.add(e.getKey());
        }

        SyncPlanner.Plan plan = SyncPlanner.plan(remoteIds, cachedIds, unsupported, favoriteIds, prefs.getMaxPhotos(),
                ROTATE_FRACTION, new Random());

        // photos deleted from the cloud go right away
        for (File f : orphanFiles) delete(f);

        int added = 0, failed = 0;
        int total = plan.download.size();
        int targetLongSide = screenLongSide(ctx);
        for (String id : plan.download) {
            if (isStopped()) break;
            reportProgress(provider, added + failed, total);
            File tmp = new File(dir, fileForId.get(id) + ".part");
            try {
                provider.download(ctx, id, tmp);
                if (shrink(tmp, new File(dir, fileForId.get(id)), targetLongSide)) {
                    added++;
                    failures.recordSuccess(id);
                } else {
                    failed++;
                    failures.recordDecodeFailure(id);   // the download itself worked, so this is the format
                }
            } catch (NotSignedInException e) {
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

        saveFailures(dir, failures);

        int onPhone = countPhotos(dir);
        StringBuilder s = new StringBuilder();
        s.append(timeNow()).append(" – ").append(onPhone).append(" photos on phone (")
                .append(added).append(" new");
        if (failed > 0) s.append(", ").append(failed).append(" skipped");
        s.append(")");
        if (!unsupported.isEmpty()) {
            s.append(" ").append(ctx.getResources().getQuantityString(R.plurals.cloud_unsupported_skipped, unsupported.size(), unsupported.size()));
        }
        if (isStopped()) s.append(" – paused, will continue");
        return s.toString();
    }

    // ---------------------------------------------------------------- progress / foreground

    /** Walk the folder (and sub-folders up to {@code depthLeft}) collecting images. */
    private void collectImages(Context ctx, CloudProvider provider, String folderId, int depthLeft,
                               List<CloudItem> out) throws IOException {
        for (CloudItem item : provider.listChildren(ctx, folderId)) {
            if (out.size() >= MAX_LISTED || isStopped()) return;
            if (item.image) out.add(item);
            else if (item.folder && depthLeft > 0) collectImages(ctx, provider, item.id, depthLeft - 1, out);
        }
    }

    private void reportProgress(CloudProvider provider, int done, int total) {
        setProgressAsync(new Data.Builder().putInt(PROGRESS_DONE, done).putInt(PROGRESS_TOTAL, total).build());
        if (getInputData().getBoolean(KEY_FOREGROUND, false)) goForeground(provider, done, total);
    }

    /**
     * Runs the job as a foreground service with a notification. Android then
     * keeps its internet access when the user leaves the app. Only allowed
     * when started from the app (the "Sync now" button), so failures are ignored.
     */
    private void goForeground(CloudProvider provider, int done, int total) {
        try {
            setForegroundAsync(foregroundInfo(getApplicationContext(), provider, done, total)).get();
        } catch (Exception e) {
            Log.i(TAG, "Running without foreground notification: " + e);
        }
    }

    private static ForegroundInfo foregroundInfo(Context ctx, CloudProvider provider, int done, int total) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    ctx.getString(R.string.cloud_channel), NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(ctx.getString(R.string.cloud_notification_title, provider.displayName(ctx)))
                .setContentText(total > 0 ? ctx.getString(R.string.cloud_progress, done, total)
                        : ctx.getString(R.string.cloud_listing))
                .setProgress(total, done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        int notificationId = 4700 + Math.abs(provider.id().hashCode() % 100);
        return new ForegroundInfo(notificationId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    private static File failuresFile(File cacheDir) {
        return new File(cacheDir, "failed.json");
    }

    private static DecodeFailures loadFailures(File cacheDir) {
        Map<String, Integer> saved = new HashMap<>();
        File f = failuresFile(cacheDir);
        if (f.isFile()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                byte[] bytes = new byte[(int) f.length()];
                int n = in.read(bytes);
                JSONObject o = new JSONObject(new String(bytes, 0, Math.max(n, 0), java.nio.charset.StandardCharsets.UTF_8));
                for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String id = it.next();
                    saved.put(id, o.getInt(id));
                }
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Ignoring unreadable failed.json", e);
            }
        }
        return new DecodeFailures(saved);
    }

    private static void saveFailures(File cacheDir, DecodeFailures failures) {
        File f = failuresFile(cacheDir);
        if (failures.asMap().isEmpty()) {
            delete(f);
            return;
        }
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(new JSONObject(failures.asMap()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "Could not save failed.json", e);
        }
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

    /** Cloud ids can contain characters like '!' – make a safe, stable file name. */
    static String fileName(CloudProvider provider, String id) {
        return provider.filePrefix() + id.replaceAll("[^A-Za-z0-9._-]", "_") + ".jpg";
    }

    private static int screenLongSide(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return Math.max(1280, Math.max(dm.widthPixels, dm.heightPixels));
    }

    private static void delete(File f) {
        if (f.exists() && !f.delete()) Log.w(TAG, "Could not delete " + f);
    }
}
