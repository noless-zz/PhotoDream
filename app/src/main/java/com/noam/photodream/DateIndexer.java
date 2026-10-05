package com.noam.photodream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.ExifInterface;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the date taken (EXIF DateTimeOriginal) of phone photos, a few hundred per run, in the
 * background, and keeps it in {@link PhotoDateIndex}. Cloud photos are skipped: their date comes
 * from the cloud during sync (the shrunken copies have no EXIF).
 */
public final class DateIndexer {

    private static final String TAG = "DateIndexer";

    private DateIndexer() { }

    /** Blocking: call from a background thread. */
    public static void indexLocal(Context context, List<Photo> photos, int maxPerRun) {
        PhotoDateIndex index = PhotoDateIndex.get(context);
        Set<String> present = new HashSet<>();
        int done = 0;
        for (Photo p : photos) {
            if (!"local".equals(p.sourceId)) continue;
            present.add(p.key());
            if (done >= maxPerRun || index.has(p.key())) continue;
            index.put(p.key(), readDate(context, p));
            done++;
        }
        index.retainOnly("local", present);
        if (done > 0) index.save();
    }

    // android.media.ExifInterface can read an InputStream since API 24 – enough here, and the
    // androidx replacement would be a new dependency (not allowed without asking).
    @SuppressLint("ExifInterface")
    private static LocalDate readDate(Context context, Photo photo) {
        try (InputStream in = context.getContentResolver().openInputStream(photo.uri)) {
            if (in == null) return null;
            ExifInterface exif = new ExifInterface(in);
            String taken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (taken == null) taken = exif.getAttribute(ExifInterface.TAG_DATETIME);
            return PhotoDates.parse(taken);
        } catch (IOException | RuntimeException e) {
            Log.i(TAG, "No EXIF date for a photo", e);
            return null;
        }
    }
}
