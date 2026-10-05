package com.noam.photodream.alarm.challenge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * A photo card that lies face down (PhotoDream pattern on the back) and flips in 3D to show the photo.
 * Same "tilted print" look as the photo table (white border, shadow). Shared by the
 * "Flip them all" and "Memory pairs" challenges.
 *
 * The flip is two quarter turns around the vertical axis: 0° → 90° (edge-on), swap the faces,
 * then -90° → 0°. {@code cameraDistance} keeps the perspective natural.
 */
public class FlipCardView extends FrameLayout {

    private static final long HALF_FLIP_MS = 160;

    private final ImageView front;
    private final View back;
    private boolean faceUp;

    public FlipCardView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        setCameraDistance(8000 * density);        // without this the card looks huge while it turns
        setElevation(6 * density);
        setOutlineProvider(ViewOutlineProvider.BOUNDS);

        front = new ImageView(context);
        front.setScaleType(ImageView.ScaleType.CENTER_CROP);
        front.setBackgroundColor(Color.WHITE);
        front.setCropToPadding(true);
        addView(front, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        back = new BackView(context);
        addView(back, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        applyFaces();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int pad = Math.max(4, Math.round(w * 0.04f));
        front.setPadding(pad, pad, pad, pad);
    }

    public void setPhoto(Bitmap bitmap) { front.setImageBitmap(bitmap); }

    public boolean isFaceUp() { return faceUp; }

    /** Jump to a face without animation. */
    public void setFaceUp(boolean up) {
        animate().cancel();
        setRotationY(0f);
        faceUp = up;
        applyFaces();
    }

    /** Animated flip; does nothing if the card is already in that state. */
    public void flipTo(boolean up) {
        if (faceUp == up) return;
        faceUp = up;
        animate().cancel();
        animate().rotationY(90f).setDuration(HALF_FLIP_MS).withEndAction(() -> {
            applyFaces();
            setRotationY(-90f);
            animate().rotationY(0f).setDuration(HALF_FLIP_MS).start();
        }).start();
    }

    public void cancelAnimations() { animate().cancel(); }

    private void applyFaces() {
        front.setVisibility(faceUp ? VISIBLE : INVISIBLE);
        back.setVisibility(faceUp ? INVISIBLE : VISIBLE);
    }

    /** The card back: white print border, navy field with a small diamond pattern. */
    private static final class BackView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path diamond = new Path();

        BackView(Context c) {
            super(c);
            fill.setColor(0xFF1F2A44);
            line.setColor(0xFFE0A458);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawColor(Color.WHITE);
            float pad = Math.max(4f, w * 0.04f);
            canvas.drawRect(pad, pad, w - pad, h - pad, fill);
            float step = Math.max(16f, w / 5f);
            for (float cy = pad + step / 2; cy < h - pad; cy += step) {
                for (float cx = pad + step / 2; cx < w - pad; cx += step) {
                    float r = step * 0.3f;
                    diamond.reset();
                    diamond.moveTo(cx, cy - r);
                    diamond.lineTo(cx + r, cy);
                    diamond.lineTo(cx, cy + r);
                    diamond.lineTo(cx - r, cy);
                    diamond.close();
                    canvas.drawPath(diamond, line);
                }
            }
        }
    }
}
