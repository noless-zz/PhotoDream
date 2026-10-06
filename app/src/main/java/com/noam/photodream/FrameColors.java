package com.noam.photodream;

/**
 * The colors a source's photo frame (the "print" border on the photo table) can have.
 * Pure Java: colors are plain ARGB ints, so this is unit-tested without Android.
 * The id is what we store in {@link Prefs}.
 */
public final class FrameColors {

    public static final String DEFAULT = "white";

    private static final String[] IDS = {"white", "cream", "black", "blue", "green", "amber", "coral", "purple", "teal"};
    private static final int[] ARGB = {
            0xFFFFFFFF, 0xFFF5EBD0, 0xFF111111,
            0xFF2F6FDE, 0xFF2E9E5B, 0xFFF2A60C, 0xFFF0644E, 0xFF8750C8, 0xFF1AA6A6};

    private FrameColors() { }

    /** All ids in display order (first one is the default). */
    public static String[] ids() { return IDS.clone(); }

    /** True for an id we know. */
    public static boolean isKnown(String id) { return indexOf(id) >= 0; }

    /** Unknown or missing ids become the default, so old/odd stored values never crash. */
    public static String normalize(String id) { return isKnown(id) ? id : DEFAULT; }

    public static int argb(String id) { return ARGB[Math.max(0, indexOf(id))]; }

    /** Index of the id in {@link #ids()}, or -1. */
    public static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }
}
