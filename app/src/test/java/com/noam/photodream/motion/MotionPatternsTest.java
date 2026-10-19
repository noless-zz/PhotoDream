package com.noam.photodream.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Rules every pattern must follow, checked on portrait and landscape screens for many times. */
public class MotionPatternsTest {

    private static final float W = 1080, H = 2280;

    private static MotionPattern[] all() {
        java.util.List<MotionPattern> list = new java.util.ArrayList<>();
        for (MotionKind k : MotionKind.values()) {
            MotionPattern p = k.newPattern();
            if (p != null) list.add(p);
        }
        return list.toArray(new MotionPattern[0]);
    }

    @Test
    public void everyPatternKindHasAPatternExceptNoneAndDrift() {
        for (MotionKind k : MotionKind.values()) {
            if (k == MotionKind.NONE || k == MotionKind.DRIFT) assertNull(k.newPattern());
            else assertNotNull(k.name(), k.newPattern());
        }
    }

    @Test
    public void cardCentresStayOnScreenAndValuesAreSane() {
        Pose p = new Pose();
        MotionFrame f = new MotionFrame();
        float[][] screens = {{W, H}, {H, W}};
        for (MotionPattern pattern : all()) {
            String name = pattern.getClass().getSimpleName();
            for (float[] s : screens) {
                for (int count : new int[] {1, 3, 8, 20}) {
                    for (double t = 0; t < 600; t += 3.7) {
                        f.set(s[0], s[1], count, t);
                        for (int rank = 0; rank < count + 2; rank++) {   // ranks past the end are clamped
                            pattern.pose(f, rank, rank, p);
                            assertTrue(name + " x " + p.x, p.x >= 0.05f * s[0] && p.x <= 0.95f * s[0]);
                            assertTrue(name + " y " + p.y, p.y >= 0.05f * s[1] && p.y <= 0.95f * s[1]);
                            assertTrue(name + " rotation", Math.abs(p.rotation) <= 20f);
                            assertTrue(name + " scale", p.scale >= 0.5f && p.scale <= 1.2f);
                            assertTrue(name + " alpha", p.alpha >= 0.5f && p.alpha <= 1f);
                            assertTrue(name + " depth", p.depth >= -1f && p.depth <= 1f);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void patternsMoveOverTime() {
        Pose a = new Pose(), b = new Pose();
        MotionFrame f = new MotionFrame();
        for (MotionPattern pattern : all()) {
            pattern.pose(f.set(W, H, 8, 10), 3, 3, a);
            pattern.pose(f.set(W, H, 8, 12), 3, 3, b);
            assertTrue(pattern.getClass().getSimpleName(),
                    Math.hypot(a.x - b.x, a.y - b.y) > 1f);
        }
    }

    @Test
    public void neighbouringRanksGetDifferentPlaces() {
        Pose a = new Pose(), b = new Pose();
        MotionFrame f = new MotionFrame().set(W, H, 8, 42);
        for (MotionPattern pattern : all()) {
            for (int rank = 0; rank < 7; rank++) {
                pattern.pose(f, rank, rank, a);
                pattern.pose(f, rank + 1, rank + 1, b);
                assertTrue(pattern.getClass().getSimpleName() + " rank " + rank,
                        Math.hypot(a.x - b.x, a.y - b.y) > 20f);
            }
        }
    }

    @Test
    public void sunflowerSeedsAreAGoldenAngleApart() {
        assertEquals(137.508, Math.toDegrees(SunflowerMotion.GOLDEN_ANGLE), 0.001);
        // on a square table, at t=0 and with no breathing yet, seed n points at n × golden angle
        MotionFrame f = new MotionFrame().set(1000, 1000, 10, 0);
        Pose p = new Pose();
        new SunflowerMotion().pose(f, 4, 4, p);
        double angle = Math.atan2(p.y - 500, p.x - 500);
        double expected = Math.IEEEremainder(4 * SunflowerMotion.GOLDEN_ANGLE, 2 * Math.PI);
        assertEquals(expected, angle, 1e-4);
    }

    @Test
    public void sunflowerNewestIsInTheMiddleAndBiggest() {
        MotionFrame f = new MotionFrame().set(W, H, 8, 5);
        Pose newest = new Pose(), oldest = new Pose();
        SunflowerMotion s = new SunflowerMotion();
        s.pose(f, 0, 0, newest);
        s.pose(f, 7, 7, oldest);
        assertTrue(dist(newest, f) < dist(oldest, f));
        assertTrue(newest.scale > oldest.scale);
        assertTrue(newest.depth > oldest.depth);
    }

    @Test
    public void galaxyNewestStartsNearTheCore() {
        MotionFrame f = new MotionFrame().set(W, H, 10, 7);
        Pose core = new Pose(), rim = new Pose();
        GalaxyMotion g = new GalaxyMotion();
        g.pose(f, 0, 0, core);
        g.pose(f, 9, 9, rim);
        assertTrue(dist(core, f) < dist(rim, f));
    }

    @Test
    public void tornadoClimbsAndItsBackIsSmallerAndDimmer() {
        MotionFrame f = new MotionFrame().set(W, H, 8, 0);
        Pose bottom = new Pose(), top = new Pose();
        TornadoMotion t = new TornadoMotion();
        t.pose(f, 0, 0, bottom);
        t.pose(f, 7, 7, top);
        assertTrue(bottom.y > top.y);   // screen y grows downward

        // follow one card round a full turn: whenever it is behind (depth < 0) it is smaller and dimmer
        Pose p = new Pose();
        for (double time = 0; time < TornadoMotion.SPIN_SECONDS; time += 0.25) {
            t.pose(f.set(W, H, 8, time), 3, 3, p);
            if (p.depth < -0.5f) assertTrue(p.scale < 0.95f && p.alpha < 0.8f);
            if (p.depth > 0.5f) assertTrue(p.scale > 0.95f && p.alpha > 0.8f);
        }
    }

    @Test
    public void wavesRunFromStartToEndOfTheRow() {
        MotionFrame f = new MotionFrame().set(H, W, 5, 0);   // landscape, one row
        Pose first = new Pose(), last = new Pose();
        WavesMotion w = new WavesMotion();
        w.pose(f, 0, 0, first);
        w.pose(f, 4, 4, last);
        assertTrue(first.x < last.x);
        assertNotEquals(first.y, last.y, 0.5f);
    }

    @Test
    public void wavesUseTwoRowsWhenCrowded() {
        MotionFrame f = new MotionFrame().set(W, H, 10, 0);
        Pose a = new Pose(), b = new Pose();
        WavesMotion w = new WavesMotion();
        w.pose(f, 0, 0, a);
        w.pose(f, 1, 1, b);
        assertTrue(a.y < H / 2f && b.y > H / 2f);
    }

    @Test
    public void parseFallsBackForUnknownNames() {
        assertEquals(MotionKind.GALAXY, MotionKind.parse("GALAXY", MotionKind.DRIFT));
        assertEquals(MotionKind.DRIFT, MotionKind.parse(null, MotionKind.DRIFT));
        assertEquals(MotionKind.NONE, MotionKind.parse("NOT_A_PATTERN", MotionKind.NONE));
    }

    private static double dist(Pose p, MotionFrame f) {
        return Math.hypot(p.x - f.centerX(), p.y - f.centerY());
    }
}
