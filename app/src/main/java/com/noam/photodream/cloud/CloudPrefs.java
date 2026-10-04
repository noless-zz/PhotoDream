package com.noam.photodream.cloud;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings of one cloud source (folder, limits, last sync). One prefs file per provider. */
public class CloudPrefs {


    private static final String KEY_FOLDER_ID = "folder_id";
    private static final String KEY_FOLDER_PATH = "folder_path";
    private static final String KEY_SUBFOLDERS = "subfolders";
    private static final String KEY_MAX_PHOTOS = "max_photos";
    private static final String KEY_WIFI_CHARGING = "wifi_charging_only";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_LAST_SYNC_MSG = "last_sync_msg";

    private final SharedPreferences sp;

    public CloudPrefs(Context context, CloudProvider provider) {
        this(context, provider.id());
    }

    public CloudPrefs(Context context, String providerId) {
        sp = context.getApplicationContext().getSharedPreferences(providerId, Context.MODE_PRIVATE);
    }

    /** OneDrive item id of the chosen folder; null = none chosen. */
    public String getFolderId() { return sp.getString(KEY_FOLDER_ID, null); }
    public String getFolderPath() { return sp.getString(KEY_FOLDER_PATH, ""); }
    public void setFolder(String id, String path) {
        sp.edit().putString(KEY_FOLDER_ID, id).putString(KEY_FOLDER_PATH, path).apply();
    }

    public boolean isIncludeSubfolders() { return sp.getBoolean(KEY_SUBFOLDERS, true); }
    public void setIncludeSubfolders(boolean b) { sp.edit().putBoolean(KEY_SUBFOLDERS, b).apply(); }

    /** How many photos to keep on the phone (50..1000). */
    public int getMaxPhotos() { return sp.getInt(KEY_MAX_PHOTOS, 200); }
    public void setMaxPhotos(int n) { sp.edit().putInt(KEY_MAX_PHOTOS, n).apply(); }

    /** Background sync only on Wi-Fi while charging. */
    public boolean isWifiChargingOnly() { return sp.getBoolean(KEY_WIFI_CHARGING, true); }
    public void setWifiChargingOnly(boolean b) { sp.edit().putBoolean(KEY_WIFI_CHARGING, b).apply(); }

    public long getLastSyncTime() { return sp.getLong(KEY_LAST_SYNC_TIME, 0); }
    public String getLastSyncMessage() { return sp.getString(KEY_LAST_SYNC_MSG, ""); }
    public void setLastSync(long time, String message) {
        sp.edit().putLong(KEY_LAST_SYNC_TIME, time).putString(KEY_LAST_SYNC_MSG, message).apply();
    }

    /** Forget folder and sync status (on disconnect). */
    public void clear() {
        boolean sub = isIncludeSubfolders();
        int max = getMaxPhotos();
        boolean wifi = isWifiChargingOnly();
        sp.edit().clear()
                .putBoolean(KEY_SUBFOLDERS, sub).putInt(KEY_MAX_PHOTOS, max)
                .putBoolean(KEY_WIFI_CHARGING, wifi).apply();
    }
}
