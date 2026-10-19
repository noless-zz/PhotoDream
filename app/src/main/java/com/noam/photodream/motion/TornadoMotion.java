package com.noam.photodream.motion;

/**
 * Photos swirling up a tornado: a funnel that is narrow at the bottom and wide at the top.
 *
 * Each card circles the funnel's axis. Seen from the front, a circle is just a side-to-side
 * movement (x = cos θ); the other half of the circle, sin θ, is how close the card is to us.
 * Cards behind the axis are drawn smaller, dimmer and underneath the others, which is all it
 * takes to look 3D. New photos are sucked in at the bottom and climb as newer ones arrive.
 * The axis itself sways a little, as real tornadoes do.
 */
public final class TornadoMotion implements MotionPattern {

    static final double SPIN_SECONDS = 9;      // one turn round the axis
    static final double SWAY_SECONDS = 23;     // the axis sways left and right

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        int n = Math.min(rank, f.count - 1);
        float up = f.count > 1 ? n / (float) (f.count - 1) : 0f;          // 0 bottom … 1 top

        // golden-angle offset so neighbours never line up behind each other
        double theta = 2 * Math.PI * f.time / SPIN_SECONDS + n * SunflowerMotion.GOLDEN_ANGLE;
        float radius = f.width * (0.06f + 0.30f * up);
        float axisX = f.centerX() + (float) (0.06 * f.width * Math.sin(2 * Math.PI * f.time / SWAY_SECONDS));
        float depth = (float) Math.sin(theta);                             // -1 behind … +1 in front
        float near = (depth + 1f) / 2f;                                    // 0 … 1

        out.set(axisX + radius * (float) Math.cos(theta),
                f.height * (0.82f - 0.64f * up),
                (float) (-12 * Math.cos(theta)),                           // leans into the turn
                0.8f + 0.25f * near,
                0.6f + 0.4f * near,
                depth);
    }
}
