package com.noam.photodream.alarm.challenge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RoundPickerTest {

    private static RoundPicker.Candidate c(String key, String... labels) {
        return new RoundPicker.Candidate(key, Arrays.asList(labels));
    }

    /** 12 photos with clearly different labels. */
    private static List<RoundPicker.Candidate> variedPool() {
        List<RoundPicker.Candidate> pool = new ArrayList<>();
        String[] topics = {"dog", "beach", "pizza", "car", "tree", "baby", "mountain", "cake", "bicycle", "boat", "guitar", "snow"};
        for (int i = 0; i < topics.length; i++) pool.add(c("p" + i, topics[i], "outdoor" + i, "thing" + i));
        return pool;
    }

    @Test
    public void roundHasTheRequestedNumberOfCardsAndTheTargetOnce() {
        for (int n : new int[]{4, 6, 9}) {
            RoundPicker.Round r = RoundPicker.pick(variedPool(), n, new Random(n));
            assertNotNull(r);
            assertEquals(n, r.cards.size());
            int hits = 0;
            for (RoundPicker.Candidate x : r.cards) if (x == r.target) hits++;
            assertEquals(1, hits);
            assertEquals(r.target, r.cards.get(r.targetIndex()));
            Set<String> keys = new HashSet<>();
            for (RoundPicker.Candidate x : r.cards) keys.add(x.key);
            assertEquals("no photo twice", n, keys.size());
        }
    }

    @Test
    public void distractorsNeverShareTheTargetsTopLabels() {
        List<RoundPicker.Candidate> pool = new ArrayList<>();
        // lots of overlap: "dog" and "outdoor" are everywhere
        for (int i = 0; i < 30; i++) {
            pool.add(c("d" + i, "dog", "outdoor", "grass" + (i % 3)));
            pool.add(c("n" + i, "city" + i, "street", "night"));
            pool.add(c("b" + i, "beach", "outdoor", "sea" + i));
        }
        for (long seed = 0; seed < 100; seed++) {
            RoundPicker.Round r = RoundPicker.pick(pool, 6, new Random(seed));
            assertNotNull(r);
            List<String> top = r.target.labels.subList(0, Math.min(3, r.target.labels.size()));
            for (RoundPicker.Candidate x : r.cards) {
                if (x == r.target) continue;
                for (String l : x.labels) assertFalse(x.key + " shares " + l, top.contains(l));
            }
        }
    }

    @Test
    public void notEnoughDifferentPhotosGivesNull() {
        List<RoundPicker.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 10; i++) pool.add(c("p" + i, "dog", "grass"));   // all the same
        assertNull(RoundPicker.pick(pool, 4, new Random(1)));
        assertNull(RoundPicker.pick(variedPool().subList(0, 3), 4, new Random(1)));
        assertNull(RoundPicker.pick(new ArrayList<>(), 4, new Random(1)));
    }

    @Test
    public void photosWithoutLabelsCanBeDistractorsButNeverTargets() {
        List<RoundPicker.Candidate> pool = new ArrayList<>();
        pool.add(c("target", "cat", "sofa"));
        pool.add(c("x1")); pool.add(c("x2")); pool.add(c("x3"));
        for (long seed = 0; seed < 30; seed++) {
            RoundPicker.Round r = RoundPicker.pick(pool, 4, new Random(seed));
            assertNotNull(r);
            assertEquals("target", r.target.key);
        }
    }

    @Test
    public void sameSeedSameRound() {
        RoundPicker.Round a = RoundPicker.pick(variedPool(), 6, new Random(7));
        RoundPicker.Round b = RoundPicker.pick(variedPool(), 6, new Random(7));
        assertEquals(a.target.key, b.target.key);
        for (int i = 0; i < 6; i++) assertEquals(a.cards.get(i).key, b.cards.get(i).key);
    }

    @Test
    public void differentSeedsPickDifferentTargets() {
        Set<String> targets = new HashSet<>();
        Random random = new Random(1);
        for (int i = 0; i < 60; i++) targets.add(RoundPicker.pick(variedPool(), 4, random).target.key);
        assertTrue(targets.size() > 3);
    }

    @Test
    public void labelComparisonIgnoresCaseAndSpaces() {
        List<RoundPicker.Candidate> pool = new ArrayList<>();
        pool.add(c("a", "Dog", "grass"));
        pool.add(c("b", " dog ", "sofa"));          // same thing, different spelling
        pool.add(c("c", "tree", "sky")); pool.add(c("d", "car", "road")); pool.add(c("e", "boat", "sea"));
        for (long seed = 0; seed < 100; seed++) {
            RoundPicker.Round r = RoundPicker.pick(pool, 4, new Random(seed));
            if (r == null) continue;
            boolean hasA = false, hasB = false;
            for (RoundPicker.Candidate x : r.cards) {
                hasA |= x.key.equals("a");
                hasB |= x.key.equals("b");
            }
            // when one dog is the answer, the other dog must not be on the table
            if (r.target.key.equals("a")) assertFalse(hasB);
            if (r.target.key.equals("b")) assertFalse(hasA);
        }
    }

    @Test
    public void tooFewCardsRequestedGivesNull() {
        assertNull(RoundPicker.pick(variedPool(), 1, new Random(1)));
    }
}
