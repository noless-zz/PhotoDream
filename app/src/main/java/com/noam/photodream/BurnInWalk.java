package com.noam.photodream;

import java.util.Random;

/**
 * The slow random walk that keeps an OLED clock from burning in: every minute the clock moves a few
 * dp, but never farther than {@code maxOffset} from its home corner. Pure Java, unit-tested.
 */
public final class BurnInWalk {

    private final float maxOffset;     // the clock stays inside a square of ±maxOffset around home
    private final float maxStep;       // largest move in one minute, per axis
    private final Random random;
    private float x, y;

    public BurnInWalk(float maxOffset, float maxStep, Random random) {
        this.maxOffset = maxOffset;
        this.maxStep = maxStep;
        this.random = random;
    }

    public float x() { return x; }

    public float y() { return y; }

    /** Take one step. A step is never zero on both axes, so the clock really moves. */
    public void step() {
        float nx, ny;
        int guard = 0;
        do {
            nx = clamp(x + (random.nextFloat() * 2f - 1f) * maxStep);
            ny = clamp(y + (random.nextFloat() * 2f - 1f) * maxStep);
        } while (nx == x && ny == y && ++guard < 10);
        x = nx;
        y = ny;
    }

    private float clamp(float v) { return Math.max(-maxOffset, Math.min(maxOffset, v)); }
}
