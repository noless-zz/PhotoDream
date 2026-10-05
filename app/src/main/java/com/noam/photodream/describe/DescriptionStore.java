package com.noam.photodream.describe;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The {@link DescriptionCache} on disk: files/descriptions.json, shared by the job, settings and the alarm challenge. */
public final class DescriptionStore {

    private static final String TAG = "DescriptionStore";
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static DescriptionStore instance;

    private final File file;
    private final DescriptionCache cache;

    public static synchronized DescriptionStore get(Context context) {
        if (instance == null) instance = new DescriptionStore(context.getApplicationContext());
        return instance;
    }

    private DescriptionStore(Context context) {
        file = new File(context.getFilesDir(), "descriptions.json");
        cache = load();
    }

    public DescriptionCache cache() { return cache; }

    public void save() {
        final String json;
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Map<String, Object>> e : cache.toMap().entrySet()) {
                JSONObject d = new JSONObject();
                for (Map.Entry<String, Object> f : e.getValue().entrySet()) {
                    Object v = f.getValue();
                    d.put(f.getKey(), v instanceof List ? new JSONArray((List<?>) v) : v);
                }
                o.put(e.getKey(), d);
            }
            json = o.toString();
        } catch (JSONException e) {
            Log.w(TAG, "Could not build descriptions JSON", e);
            return;
        }
        WRITER.execute(() -> {
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "Could not save descriptions", e);
                return;
            }
            if (!tmp.renameTo(file)) Log.w(TAG, "Could not replace " + file);
        });
    }

    private DescriptionCache load() {
        if (!file.isFile()) return new DescriptionCache();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            JSONObject o = new JSONObject(new String(bytes, 0, Math.max(0, n), StandardCharsets.UTF_8));
            Map<String, Map<String, Object>> saved = new HashMap<>();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject d = o.getJSONObject(key);
                Map<String, Object> m = new HashMap<>();
                for (Iterator<String> fi = d.keys(); fi.hasNext(); ) {
                    String f = fi.next();
                    Object v = d.get(f);
                    if (v instanceof JSONArray) {
                        List<Object> list = new ArrayList<>();
                        for (int i = 0; i < ((JSONArray) v).length(); i++) list.add(((JSONArray) v).get(i));
                        v = list;
                    }
                    m.put(f, v);
                }
                saved.put(key, m);
            }
            return DescriptionCache.fromMap(saved);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Ignoring unreadable descriptions.json", e);
            return new DescriptionCache();
        }
    }
}
