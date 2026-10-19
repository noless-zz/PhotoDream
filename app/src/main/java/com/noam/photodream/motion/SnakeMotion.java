package com.noam.photodream.motion;

/**
 * A snake of photos: the newest is the head, the others follow in a line.
 *
 * The head wanders along a Lissajous curve – x and y each swing on a sine wave, but at
 * different speeds, so the path never quite repeats. The trick for the body is that the path
 * is a function of time: the card k places behind the head is simply where the head was
 * k × {@link #LAG_SECONDS} ago. No memory of past positions is needed.
 */
public final class SnakeMotion implements MotionPattern {

    static final double LAG_SECONDS = 1.6;     // how far behind each body card trails
    static final double X_SECONDS = 37;        // one left-right swing of the head
    static final double Y_SECONDS = 23;        // one up-down swing (not a multiple of 37: no repeats)
    static final float REACH = 0.38f;

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        int n = Math.min(rank, f.count - 1);
        double t = f.time - n * LAG_SECONDS;
        double ax = 2 * Math.PI / X_SECONDS, ay = 2 * Math.PI / Y_SECONDS;
        double x = Math.sin(ax * t);
        double y = Math.sin(ay * t + 0.7);
        // the direction the head is going here: the derivative of the path
        double heading = Math.atan2(ay * Math.cos(ay * t + 0.7) * f.height, ax * Math.cos(ax * t) * f.width);
        float tail = f.count > 1 ? n / (float) (f.count - 1) : 0f;     // 0 head … 1 tip of the tail

        out.set(f.centerX() + REACH * f.width * (float) x,
                f.centerY() + REACH * f.height * (float) y,
                (float) (10 * Math.sin(heading)),
                1.1f - 0.3f * tail,            // the body tapers toward the tail
                1f,
                1f - 2f * tail);               // head on top
    }
}
