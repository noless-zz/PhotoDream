package com.noam.photodream.motion;

/**
 * "Surprise me": plays every other pattern in turn, switching every few minutes.
 *
 * Switching needs no special animation – the view always eases cards toward their target, so
 * when the target suddenly comes from a different pattern the photos glide into the new shape.
 */
public final class SurpriseMotion implements MotionPattern {

    static final double SWITCH_SECONDS = 4 * 60;

    private final MotionPattern[] patterns;

    public SurpriseMotion() {
        java.util.List<MotionPattern> list = new java.util.ArrayList<>();
        for (MotionKind k : MotionKind.values()) {
            if (k == MotionKind.SURPRISE) continue;     // not itself
            MotionPattern p = k.newPattern();
            if (p != null) list.add(p);
        }
        patterns = list.toArray(new MotionPattern[0]);
    }

    /** Which pattern is playing at {@code time}. */
    MotionPattern current(double time) {
        int i = (int) Math.floor(Math.max(0, time) / SWITCH_SECONDS);
        return patterns[i % patterns.length];
    }

    int patternCount() {
        return patterns.length;
    }

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        current(f.time).pose(f, rank, id, out);
    }
}
