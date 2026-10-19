package com.noam.photodream.motion;

/**
 * Photos strung along the two arms of a spiral galaxy that turns around the middle.
 *
 * Cards take turns between the arms (rank 0 → arm A, rank 1 → arm B, …). The further along its
 * arm a card is, the further out it sits and the more the arm has wound round, which draws the
 * spiral. A slow ripple runs along the arms so they look alive rather than rigid.
 * New photos are born in the bright core and drift outward as newer ones arrive.
 */
public final class GalaxyMotion implements MotionPattern {

    static final int ARMS = 2;
    static final double TURN_SECONDS = 90;      // one full turn of the galaxy
    static final double WIND = 1.6 * Math.PI;   // how far an arm curls from the core to the edge
    static final float REACH = 0.44f;

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        int n = Math.min(rank, f.count - 1);
        int arm = n % ARMS;
        int along = n / ARMS;
        int perArm = (f.count + ARMS - 1) / ARMS;
        double d = (along + 0.6) / (perArm + 0.1);                          // 0..1 from core to edge

        double theta = arm * 2 * Math.PI / ARMS
                + WIND * d
                + 2 * Math.PI * f.time / TURN_SECONDS
                + 0.25 * Math.sin(0.15 * f.time + 4 * d);                    // ripple along the arm

        out.set(f.centerX() + (float) (REACH * f.width * d * Math.cos(theta)),
                f.centerY() + (float) (REACH * f.height * d * Math.sin(theta)),
                (float) (10 * Math.sin(theta)),
                (float) (1.15 - 0.45 * d),   // big in the core, small at the rim
                1f,
                (float) (1 - 2 * d));
    }
}
