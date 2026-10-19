package com.noam.photodream.motion;

/**
 * Photos along a golden spiral, zooming outward one step with every new photo.
 *
 * A golden spiral grows by the golden ratio φ ≈ 1.618 every quarter turn – it is the curve
 * through the corners of the Fibonacci squares 1, 1, 2, 3, 5, 8… So place n sits a quarter
 * turn further round than place n+1 and φ times further out. The newest photo is born, small,
 * in the eye of the spiral; when the next one arrives every photo moves one place out, turning
 * 90° and growing, which looks like zooming into a nautilus shell.
 *
 * Sizes grow by √φ per place rather than φ, so the inner photos stay big enough to see.
 */
public final class FibonacciMotion implements MotionPattern {

    public static final double PHI = (1 + Math.sqrt(5)) / 2;

    static final float REACH = 0.42f;            // the outermost place, as a share of width/height
    static final double TURN_SECONDS = 150;      // the whole shell turns slowly too
    static final float MIN_SCALE = 0.3f;

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        int n = Math.min(rank, f.count - 1);
        int in = f.count - 1 - n;                // places in from the outermost (0 for the oldest)

        double r = Math.pow(PHI, -in);           // 1, 1/φ, 1/φ², …
        double theta = -in * Math.PI / 2 + 2 * Math.PI * f.time / TURN_SECONDS;
        float scale = (float) Math.max(MIN_SCALE, 1.15 * Math.pow(PHI, -in / 2.0));

        out.set(f.centerX() + (float) (REACH * f.width * r * Math.cos(theta)),
                f.centerY() + (float) (REACH * f.height * r * Math.sin(theta)),
                (float) (8 * Math.sin(theta)),
                scale,
                1f,
                f.count > 1 ? 2f * in / (f.count - 1) - 1f : 0f);   // the small inner ones lie on top
    }
}
