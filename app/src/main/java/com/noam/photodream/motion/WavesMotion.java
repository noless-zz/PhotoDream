package com.noam.photodream.motion;

/**
 * A conveyor belt of photos bobbing on sine waves, like boats on the sea.
 *
 * Cards stand in a row (two rows when there are many) – newest at the start, oldest at the
 * end – and a wave y = A·sin(k·x − ω·t) rolls along it. Each card also tilts with the slope of
 * the wave under it (the slope of sin is cos), which is what makes it look like it floats.
 * The second row rides half a wave behind the first.
 */
public final class WavesMotion implements MotionPattern {

    static final double WAVE_SECONDS = 6;      // one wave passes a card
    static final double WAVES_ACROSS = 1.3;    // how many waves fit along the row
    static final float MAX_TILT = 15f;

    @Override
    public void pose(MotionFrame f, int rank, long id, Pose out) {
        int n = Math.min(rank, f.count - 1);
        int rows = f.count > 6 ? 2 : 1;
        int cols = (f.count + rows - 1) / rows;
        int row = n % rows;
        int col = n / rows;
        float u = cols > 1 ? col / (float) (cols - 1) : 0.5f;           // 0 start … 1 end of the row

        float rowY = rows == 1 ? f.centerY() : f.height * (row == 0 ? 0.32f : 0.68f);
        float amp = f.height * (rows == 1 ? 0.09f : 0.07f);
        float span = 0.76f * f.width;
        double phase = 2 * Math.PI * (WAVES_ACROSS * u - f.time / WAVE_SECONDS) + row * Math.PI;

        // dy/dx of amp·sin(2π·WAVES_ACROSS·x/span − …)
        double slope = amp * Math.cos(phase) * 2 * Math.PI * WAVES_ACROSS / span;
        float tilt = (float) Math.toDegrees(Math.atan(slope));
        tilt = Math.max(-MAX_TILT, Math.min(MAX_TILT, tilt));

        out.set(f.width * 0.12f + span * u,
                rowY + amp * (float) Math.sin(phase),
                tilt,
                1f,
                1f,
                0f);
    }
}
