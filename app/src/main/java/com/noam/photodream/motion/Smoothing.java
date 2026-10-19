package com.noam.photodream.motion;

/**
 * Eases a value toward a target, frame by frame, at the same speed whatever the frame rate.
 *
 * Each frame the value covers a fixed share of the remaining distance. Using
 * {@code 1 - e^(-dt/tau)} as that share means 60 small steps per second and 30 bigger ones end
 * up in the same place: after {@code tau} seconds about 63% of the way is covered.
 */
public final class Smoothing {

    private Smoothing() { }

    /** Share of the remaining distance to cover in a frame that lasted {@code dtSec}. */
    public static float factor(double dtSec, double tauSec) {
        if (dtSec <= 0) return 0f;
        if (tauSec <= 0) return 1f;
        return (float) (1.0 - Math.exp(-dtSec / tauSec));
    }

    public static float step(float current, float target, float factor) {
        return current + (target - current) * factor;
    }
}
