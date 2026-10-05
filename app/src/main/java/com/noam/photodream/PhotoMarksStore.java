package com.noam.photodream;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads and saves {@link PhotoMarks} in files/photo_marks.json. One shared instance per process,
 * so the focus buttons, the settings screen and the sync all see the same marks. Saving happens on a
 * background thread; the file is tiny.
 */
public final class PhotoMarksStore {

    private static final String TAG = "PhotoMarksStore";
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static PhotoMarksStore instance;

    private final File file;
    private final PhotoMarks marks;

    public static synchronized PhotoMarksStore get(Context context) {
        if (instance == null) instance = new PhotoMarksStore(context.getApplicationContext());
        return instance;
    }

    private PhotoMarksStore(Context context) {
        file = new File(context.getFilesDir(), "photo_marks.json");
        marks = load();
    }

    /** The live marks. Change them, then call {@link #save()}. */
    public PhotoMarks marks() { return marks; }

    public void save() {
        final String json;
        synchronized (marks) {
            JSONObject o = new JSONObject();
            try {
                o.put("favorites", new JSONArray(marks.favoriteKeys()));
                o.put("hidden", new JSONArray(marks.hiddenKeys()));
            } catch (JSONException e) {
                Log.w(TAG, "Could not build marks JSON", e);
                return;
            }
            json = o.toString();
        }
        WRITER.execute(() -> {
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "Could not save marks", e);
                return;
            }
            if (!tmp.renameTo(file)) Log.w(TAG, "Could not replace " + file);
        });
    }

    private PhotoMarks load() {
        if (!file.isFile()) return new PhotoMarks();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            JSONObject o = new JSONObject(new String(bytes, 0, Math.max(0, n), StandardCharsets.UTF_8));
            return new PhotoMarks(strings(o.optJSONArray("favorites")), strings(o.optJSONArray("hidden")));
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Ignoring unreadable photo_marks.json", e);
            return new PhotoMarks();
        }
    }

    private static List<String> strings(JSONArray a) throws JSONException {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        return out;
    }
}
