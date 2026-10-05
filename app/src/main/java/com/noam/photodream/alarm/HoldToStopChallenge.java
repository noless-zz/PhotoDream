package com.noam.photodream.alarm;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.noam.photodream.Photo;
import com.noam.photodream.R;

import java.util.List;

/**
 * The built-in fallback: press and hold for 3 seconds. Used when there are too few photos for the
 * chosen challenge, so an alarm can always be stopped.
 */
public class HoldToStopChallenge implements AlarmChallenge {

    private static final long HOLD_MS = 3000;

    private ValueAnimator animator;
    private ProgressBar bar;
    private Listener listener;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View createView(Context context, List<Photo> photos, Alarm.Difficulty difficulty, Listener l) {
        listener = l;
        FrameLayout root = new FrameLayout(context);
        float dp = context.getResources().getDisplayMetrics().density;

        bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Math.round(12 * dp), Gravity.BOTTOM);
        barLp.setMargins(Math.round(16 * dp), 0, Math.round(16 * dp), Math.round(8 * dp));
        root.addView(bar, barLp);

        TextView button = new TextView(context);
        button.setText(R.string.alarm_hold_to_stop);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(22);
        button.setTextColor(0xFF000000);
        button.setBackgroundResource(R.drawable.bg_hold_button);
        button.setContentDescription(context.getString(R.string.alarm_hold_to_stop));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(220 * dp), Math.round(220 * dp), Gravity.CENTER);
        root.addView(button, lp);

        button.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    beginHold();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    resetHold();
                    return true;
                default:
                    return true;
            }
        });
        return root;
    }

    private void beginHold() {
        resetHold();
        animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(HOLD_MS);
        animator.addUpdateListener(a -> {
            int pct = (int) a.getAnimatedValue();
            bar.setProgress(pct);
            listener.onProgress(pct, 100);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!cancelled) listener.onSolved();
            }
        });
        animator.start();
    }

    private void resetHold() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (bar != null) bar.setProgress(0);
    }

    @Override public void start() { }

    @Override public void stop() { resetHold(); }
}
