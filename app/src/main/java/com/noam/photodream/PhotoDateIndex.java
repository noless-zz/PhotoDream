package com.noam.photodream;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * When each photo was taken, by {@link Photo#key()}, saved in files/photo_dates.json.
 *
 * Cloud photos lose their EXIF when the sync shrinks them, so the sync records the date the cloud
 * reports (OneDrive {@code photo.takenDateTime}, Drive {@code imageMediaMetadata.time}) here;
 * phone photos are read once from EXIF by {@link DateIndexer}. An empty text means "looked, no date".
 */
public final class PhotoDateIndex {

    private static final String TAG = "PhotoDateIndex";
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static PhotoDateIndex instance;

    private final File file;
    private final Map<String, String> dates = new HashMap<>();   // key -> "2023-06-14" or "" (no date)

    public static synchronized PhotoDateIndex get(Context context) {
        if (instance == null) instance = new PhotoDateIndex(context.getApplicationContext());
        return instance;
    }

    private PhotoDateIndex(Context context) {
        file = new File(context.getFilesDir(), "photo_dates.json");
        load();
    }

    /** The day the photo was taken, or null if unknown. */
    public synchronized LocalDate dateOf(Photo photo) {
        String s = dates.get(photo.key());
        return s == null || s.isEmpty() ? null : PhotoDates.parse(s);
    }

    /** True if we already looked at this photo (even if it had no date). */
    public synchronized boolean has(String key) { return dates.containsKey(key); }

    /** @param date null records "no date found" so we don't look again */
    public synchronized void put(String key, LocalDate date) { dates.put(key, date == null ? "" : date.toString()); }

    /** Forget photos of one source that are no longer there. */
    public synchronized void retainOnly(String sourceId, Set<String> keysStillThere) {
        String prefix = sourceId + ":";
        dates.keySet().removeIf(k -> k.startsWith(prefix) && !keysStillThere.contains(k));
    }

    public void save() {
        final String json;
        synchronized (this) {
            json = new JSONObject(dates).toString();
        }
        WRITER.execute(() -> {
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "Could not save dates", e);
                return;
            }
            if (!tmp.renameTo(file)) Log.w(TAG, "Could not replace " + file);
        });
    }

    private void load() {
        if (!file.isFile()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            JSONObject o = new JSONObject(new String(bytes, 0, Math.max(0, n), StandardCharsets.UTF_8));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                dates.put(k, o.optString(k, ""));
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Ignoring unreadable photo_dates.json", e);
        }
    }
}
