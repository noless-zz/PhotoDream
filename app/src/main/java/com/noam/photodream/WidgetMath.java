package com.noam.photodream;

/**
 * Size rules for the home-screen widget. A widget's picture travels to the launcher inside a
 * {@code RemoteViews}, and Android refuses bitmaps larger than about 1.5 screens of pixels.
 * Pure Java, unit-tested.
 */
public final class WidgetMath {

    private WidgetMath() { }

    /** Most bytes of bitmap memory we allow in one widget update: 1.5 × the screen at 4 bytes per pixel, minus a margin. */
    public static long maxBitmapBytes(int screenW, int screenH) {
        return (long) (screenW * (long) screenH * 4L * 1.5 * 0.8);
    }

    /**
     * The pixel size to render at: the widget's own size, scaled down (same shape) if it would
     * exceed {@code maxBytes}. Never smaller than 1×1.
     * @return {width, height}
     */
    public static int[] fit(int widthPx, int heightPx, long maxBytes) {
        int w = Math.max(1, widthPx), h = Math.max(1, heightPx);
        long bytes = (long) w * h * 4L;
        if (bytes <= maxBytes) return new int[]{w, h};
        double scale = Math.sqrt(maxBytes / (double) bytes);
        return new int[]{Math.max(1, (int) Math.floor(w * scale)), Math.max(1, (int) Math.floor(h * scale))};
    }

    /** dp → px for a widget dimension the launcher reports in dp. */
    public static int dpToPx(int dp, float density) {
        return Math.round(dp * density);
    }
}
