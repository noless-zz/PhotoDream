package com.noam.photodream.motion;

/**
 * Hyperspace: photos come out of a vanishing point in the middle and fly toward you, growing
 * and spreading out, then fade and start again far away.
 *
 * Each card is at its own point of a repeating trip, {@code progress} 0 (far) → 1 (passing
 * you). Cards are spread over the trip by the golden ratio – card id × φ, keeping only the
 * part after the decimal point – which scatters any number of cards evenly, so they never
 * bunch up. Speed and size follow perspective: things close to you look bigger and move faster.
 */
public final class WarpMotion implements MotionPattern {

    static final double TRIP_SECONDS = 16;     // from the vanishing point to past the viewer
    static final float REACH = 0.44f;

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        double progress = frac(f.time / TRIP_SECONDS + id * FibonacciMotion.PHI);
        double theta = id * SunflowerMotion.GOLDEN_ANGLE;              // each card's own direction
        double reach = progress * progress;                            // slow far away, fast close up
        // fade in just after the start and out just before the end, so the jump back is invisible
        float alpha = (float) Math.min(1, Math.min(progress / 0.12, (1 - progress) / 0.1));

        out.set(f.centerX() + (float) (REACH * f.width * reach * Math.cos(theta)),
                f.centerY() + (float) (REACH * f.height * reach * Math.sin(theta)),
                0f,
                (float) (0.3 + 0.95 * progress * progress),
                Math.max(0f, alpha),
                (float) (2 * progress - 1));                           // the nearest is on top
    }

    static double frac(double v) {
        return v - Math.floor(v);
    }
}
