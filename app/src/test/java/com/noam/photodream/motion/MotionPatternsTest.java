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
                            assertTrue(name + " scale", p.scale >= 0.25f && p.scale <= 1.3f);
                            assertTrue(name + " alpha", p.alpha >= 0f && p.alpha <= 1f);
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
    public void fibonacciPlacesAreAQuarterTurnAndPhiApart() {
        assertEquals(1.618034, FibonacciMotion.PHI, 1e-6);
        MotionFrame f = new MotionFrame().set(1000, 1000, 8, 0);   // square table, no turn yet
        Pose outer = new Pose(), inner = new Pose();
        FibonacciMotion fib = new FibonacciMotion();
        fib.pose(f, 6, 6, outer);
        fib.pose(f, 5, 5, inner);
        double r1 = Math.hypot(outer.x - 500, outer.y - 500), r2 = Math.hypot(inner.x - 500, inner.y - 500);
        assertEquals(FibonacciMotion.PHI, r1 / r2, 1e-3);
        double turn = Math.atan2(inner.y - 500, inner.x - 500) - Math.atan2(outer.y - 500, outer.x - 500);
        assertEquals(-Math.PI / 2, Math.IEEEremainder(turn, 2 * Math.PI), 1e-3);
        assertTrue(outer.scale > inner.scale);
    }

    @Test
    public void snakeBodyIsWhereTheHeadWasEarlier() {
        SnakeMotion s = new SnakeMotion();
        Pose body = new Pose(), headBefore = new Pose();
        s.pose(new MotionFrame().set(W, H, 8, 100), 3, 3, body);
        s.pose(new MotionFrame().set(W, H, 8, 100 - 3 * SnakeMotion.LAG_SECONDS), 0, 0, headBefore);
        assertEquals(headBefore.x, body.x, 0.01f);
        assertEquals(headBefore.y, body.y, 0.01f);
    }

    @Test
    public void warpCardsGrowAsTheyComeCloserAndFadeBeforeLooping() {
        WarpMotion w = new WarpMotion();
        MotionFrame f = new MotionFrame();
        Pose p = new Pose();
        // the time at which card 5 is at the very start of a trip
        double start = (1 - WarpMotion.frac(5 * FibonacciMotion.PHI)) * WarpMotion.TRIP_SECONDS;
        float lastScale = 0f;
        for (double t = 0.5; t < WarpMotion.TRIP_SECONDS - 0.5; t += 0.5) {
            w.pose(f.set(W, H, 8, start + t), 0, 5, p);
            assertTrue(p.scale > lastScale);
            lastScale = p.scale;
        }
        w.pose(f.set(W, H, 8, start + WarpMotion.TRIP_SECONDS - 0.05), 0, 5, p);
        assertTrue("almost gone just before it loops", p.alpha < 0.1f);
    }

    @Test
    public void orbitsRepeat() {
        OrbitsMotion o = new OrbitsMotion();
        Pose a = new Pose(), b = new Pose();
        // every ratio is a whole number, so after one base period every path has closed
        o.pose(new MotionFrame().set(W, H, 8, 7), 2, 2, a);
        o.pose(new MotionFrame().set(W, H, 8, 7 + OrbitsMotion.BASE_SECONDS), 2, 2, b);
        assertEquals(a.x, b.x, 0.5f);
        assertEquals(a.y, b.y, 0.5f);
    }

    @Test
    public void flockKeepsItsDistanceAndForgetsPhotosThatLeft() {
        FlockMotion flock = new FlockMotion();
        MotionFrame f = new MotionFrame();
        Pose[] p = new Pose[8];
        for (int i = 0; i < p.length; i++) p[i] = new Pose();
        for (double t = 0; t <= 120; t += 1.0 / 30) {
            f.set(1000, 1000, 8, t);
            for (int i = 0; i < 8; i++) flock.pose(f, i, i, p[i]);
        }
        assertEquals(8, flock.size());
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) closest = Math.min(closest, Math.hypot(p[i].x - p[j].x, p[i].y - p[j].y));
        }
        assertTrue("fish keep some personal space: " + closest, closest > 40);

        // photos 0..5 leave the table: two frames later only 6 and 7 are left
        for (int frameNo = 1; frameNo <= 2; frameNo++) {
            f.set(1000, 1000, 8, 120 + frameNo / 30.0);
            flock.pose(f, 0, 6, p[6]);
            flock.pose(f, 1, 7, p[7]);
        }
        assertEquals(2, flock.size());
    }

    @Test
    public void surprisePlaysEveryOtherPatternInTurn() {
        SurpriseMotion s = new SurpriseMotion();
        assertEquals(MotionKind.values().length - 3, s.patternCount());   // minus NONE, DRIFT and itself
        java.util.Set<Class<?>> seen = new java.util.HashSet<>();
        for (int i = 0; i < s.patternCount(); i++) {
            seen.add(s.current(i * SurpriseMotion.SWITCH_SECONDS + 1).getClass());
        }
        assertEquals(s.patternCount(), seen.size());
        assertEquals(s.current(1), s.current(SurpriseMotion.SWITCH_SECONDS - 1));
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
