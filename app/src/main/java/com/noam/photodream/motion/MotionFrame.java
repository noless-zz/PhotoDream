package com.noam.photodream.motion;

/**
 * What every pattern needs to know about the current frame: the table size, how many places
 * the pattern should lay out, and the time.
 *
 * {@code count} is the table's maximum number of cards, not how many are on it right now, so
 * the layout does not change shape while the table fills up.
 */
public final class MotionFrame {
    public float width, height;
    public int count;
    /** Seconds since the pattern started. Only moves forward. */
    public double time;

    public MotionFrame set(float width, float height, int count, double time) {
        this.width = width;
        this.height = height;
        this.count = Math.max(1, count);
        this.time = time;
        return this;
    }

    public float centerX() { return width / 2f; }
    public float centerY() { return height / 2f; }
    public float shortSide() { return Math.min(width, height); }
}
