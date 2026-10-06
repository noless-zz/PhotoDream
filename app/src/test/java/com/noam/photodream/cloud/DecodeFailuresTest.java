package com.noam.photodream.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DecodeFailuresTest {

    @Test
    public void excludedOnlyAfterTwoFailures() {
        DecodeFailures f = new DecodeFailures();
        f.recordDecodeFailure("a");
        assertTrue(f.excluded().isEmpty());
        f.recordDecodeFailure("a");
        assertEquals(new HashSet<>(Arrays.asList("a")), f.excluded());
    }

    @Test
    public void successForgetsTheFailures() {
        DecodeFailures f = new DecodeFailures();
        f.recordDecodeFailure("a");
        f.recordSuccess("a");
        f.recordDecodeFailure("a");
        assertTrue(f.excluded().isEmpty());
    }

    @Test
    public void roundTripThroughMapAndRetain() {
        DecodeFailures f = new DecodeFailures();
        f.recordDecodeFailure("a");
        f.recordDecodeFailure("a");
        f.recordDecodeFailure("gone");
        DecodeFailures g = new DecodeFailures(f.asMap());
        g.retainOnly(new HashSet<>(Arrays.asList("a", "b")));
        assertEquals(new HashSet<>(Arrays.asList("a")), g.excluded());
        assertEquals(1, g.asMap().size());
    }

    @Test
    public void plannerNeverDownloadsOrCountsExcluded() {
        List<String> remote = new ArrayList<>();
        for (int i = 0; i < 10; i++) remote.add("id" + i);
        Set<String> excluded = new HashSet<>(Arrays.asList("id0", "id1", "id2"));
        SyncPlanner.Plan p = SyncPlanner.plan(remote, new HashSet<>(), excluded, 10, 0.2, new Random(1));
        assertEquals(7, p.download.size());           // 3 excluded never download
        assertFalse(p.download.contains("id0"));
        assertFalse(p.download.contains("id2"));
    }

    @Test
    public void excludedDoesNotTakeUpTargetRoom() {
        List<String> remote = new ArrayList<>();
        for (int i = 0; i < 100; i++) remote.add("id" + i);
        Set<String> excluded = new HashSet<>();
        for (int i = 0; i < 50; i++) excluded.add("id" + i);
        SyncPlanner.Plan p = SyncPlanner.plan(remote, new HashSet<>(), excluded, 20, 0.2, new Random(1));
        assertEquals(20, p.download.size());
        for (String d : p.download) assertFalse(excluded.contains(d));
    }
}
