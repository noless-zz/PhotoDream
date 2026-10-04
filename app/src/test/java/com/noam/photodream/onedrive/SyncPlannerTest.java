package com.noam.photodream.onedrive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SyncPlannerTest {

    private static List<String> ids(int from, int to) {
        List<String> l = new ArrayList<>();
        for (int i = from; i < to; i++) l.add("id" + i);
        return l;
    }

    @Test
    public void firstSyncDownloadsUpToTarget() {
        SyncPlanner.Plan p = SyncPlanner.plan(ids(0, 1000), new HashSet<>(), 200, 0.2, new Random(1));
        assertEquals(200, p.download.size());
        assertTrue(p.keep.isEmpty());
        assertTrue(p.delete.isEmpty());
    }

    @Test
    public void fullCacheRotatesAFifth() {
        Set<String> cached = new HashSet<>(ids(0, 200));
        SyncPlanner.Plan p = SyncPlanner.plan(ids(0, 1000), cached, 200, 0.2, new Random(2));
        assertEquals(160, p.keep.size());
        assertEquals(40, p.delete.size());
        assertEquals(40, p.download.size());
        for (String d : p.download) assertFalse(cached.contains(d));
    }

    @Test
    public void smallFolderKeepsEverything() {
        Set<String> cached = new HashSet<>(ids(0, 100));
        SyncPlanner.Plan p = SyncPlanner.plan(ids(0, 150), cached, 200, 0.2, new Random(3));
        assertEquals(100, p.keep.size());
        assertEquals(50, p.download.size());
        assertTrue(p.delete.isEmpty());
    }

    @Test
    public void photosDeletedInOneDriveAreRemoved() {
        Set<String> cached = new HashSet<>(ids(0, 50));
        SyncPlanner.Plan p = SyncPlanner.plan(ids(10, 50), cached, 200, 0.2, new Random(4));
        assertEquals(10, p.delete.size());
        assertEquals(40, p.keep.size());
        assertTrue(p.download.isEmpty());
    }

    @Test
    public void loweringTheLimitShrinksTheCache() {
        Set<String> cached = new HashSet<>(ids(0, 200));
        SyncPlanner.Plan p = SyncPlanner.plan(ids(0, 200), cached, 50, 0.2, new Random(5));
        assertEquals(50, p.keep.size());
        assertEquals(150, p.delete.size());
        assertTrue(p.download.isEmpty());
    }

    @Test
    public void neverMoreThanTarget() {
        Random r = new Random(6);
        for (int round = 0; round < 200; round++) {
            int remote = r.nextInt(500), cachedN = r.nextInt(400), target = 50 + r.nextInt(300);
            Set<String> cached = new HashSet<>(ids(r.nextInt(100), r.nextInt(100) + cachedN));
            SyncPlanner.Plan p = SyncPlanner.plan(ids(0, remote), cached, target, 0.2, r);
            assertTrue(p.keep.size() + p.download.size() <= target);
            Set<String> all = new HashSet<>(p.keep);
            all.addAll(p.delete);
            assertEquals(cached, all);   // every cached file is either kept or deleted
        }
    }
}
