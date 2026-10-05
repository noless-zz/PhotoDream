package com.noam.photodream;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** All user settings in one place (stored in SharedPreferences). */
public class Prefs {

    private static final String FILE = "photodream";
    private static final String KEY_FOLDER = "local_folder_uri";          // old single-folder key
    private static final String KEY_FOLDERS = "local_folders";
    private static final String KEY_INTERVAL = "interval_sec";
    private static final String KEY_TRANSITION = "transition";
    private static final String KEY_SHUFFLE = "shuffle";
    private static final String KEY_CROP = "crop";
    private static final String KEY_CLOCK = "show_clock";
    private static final String KEY_DIM = "dim";
    private static final String KEY_DISPLAY_MODE = "display_mode";
    private static final String KEY_TABLE_ENTRY = "table_entry";
    private static final String KEY_TABLE_MAX_CARDS = "table_max_cards";
    private static final String KEY_TABLE_CARD_SIZE = "table_card_size";
    private static final String KEY_TABLE_ROTATION = "table_rotation";
    private static final String KEY_TABLE_DRIFT = "table_drift";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * The phone folders the user picked (all of them together are the "local" source).
     * Older versions stored one folder under {@code local_folder_uri}; it is moved into the list
     * the first time this is read.
     */
    public List<Uri> getLocalFolders() {
        List<String> stored = null;
        String json = sp.getString(KEY_FOLDERS, null);
        if (json != null) {
            stored = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) stored.add(a.getString(i));
            } catch (JSONException e) {
                stored.clear();
            }
        }
        List<String> list = FolderList.migrate(stored, sp.getString(KEY_FOLDER, null));
        if (json == null) {
            saveFolders(list);                       // migration done: from now on only the list counts
            sp.edit().remove(KEY_FOLDER).apply();
        }
        List<Uri> out = new ArrayList<>();
        for (String s : list) out.add(Uri.parse(s));
        return out;
    }

    /** Adds a folder; returns false if it was already in the list. */
    public boolean addLocalFolder(Uri uri) {
        List<String> list = toStrings(getLocalFolders());
        boolean added = FolderList.add(list, uri.toString());
        if (added) saveFolders(list);
        return added;
    }

    public void removeLocalFolder(Uri uri) {
        List<String> list = toStrings(getLocalFolders());
        if (FolderList.remove(list, uri.toString())) saveFolders(list);
    }

    private static List<String> toStrings(List<Uri> uris) {
        List<String> out = new ArrayList<>();
        for (Uri u : uris) out.add(u.toString());
        return out;
    }

    private void saveFolders(List<String> list) {
        sp.edit().putString(KEY_FOLDERS, new JSONArray(list).toString()).apply();
    }

    /**
     * Whether a source ("local", "onedrive", "gdrive") is part of the slideshow.
     * Hiding a cloud only leaves it out of the show; it keeps syncing in the background.
     */
    public boolean isSourceEnabled(String sourceId) { return sp.getBoolean("source_enabled_" + sourceId, true); }
    public void setSourceEnabled(String sourceId, boolean on) { sp.edit().putBoolean("source_enabled_" + sourceId, on).apply(); }

    /** Frame color id (see {@link FrameColors}) for a source's cards; classic white by default. */
    public String getFrameColor(String sourceId) {
        return FrameColors.normalize(sp.getString("frame_color_" + sourceId, FrameColors.DEFAULT));
    }
    public void setFrameColor(String sourceId, String colorId) {
        sp.edit().putString("frame_color_" + sourceId, FrameColors.normalize(colorId)).apply();
    }

    /** One-photo mode: show a thin strip in the source's color along the bottom edge. */
    public boolean isSourceStrip() { return sp.getBoolean("source_strip", false); }
    public void setSourceStrip(boolean on) { sp.edit().putBoolean("source_strip", on).apply(); }

    /** Seconds each photo stays on screen (5..60). */
    public int getIntervalSeconds() { return sp.getInt(KEY_INTERVAL, 10); }
    public void setIntervalSeconds(int s) { sp.edit().putInt(KEY_INTERVAL, s).apply(); }

    public SlideshowView.Transition getTransition() {
        try {
            return SlideshowView.Transition.valueOf(
                    sp.getString(KEY_TRANSITION, SlideshowView.Transition.SLIDE.name()));
        } catch (IllegalArgumentException e) {
            return SlideshowView.Transition.SLIDE;
        }
    }
    public void setTransition(SlideshowView.Transition t) { sp.edit().putString(KEY_TRANSITION, t.name()).apply(); }

    public boolean isShuffle() { return sp.getBoolean(KEY_SHUFFLE, true); }
    public void setShuffle(boolean b) { sp.edit().putBoolean(KEY_SHUFFLE, b).apply(); }

    public boolean isCrop() { return sp.getBoolean(KEY_CROP, true); }
    public void setCrop(boolean b) { sp.edit().putBoolean(KEY_CROP, b).apply(); }

    public boolean isShowClock() { return sp.getBoolean(KEY_CLOCK, true); }
    public void setShowClock(boolean b) { sp.edit().putBoolean(KEY_CLOCK, b).apply(); }

    public boolean isDim() { return sp.getBoolean(KEY_DIM, false); }
    public void setDim(boolean b) { sp.edit().putBoolean(KEY_DIM, b).apply(); }

    public enum DisplayMode { SINGLE, TABLE }

    public DisplayMode getDisplayMode() {
        try {
            return DisplayMode.valueOf(sp.getString(KEY_DISPLAY_MODE, "SINGLE"));
        } catch (Exception e) {
            return DisplayMode.SINGLE;
        }
    }
    public void setDisplayMode(DisplayMode m) { sp.edit().putString(KEY_DISPLAY_MODE, m.name()).apply(); }

    public enum Entry { DROP, FLY_IN, POP, FADE, RANDOM }

    public Entry getTableEntry() {
        try {
            return Entry.valueOf(sp.getString(KEY_TABLE_ENTRY, "RANDOM"));
        } catch (Exception e) {
            return Entry.RANDOM;
        }
    }
    public void setTableEntry(Entry e) { sp.edit().putString(KEY_TABLE_ENTRY, e.name()).apply(); }

    public int getTableMaxCards() { return sp.getInt(KEY_TABLE_MAX_CARDS, 8); }
    public void setTableMaxCards(int i) { sp.edit().putInt(KEY_TABLE_MAX_CARDS, i).apply(); }

    public int getTableCardSize() { return sp.getInt(KEY_TABLE_CARD_SIZE, 50); }
    public void setTableCardSize(int i) { sp.edit().putInt(KEY_TABLE_CARD_SIZE, i).apply(); }

    public int getTableRotation() { return sp.getInt(KEY_TABLE_ROTATION, 12); }
    public void setTableRotation(int i) { sp.edit().putInt(KEY_TABLE_ROTATION, i).apply(); }

    public boolean isTableDrift() { return sp.getBoolean(KEY_TABLE_DRIFT, true); }
    public void setTableDrift(boolean b) { sp.edit().putBoolean(KEY_TABLE_DRIFT, b).apply(); }
}
