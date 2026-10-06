package com.noam.photodream;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of phone folders (as tree-Uri strings) the user picked. Pure Java so the
 * rules – ignore duplicates, migrate the old single-folder setting – are unit-tested.
 * {@link Prefs} stores the result as a JSON array.
 */
public final class FolderList {

    private FolderList() { }

    /**
     * The folders to use. If the new list was never saved ({@code stored == null}) but the
     * old single-folder setting exists, that folder becomes the first entry.
     */
    public static List<String> migrate(List<String> stored, String legacySingle) {
        List<String> out = new ArrayList<>();
        if (stored != null) {
            for (String s : stored) add(out, s);
        } else if (legacySingle != null) {
            add(out, legacySingle);
        }
        return out;
    }

    /** Adds the folder unless it is empty or already there. Returns true if the list changed. */
    public static boolean add(List<String> folders, String uri) {
        if (uri == null || uri.isEmpty() || folders.contains(uri)) return false;
        folders.add(uri);
        return true;
    }

    /** Removes the folder. Returns true if it was in the list. */
    public static boolean remove(List<String> folders, String uri) {
        return folders.remove(uri);
    }
}
