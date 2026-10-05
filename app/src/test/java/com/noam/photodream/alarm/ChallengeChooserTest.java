package com.noam.photodream.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ChallengeChooserTest {

    private static final List<ChallengeChooser.Option> OPTIONS = Arrays.asList(
            new ChallengeChooser.Option("flip", 3),
            new ChallengeChooser.Option("memory", 6));

    @Test
    public void requestedChallengeRunsWhenThereAreEnoughPhotos() {
        assertEquals("flip", ChallengeChooser.choose("flip", OPTIONS, 3, new Random(1)));
        assertEquals("memory", ChallengeChooser.choose("memory", OPTIONS, 50, new Random(1)));
    }

    @Test
    public void tooFewPhotosFallsBackToHold() {
        assertEquals(ChallengeChooser.HOLD, ChallengeChooser.choose("flip", OPTIONS, 2, new Random(1)));
        assertEquals(ChallengeChooser.HOLD, ChallengeChooser.choose("memory", OPTIONS, 0, new Random(1)));
    }

    @Test
    public void unknownChallengeFallsBackToHold() {
        assertEquals(ChallengeChooser.HOLD, ChallengeChooser.choose("teleport", OPTIONS, 100, new Random(1)));
        assertEquals(ChallengeChooser.HOLD, ChallengeChooser.choose(null, OPTIONS, 100, new Random(1)));
    }

    @Test
    public void randomOnlyPicksChallengesThatHaveEnoughPhotos() {
        Set<String> seen = new HashSet<>();
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) seen.add(ChallengeChooser.choose(Alarm.CHALLENGE_RANDOM, OPTIONS, 4, random));
        assertEquals(new HashSet<>(Arrays.asList("flip")), seen);
    }

    @Test
    public void randomUsesEveryUsableChallengeOverTime() {
        Set<String> seen = new HashSet<>();
        Random random = new Random(1);   // one Random: consecutive seeds give correlated first values
        for (int i = 0; i < 100; i++) seen.add(ChallengeChooser.choose(Alarm.CHALLENGE_RANDOM, OPTIONS, 10, random));
        assertTrue(seen.contains("flip") && seen.contains("memory"));
    }

    @Test
    public void randomSkipsGamesThatNeedMoreDescribedPhotosThanThereAre() {
        List<ChallengeChooser.Option> options = Arrays.asList(
                new ChallengeChooser.Option("flip", 3), new ChallengeChooser.Option("find", 4, 4));
        Random random = new Random(1);
        for (int i = 0; i < 50; i++) {
            assertEquals("flip", ChallengeChooser.choose(Alarm.CHALLENGE_RANDOM, options, 20, 2, random));
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) seen.add(ChallengeChooser.choose(Alarm.CHALLENGE_RANDOM, options, 20, 10, random));
        assertTrue(seen.contains("find") && seen.contains("flip"));
    }

    @Test
    public void namedGameStillRunsWithoutEnoughDescriptions() {
        List<ChallengeChooser.Option> options = Arrays.asList(new ChallengeChooser.Option("find", 4, 4));
        assertEquals("find", ChallengeChooser.choose("find", options, 20, 0, new Random(1)));
    }

    @Test
    public void randomWithNothingRegisteredOrNoPhotosIsHold() {
        assertEquals(ChallengeChooser.HOLD, ChallengeChooser.choose(Alarm.CHALLENGE_RANDOM, new ArrayList<>(), 100, new Random(1)));
        assertEquals(ChallengeChooser.HOLD, ChallengeChooser.choose(Alarm.CHALLENGE_RANDOM, OPTIONS, 0, new Random(1)));
    }
}
