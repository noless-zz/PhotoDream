package com.noam.photodream;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** All user settings in one place (stored in SharedPreferences). */
public class Prefs {

    private static final String FILE = "photodream";
    private static final String KEY_FOLDER = "local_folder_uri";
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

    public Uri getLocalFolder() {
        String s = sp.getString(KEY_FOLDER, null);
        return s == null ? null : Uri.parse(s);
    }

    public void setLocalFolder(Uri uri) {
        sp.edit().putString(KEY_FOLDER, uri == null ? null : uri.toString()).apply();
    }

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
