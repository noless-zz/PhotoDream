package com.noam.photodream;

import java.util.List;

/**
 * "Where do I crop so nobody loses their head?" Pure Java, unit-tested.
 * Face boxes are fractions of the image: {left, top, right, bottom}, each 0..1.
 */
public final class CropMath {

    private CropMath() { }

    /**
     * The biggest rectangle with the view's shape that fits inside the image, placed so that all the
     * faces are inside it – when they fit at all; otherwise centred on the group of faces.
     * With no faces it is the plain centre crop.
     *
     * @param viewAspect view width / view height
     * @return {left, top, width, height} in image pixels
     */
    public static float[] bestCrop(int imgW, int imgH, List<float[]> faces, float viewAspect) {
        float w, h;
        if (imgW / (float) imgH > viewAspect) {        // image is wider than the view: use full height
            h = imgH;
            w = h * viewAspect;
        } else {                                       // image is taller: use full width
            w = imgW;
            h = w / viewAspect;
        }
        float[] focus = focusPoint(faces);
        float cx = focus == null ? 0.5f : focus[0];
        float cy = focus == null ? 0.5f : focus[1];
        float left = clamp(cx * imgW - w / 2f, 0f, imgW - w);
        float top = clamp(cy * imgH - h / 2f, 0f, imgH - h);
        return new float[]{left, top, w, h};
    }

    /** Centre of the box that contains every face as fractions {x, y}, or null if there are no faces. */
    public static float[] focusPoint(List<float[]> faces) {
        if (faces == null || faces.isEmpty()) return null;
        float l = 1f, t = 1f, r = 0f, b = 0f;
        for (float[] f : faces) {
            l = Math.min(l, f[0]);
            t = Math.min(t, f[1]);
            r = Math.max(r, f[2]);
            b = Math.max(b, f[3]);
        }
        return new float[]{(l + r) / 2f, (t + b) / 2f};
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
