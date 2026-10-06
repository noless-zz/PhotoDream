package com.noam.photodream.alarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides which challenge actually runs. Pure Java so the "never trap the user" rule is tested:
 * if the chosen challenge is unknown, or there are too few photos for it, the always-available
 * "hold to stop" fallback is used instead.
 */
public final class ChallengeChooser {

    public static final String HOLD = "hold";

    /** What the chooser needs to know about a challenge. */
    public static final class Option {
        public final String id;
        public final int minPhotos;
        /** Described photos needed (for the "find the photo" game); 0 = descriptions not needed. */
        public final int minDescribed;

        public Option(String id, int minPhotos) {
            this(id, minPhotos, 0);
        }

        public Option(String id, int minPhotos, int minDescribed) {
            this.id = id;
            this.minPhotos = minPhotos;
            this.minDescribed = minDescribed;
        }
    }

    private ChallengeChooser() { }

    /**
     * @param requested an option id, or {@link Alarm#CHALLENGE_RANDOM}
     * @param options   every registered challenge except the fallback
     * @return the id to run; {@link #HOLD} when nothing suitable exists
     */
    public static String choose(String requested, List<Option> options, int photoCount, Random random) {
        return choose(requested, options, photoCount, Integer.MAX_VALUE, random);
    }

    /**
     * With descriptions known: a <i>random</i> pick skips games that need more described photos than
     * there are. A game that was asked for by name still runs (it explains and falls back by itself).
     */
    public static String choose(String requested, List<Option> options, int photoCount, int describedCount, Random random) {
        if (Alarm.CHALLENGE_RANDOM.equals(requested)) {
            List<String> usable = new ArrayList<>();
            for (Option o : options) if (photoCount >= o.minPhotos && describedCount >= o.minDescribed) usable.add(o.id);
            return usable.isEmpty() ? HOLD : usable.get(random.nextInt(usable.size()));
        }
        for (Option o : options) {
            if (o.id.equals(requested)) return photoCount >= o.minPhotos ? o.id : HOLD;
        }
        return HOLD;
    }
}
