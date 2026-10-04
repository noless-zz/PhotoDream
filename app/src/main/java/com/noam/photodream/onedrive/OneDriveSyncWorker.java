package com.noam.photodream.onedrive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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

        try {
            String summary = sync(ctx, prefs);
            prefs.setLastSync(System.currentTimeMillis(), summary);
            return Result.success();
        } catch (OneDriveAuth.NotSignedInException e) {
            prefs.setLastSync(System.currentTimeMillis(), e.getMessage());
            return Result.failure();   // retrying won't help until the user signs in
        } catch (IOException e) {
            Log.w(TAG, "Sync failed", e);
            prefs.setLastSync(System.currentTimeMillis(), "Failed: " + e.getMessage());
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
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
        Map<String, File> orphanFiles = new HashMap<>();
        File[] existing = dir.listFiles();
        if (existing != null) {
            Map<String, String> idForFile = new HashMap<>();
            for (Map.Entry<String, String> e : fileForId.entrySet()) idForFile.put(e.getValue(), e.getKey());
            for (File f : existing) {
                String id = idForFile.get(f.getName());
                if (id != null) cachedIds.add(id);
                else orphanFiles.put(f.getName(), f);     // deleted in OneDrive, or a leftover temp file
            }
        }

        SyncPlanner.Plan plan = SyncPlanner.plan(remoteIds, cachedIds, prefs.getMaxPhotos(),
                ROTATE_FRACTION, new Random());

        for (File f : orphanFiles.values()) delete(f);
        for (String id : plan.delete) delete(new File(dir, fileForId.get(id)));

        int added = 0, failed = 0;
        int targetLongSide = screenLongSide(ctx);
        for (String id : plan.download) {
            if (isStopped()) break;
            File tmp = new File(dir, fileForId.get(id) + ".part");
            try {
                graph.download(id, tmp);
                if (shrink(tmp, new File(dir, fileForId.get(id)), targetLongSide)) added++;
                else failed++;
            } catch (OneDriveAuth.NotSignedInException e) {
                throw e;
            } catch (IOException e) {
                Log.w(TAG, "Download failed for " + id, e);
                failed++;
            } finally {
                delete(tmp);
            }
        }

        int onPhone = plan.keep.size() + added;
        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        StringBuilder s = new StringBuilder();
        s.append(when).append(" – ").append(onPhone).append(" photos on phone (")
                .append(added).append(" new");
        if (failed > 0) s.append(", ").append(failed).append(" skipped");
        s.append(")");
        if (isStopped()) s.append(" – paused, will continue");
        return s.toString();
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
