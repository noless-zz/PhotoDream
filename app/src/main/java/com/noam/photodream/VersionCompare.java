package com.noam.photodream;

/**
 * Compares version names like "0.4" or a release tag "v0.10". Pure Java, unit-tested.
 * Anything that isn't plain numbers separated by dots (a "dev-12" build, "0.4-beta") is unknown
 * and never counts as newer – we would rather stay silent than nag about the wrong thing.
 */
public final class VersionCompare {

    private VersionCompare() { }

    /** True if {@code latest} (e.g. a GitHub tag "v0.4") is a higher version than {@code current}. */
    public static boolean isNewer(String latest, String current) {
        int[] a = parse(latest), b = parse(current);
        if (a == null || b == null) return false;
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    /** "v0.4" → "0.4"; for showing the number to the user. */
    public static String display(String tag) {
        if (tag == null) return "";
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    /** "v1.12.3" → {1, 12, 3}; null if it is not a plain dotted number. */
    static int[] parse(String version) {
        if (version == null) return null;
        String s = display(version.trim());
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\.", -1);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("[0-9]{1,6}")) return null;
            out[i] = Integer.parseInt(parts[i]);
        }
        return out;
    }
}
