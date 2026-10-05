package com.noam.photodream.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Saves the alarms as JSON in their own SharedPreferences file. Call from the main thread
 * (the data is tiny, so reading it is instant).
 *
 * Change listeners are for screens that show the list; {@link AlarmScheduler} is notified
 * separately because a change must always re-arm the phone's alarm manager.
 */
public class AlarmStore {

    private static final String TAG = "AlarmStore";
    private static final String FILE = "alarms";
    private static final String KEY_ALARMS = "alarms_json";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_SNOOZES = "snoozes_json";

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private final Context context;
    private final SharedPreferences sp;

    public AlarmStore(Context context) {
        this.context = context.getApplicationContext();
        sp = this.context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void addListener(Runnable r) { LISTENERS.add(r); }
    public static void removeListener(Runnable r) { LISTENERS.remove(r); }

    /** All alarms, sorted by time of day. */
    public List<Alarm> list() {
        List<Alarm> out = new ArrayList<>();
        String json = sp.getString(KEY_ALARMS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) out.add(Alarm.fromMap(toMap(arr.getJSONObject(i))));
            } catch (JSONException e) {
                Log.w(TAG, "Could not read alarms", e);
            }
        }
        Collections.sort(out, (a, b) -> a.hour != b.hour ? Integer.compare(a.hour, b.hour)
                : a.minute != b.minute ? Integer.compare(a.minute, b.minute) : Long.compare(a.id, b.id));
        return out;
    }

    public Alarm get(long id) {
        for (Alarm a : list()) if (a.id == id) return a;
        return null;
    }

    /** Inserts a new alarm (id 0) or replaces the one with the same id. Returns the saved alarm's id. */
    public long save(Alarm alarm) {
        List<Alarm> all = list();
        if (alarm.id == 0) {
            alarm.id = sp.getLong(KEY_NEXT_ID, 1);
            sp.edit().putLong(KEY_NEXT_ID, alarm.id + 1).apply();
            all.add(alarm);
        } else {
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id == alarm.id) {
                    all.set(i, alarm);
                    found = true;
                }
            }
            if (!found) all.add(alarm);
        }
        write(all);
        return alarm.id;
    }

    public void delete(long id) {
        List<Alarm> all = list();
        all.removeIf(a -> a.id == id);
        write(all);
        clearSnooze(id);
    }

    // ---------------------------------------------------------------- snoozes

    /** Alarm id → time (epoch millis) when its snooze ends. */
    public Map<Long, Long> snoozes() {
        Map<Long, Long> out = new HashMap<>();
        String json = sp.getString(KEY_SNOOZES, null);
        if (json == null) return out;
        try {
            JSONObject o = new JSONObject(json);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String key = it.next();
                out.put(Long.parseLong(key), o.getLong(key));
            }
        } catch (JSONException | NumberFormatException e) {
            Log.w(TAG, "Could not read snoozes", e);
        }
        return out;
    }

    public void setSnooze(long alarmId, long endMillis) {
        Map<Long, Long> m = snoozes();
        m.put(alarmId, endMillis);
        writeSnoozes(m);
    }

    public void clearSnooze(long alarmId) {
        Map<Long, Long> m = snoozes();
        if (m.remove(alarmId) != null) writeSnoozes(m);
    }

    private void writeSnoozes(Map<Long, Long> m) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<Long, Long> e : m.entrySet()) o.put(String.valueOf(e.getKey()), e.getValue());
        } catch (JSONException e) {
            Log.w(TAG, "Could not write snoozes", e);
        }
        sp.edit().putString(KEY_SNOOZES, o.toString()).apply();
        notifyChanged();
    }

    // ---------------------------------------------------------------- internals

    private void write(List<Alarm> all) {
        JSONArray arr = new JSONArray();
        for (Alarm a : all) arr.put(new JSONObject(a.toMap()));
        sp.edit().putString(KEY_ALARMS, arr.toString()).apply();
        notifyChanged();
    }

    private void notifyChanged() {
        AlarmScheduler.rescheduleAll(context);       // every change re-arms the phone's alarm manager
        for (Runnable r : LISTENERS) r.run();
    }

    private static Map<String, Object> toMap(JSONObject o) throws JSONException {
        Map<String, Object> m = new HashMap<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            m.put(k, o.get(k));
        }
        return m;
    }
}
