package com.noam.photodream;

import android.graphics.Matrix;
import android.graphics.RectF;

import java.util.List;

/**
 * Builds the image matrix for a photo on screen: "fill" crops with {@link CropMath} so faces stay
 * inside; "fit" is the normal letterboxed centre. Also maps the faces' centre into view coordinates
 * (the zoom point for Ken Burns).
 */
final class FaceCrop {

    private FaceCrop() { }

    /** Matrix that draws the whole bitmap (fit) or the face-aware crop of it (fill) into the view. */
    static Matrix matrix(int bmpW, int bmpH, int viewW, int viewH, boolean fill, List<float[]> faces) {
        Matrix m = new Matrix();
        RectF dst = new RectF(0, 0, viewW, viewH);
        if (fill) {
            float[] c = CropMath.bestCrop(bmpW, bmpH, faces, viewW / (float) viewH);
            m.setRectToRect(new RectF(c[0], c[1], c[0] + c[2], c[1] + c[3]), dst, Matrix.ScaleToFit.FILL);
        } else {
            m.setRectToRect(new RectF(0, 0, bmpW, bmpH), dst, Matrix.ScaleToFit.CENTER);
        }
        return m;
    }

    /** Where the faces' centre ends up in the view, or null if there are no faces. */
    static float[] focusInView(int bmpW, int bmpH, Matrix m, List<float[]> faces) {
        float[] focus = CropMath.focusPoint(faces);
        if (focus == null) return null;
        float[] pt = {focus[0] * bmpW, focus[1] * bmpH};
        m.mapPoints(pt);
        return pt;
    }
}
