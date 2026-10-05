package com.noam.photodream.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SyncPlannerFavoritesTest {

    private static List<String> ids(int from, int to) {
        List<String> l = new ArrayList<>();
        for (int i = from; i < to; i++) l.add("id" + i);
        return l;
    }

    @Test
    public void favoritesAreNeverRotatedOut() {
        Set<String> cached = new HashSet<>(ids(0, 200));
        Set<String> favs = new HashSet<>(ids(0, 30));
        for (long seed = 0; seed < 25; seed++) {
            SyncPlanner.Plan p = SyncPlanner.plan(ids(0, 1000), cached, new HashSet<>(), favs, 200, 0.2, new Random(seed));
            for (String f : favs) {
                assertTrue("favorite " + f + " must be kept", p.keep.contains(f));
                assertFalse("favorite " + f + " must not be deleted", p.delete.contains(f));
            }
            // the rotation still happens, just among the other photos: a fifth of 200 swapped
            assertEquals(40, p.delete.size());
            assertEquals(40, p.download.size());
        }
    }

    @Test
    public void favoritesAboveTheTargetAreStillKept() {
        Set<String> cached = new HashSet<>(ids(0, 60));
        Set<String> favs = new HashSet<>(ids(0, 50));
        SyncPlanner.Plan p = SyncPlanner.plan(ids(0, 500), cached, new HashSet<>(), favs, 40, 0.2, new Random(1));
        assertTrue(p.keep.containsAll(favs));
        assertEquals(50, p.keep.size());               // only favorites remain
        assertEquals(10, p.delete.size());             // the 10 non-favorites go to make room
        assertEquals(0, p.download.size());            // nothing new: the favorites already exceed the target
    }

    @Test
    public void favoriteDeletedInTheCloudIsStillRemoved() {
        Set<String> cached = new HashSet<>(ids(0, 10));
        Set<String> favs = new HashSet<>(ids(0, 3));
        SyncPlanner.Plan p = SyncPlanner.plan(ids(1, 10), cached, new HashSet<>(), favs, 20, 0.2, new Random(1));   // id0 is gone
        assertTrue(p.gone.contains("id0"));
        assertFalse(p.keep.contains("id0"));
        assertTrue(p.keep.contains("id1") && p.keep.contains("id2"));
    }

    @Test
    public void noFavoritesBehavesLikeBefore() {
        Set<String> cached = new HashSet<>(ids(0, 200));
        SyncPlanner.Plan a = SyncPlanner.plan(ids(0, 1000), cached, 200, 0.2, new Random(2));
        SyncPlanner.Plan b = SyncPlanner.plan(ids(0, 1000), cached, new HashSet<>(), new HashSet<>(), 200, 0.2, new Random(2));
        assertEquals(a.keep, b.keep);
        assertEquals(a.delete, b.delete);
        assertEquals(a.download, b.download);
    }

    @Test
    public void favoriteThatIsNotCachedYetGetsNoSpecialTreatment() {
        // marks are only about cached photos; an uncached id in the set is simply ignored
        Set<String> cached = new HashSet<>(ids(0, 5));
        SyncPlanner.Plan p = SyncPlanner.plan(ids(0, 20), cached, new HashSet<>(), new HashSet<>(ids(10, 12)), 10, 0.2, new Random(1));
        assertEquals(10, p.keep.size() + p.download.size());
    }
}
