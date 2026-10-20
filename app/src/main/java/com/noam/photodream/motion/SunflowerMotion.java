package com.noam.photodream.motion;

/**
 * Photos laid out like the seeds in a sunflower head (Vogel's phyllotaxis model).
 *
 * Seed n sits at angle n × 137.5° (the "golden angle", 360° divided by the golden ratio squared)
 * and at distance √n from the middle. That spreads seeds as evenly as possible – each one gets
 * the same area – which is why sunflowers and pine cones grow this way.
 *
 * The newest photo is the middle seed; every new photo pushes the others one seed outward.
 * The whole flower turns slowly and gently breathes in and out.
 */
public final class SunflowerMotion implements MotionPattern {

    /** 137.507…° in radians: π (3 − √5). */
    public static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    static final double TURN_SECONDS = 120;     // one full turn of the flower
    static final double BREATH_SECONDS = 14;    // one breath in and out
    static final float REACH = 0.40f;           // outermost seed: 40% of the width/height from the middle

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        int n = Math.min(rank, f.count - 1);
        double theta = n * GOLDEN_ANGLE + 2 * Math.PI * f.time / TURN_SECONDS;
        double reach = Math.sqrt((n + 0.5) / f.count);                       // 0..1
        double breath = 1 + 0.06 * Math.sin(2 * Math.PI * f.time / BREATH_SECONDS);
        double r = reach * breath;
        float age = f.count > 1 ? n / (float) (f.count - 1) : 0f;           // 0 newest … 1 oldest

        out.set(f.centerX() + (float) (REACH * f.width * r * Math.cos(theta)),
                f.centerY() + (float) (REACH * f.height * r * Math.sin(theta)),
                (float) (6 * Math.sin(theta)),
                1.1f - 0.35f * age,          // the newest, in the middle, is the biggest
                1f,
                1f - 2f * age);              // and lies on top
    }
}
