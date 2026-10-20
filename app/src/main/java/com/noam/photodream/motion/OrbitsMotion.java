package com.noam.photodream.motion;

/**
 * Every photo traces its own Lissajous figure: x = sin(a·t + δ), y = sin(b·t).
 *
 * The ratio a:b decides the shape – 1:2 is a figure eight, 3:2 a pretzel, 5:4 a woven knot.
 * Each card picks a ratio and a starting offset from its id, so the table becomes a calm
 * tangle of different loops. Because the ratios are whole numbers, every path closes on itself
 * and repeats.
 */
public final class OrbitsMotion implements MotionPattern {

    static final int[][] RATIOS = {{1, 2}, {3, 2}, {5, 4}, {4, 3}, {2, 3}, {5, 3}};
    static final double BASE_SECONDS = 80;     // a 1:1 loop would take this long
    static final float REACH = 0.36f;

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        int[] ab = RATIOS[(int) Math.floorMod(id, (long) RATIOS.length)];
        double w = 2 * Math.PI / BASE_SECONDS;
        double delta = id * SunflowerMotion.GOLDEN_ANGLE;
        double sx = Math.sin(ab[0] * w * f.time + delta);
        double sy = Math.sin(ab[1] * w * f.time + delta * 0.5);
        // where along a hidden third axis the card is, for a gentle 3D feel
        float depth = (float) Math.cos(ab[0] * w * f.time + delta);

        out.set(f.centerX() + REACH * f.width * (float) sx,
                f.centerY() + REACH * f.height * (float) sy,
                (float) (6 * sy),
                0.85f + 0.15f * depth,
                1f,
                depth);
    }
}
