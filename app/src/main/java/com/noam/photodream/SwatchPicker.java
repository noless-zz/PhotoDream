package com.noam.photodream;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A row of round color swatches (see {@link FrameColors}); the chosen one shows a check mark.
 * Scrolls sideways on narrow phones. Each swatch is a 48dp touch target with a
 * content description ("Blue, selected") for TalkBack.
 */
public class SwatchPicker extends HorizontalScrollView {

    public interface OnPick { void onPick(String colorId); }

    /** Spoken names, in the same order as {@link FrameColors#ids()}. */
    private static final int[] NAMES = {
            R.string.frame_white, R.string.frame_cream, R.string.frame_black, R.string.frame_blue,
            R.string.frame_green, R.string.frame_amber, R.string.frame_coral, R.string.frame_purple,
            R.string.frame_teal};

    private final LinearLayout row;
    private String selected = FrameColors.DEFAULT;
    private OnPick onPick;

    public SwatchPicker(Context context) { this(context, null); }

    public SwatchPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        float dp = getResources().getDisplayMetrics().density;
        String[] ids = FrameColors.ids();
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            TextView sw = new TextView(context);
            sw.setLayoutParams(new LinearLayout.LayoutParams(Math.round(48 * dp), Math.round(48 * dp)));
            sw.setGravity(Gravity.CENTER);
            sw.setTextSize(18);
            sw.setTag(id);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(FrameColors.argb(id));
            oval.setStroke(Math.round(1.5f * dp), 0x66808080);   // light ring so white/black stay visible on any theme
            sw.setBackground(new InsetDrawable(oval, Math.round(5 * dp)));
            sw.setOnClickListener(v -> {
                setSelected(id);
                if (onPick != null) onPick.onPick(id);
            });
            row.addView(sw);
        }
        refresh();
    }

    public void setOnPick(OnPick listener) { onPick = listener; }

    /** Show this color as chosen (does not call the listener). */
    public void setSelected(String colorId) {
        selected = FrameColors.normalize(colorId);
        refresh();
    }

    private void refresh() {
        String[] ids = FrameColors.ids();
        for (int i = 0; i < ids.length; i++) {
            TextView sw = (TextView) row.getChildAt(i);
            boolean on = ids[i].equals(selected);
            // the check is a drawn glyph, not text to translate; pick a color that contrasts with the swatch
            sw.setText(on ? "\u2713" : "");
            int c = FrameColors.argb(ids[i]);
            double luma = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c);
            sw.setTextColor(luma > 150 ? Color.BLACK : Color.WHITE);
            String name = getContext().getString(NAMES[i]);
            sw.setContentDescription(on ? getContext().getString(R.string.swatch_selected, name) : name);
            sw.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }
}
