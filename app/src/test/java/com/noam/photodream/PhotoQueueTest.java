package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class PhotoQueueTest {

    private static List<Photo> photos(int n) {
        List<Photo> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Photo(null, "local", "p" + i));
        return l;
    }

    private static PhotoQueue ordered(int n) {
        PhotoQueue q = new PhotoQueue(photos(n), new Random(1));
        q.setReshuffleOnWrap(false);
        return q;
    }

    @Test
    public void walksInOrderAndWrapsAround() {
        PhotoQueue q = ordered(3);
        String seen = "";
        for (int i = 0; i < 7; i++) seen += q.next().name + " ";
        assertEquals("p0 p1 p2 p0 p1 p2 p0 ", seen);
    }

    @Test
    public void reshuffleKeepsEveryPhotoInEachRound() {
        PhotoQueue q = new PhotoQueue(photos(10), new Random(7));
        for (int round = 0; round < 3; round++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 10; i++) seen.add(q.next().name);
            assertEquals("round " + round, 10, seen.size());
        }
    }

    @Test
    public void reshuffleChangesTheOrderBetweenRounds() {
        PhotoQueue q = new PhotoQueue(photos(10), new Random(3));
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 0; i < 10; i++) a.add(q.next().name);
        for (int i = 0; i < 10; i++) b.add(q.next().name);
        assertNotEquals(a, b);
    }

    @Test
    public void previousGoesBackAndNextReplaysTheSame() {
        PhotoQueue q = ordered(5);
        q.next(); q.next(); q.next();            // p0 p1 p2
        assertEquals("p1", q.previous().name);
        assertEquals("p0", q.previous().name);
        assertNull(q.previous());                // start of history
        assertEquals("p1", q.next().name);       // replay, not a new photo
        assertEquals("p2", q.next().name);
        assertEquals("p3", q.next().name);       // history used up: continue
    }

    @Test
    public void failedPhotosAreSkipped() {
        PhotoQueue q = ordered(3);
        Photo first = q.next();                  // p0
        q.markFailed(first);
        assertEquals("p1", q.next().name);
        assertEquals("p2", q.next().name);
        assertEquals("p1", q.next().name);       // p0 never comes back
        assertEquals(2, q.playableCount());
    }

    @Test
    public void failedPhotosAreSkippedWhenGoingBackToo() {
        PhotoQueue q = ordered(4);
        q.next(); Photo p1 = q.next(); q.next();   // p0 p1 p2
        q.markFailed(p1);
        assertEquals("p0", q.previous().name);
    }

    @Test
    public void allFailedMeansEmptyAndNullNext() {
        PhotoQueue q = ordered(2);
        q.markFailed(q.next());
        q.markFailed(q.next());
        assertTrue(q.isEmpty());
        assertNull(q.next());
    }

    @Test
    public void excludedPhotosAreSkippedUntilIncludedAgain() {
        PhotoQueue q = ordered(3);
        q.exclude(new Photo(null, "local", "p1"));
        assertEquals("p0", q.next().name);
        assertEquals("p2", q.next().name);
        assertEquals("p0", q.next().name);
        q.include(new Photo(null, "local", "p1"));
        assertEquals("p1", q.next().name);
    }

    @Test
    public void emptyListGivesNull() {
        PhotoQueue q = new PhotoQueue(new ArrayList<>(), new Random(1));
        assertTrue(q.isEmpty());
        assertNull(q.next());
        assertNull(q.previous());
        q.setWeights(p -> 1.0);
        assertNull(q.next());
    }

    @Test
    public void weightedPickingFavorsHeavyPhotos() {
        PhotoQueue q = new PhotoQueue(photos(4), new Random(42));
        q.setWeights(p -> p.name.equals("p0") ? 6.0 : 1.0);
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < 4000; i++) count.merge(q.next().name, 1, Integer::sum);
        int heavy = count.get("p0");
        for (String other : new String[]{"p1", "p2", "p3"}) {
            assertTrue("p0 should beat " + other, heavy > 2 * count.get(other));
        }
        assertEquals(4000, count.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    public void weightedPickingNeverRepeatsImmediatelyAndSkipsZeroWeight() {
        PhotoQueue q = new PhotoQueue(photos(3), new Random(5));
        q.setWeights(p -> p.name.equals("p2") ? 0.0 : 1.0);
        String last = null;
        for (int i = 0; i < 200; i++) {
            String n = q.next().name;
            assertNotEquals("p2", n);
            assertNotEquals(last, n);
            last = n;
        }
    }

    @Test
    public void weightedWithSeededRandomIsReproducible() {
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (List<String> out : new List[]{a, b}) {
            PhotoQueue q = new PhotoQueue(photos(5), new Random(99));
            q.setWeights(p -> 1.0 + p.name.charAt(1));
            for (int i = 0; i < 20; i++) out.add(q.next().name);
        }
        assertEquals(a, b);
    }

    @Test
    public void singlePhotoIsReturnedAgainAndAgain() {
        PhotoQueue q = new PhotoQueue(photos(1), new Random(1));
        q.setWeights(p -> 1.0);
        assertEquals("p0", q.next().name);
        assertEquals("p0", q.next().name);
    }

    @Test
    public void allZeroWeightsFallBackToPlainRandom() {
        PhotoQueue q = new PhotoQueue(photos(3), new Random(1));
        q.setWeights(p -> 0.0);
        assertTrue(q.next() != null);
    }
}
