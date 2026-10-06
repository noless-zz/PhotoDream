package com.noam.photodream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Finds faces on the phone (ML Kit, fast mode, nothing leaves the device) and remembers the boxes
 * by {@link Photo#key()} in files/photo_faces.json, so a photo is analysed only once.
 * Boxes are fractions of the image: {left, top, right, bottom}.
 */
public final class FaceFinder {

    public interface Callback {
        /** Main thread. Empty list = no faces (or detection not possible). */
        void onFaces(List<float[]> faces);
    }

    private static final String TAG = "FaceFinder";
    private static final int DETECT_LONG_SIDE = 640;       // detection works fine on a small copy
    private static final long TIMEOUT_S = 4;

    private static FaceFinder instance;

    private final File file;
    private final Map<String, List<float[]>> known = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private FaceDetector detector;

    public static synchronized FaceFinder get(Context context) {
        if (instance == null) instance = new FaceFinder(context.getApplicationContext());
        return instance;
    }

    private FaceFinder(Context context) {
        file = new File(context.getFilesDir(), "photo_faces.json");
        load();
    }

    /**
     * Calls back on the main thread – at once if this photo was analysed before, otherwise after the
     * detection (usually well under a second). Call from the main thread.
     */
    public void find(Photo photo, Bitmap bitmap, Callback callback) {
        List<float[]> cached;
        synchronized (this) {
            cached = known.get(photo.key());
        }
        if (cached != null) {
            callback.onFaces(cached);
            return;
        }
        executor.execute(() -> {
            List<float[]> faces = detect(bitmap);
            if (faces != null) {
                synchronized (this) {
                    known.put(photo.key(), faces);
                }
                save();
            }
            List<float[]> result = faces == null ? Collections.<float[]>emptyList() : faces;
            main.post(() -> callback.onFaces(result));
        });
    }

    /** @return the boxes, or null if the detector could not run (then we try again next time). */
    private List<float[]> detect(Bitmap bitmap) {
        try {
            if (detector == null) {
                detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .build());
            }
            // a software copy at small size: decoded photos may be hardware bitmaps, which ML Kit can't read
            Bitmap soft = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (soft == null) return null;
            float scale = Math.min(1f, DETECT_LONG_SIDE / (float) Math.max(soft.getWidth(), soft.getHeight()));
            Bitmap small = scale < 1f
                    ? Bitmap.createScaledBitmap(soft, Math.max(1, Math.round(soft.getWidth() * scale)),
                    Math.max(1, Math.round(soft.getHeight() * scale)), true)
                    : soft;
            List<Face> faces = Tasks.await(detector.process(InputImage.fromBitmap(small, 0)), TIMEOUT_S, TimeUnit.SECONDS);
            List<float[]> out = new ArrayList<>();
            for (Face f : faces) {
                Rect r = f.getBoundingBox();
                out.add(new float[]{
                        clamp01(r.left / (float) small.getWidth()), clamp01(r.top / (float) small.getHeight()),
                        clamp01(r.right / (float) small.getWidth()), clamp01(r.bottom / (float) small.getHeight())});
            }
            if (small != soft) small.recycle();
            soft.recycle();
            return out;
        } catch (Exception | LinkageError e) {
            Log.w(TAG, "Face detection not available", e);
            return null;
        }
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ---------------------------------------------------------------- persistence

    private synchronized void save() {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, List<float[]>> e : known.entrySet()) {
                JSONArray boxes = new JSONArray();
                for (float[] b : e.getValue()) {
                    JSONArray box = new JSONArray();
                    for (float v : b) box.put((double) v);
                    boxes.put(box);
                }
                o.put(e.getKey(), boxes);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Could not build faces JSON", e);
            return;
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "Could not save faces", e);
            return;
        }
        if (!tmp.renameTo(file)) Log.w(TAG, "Could not replace " + file);
    }

    private void load() {
        if (!file.isFile()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            JSONObject o = new JSONObject(new String(bytes, 0, Math.max(0, n), StandardCharsets.UTF_8));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONArray boxes = o.getJSONArray(key);
                List<float[]> list = new ArrayList<>();
                for (int i = 0; i < boxes.length(); i++) {
                    JSONArray b = boxes.getJSONArray(i);
                    list.add(new float[]{(float) b.getDouble(0), (float) b.getDouble(1), (float) b.getDouble(2), (float) b.getDouble(3)});
                }
                known.put(key, list);
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Ignoring unreadable photo_faces.json", e);
        }
    }
}
