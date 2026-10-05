package com.noam.photodream.alarm;

import com.noam.photodream.R;
import com.noam.photodream.alarm.challenge.FindDescribedChallenge;
import com.noam.photodream.alarm.challenge.FlipChallenge;
import com.noam.photodream.alarm.challenge.MemoryChallenge;
import com.noam.photodream.alarm.challenge.RunawayChallenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Registry of the wake-up challenges: id → factory, display name, minimum photos.
 * Adding a challenge = one {@code register(...)} line here (issues #17-#19, #22).
 * The "hold to stop" fallback is built in and never listed in the alarm editor.
 */
public final class Challenges {

    public interface Factory { AlarmChallenge create(); }

    public static final class Info {
        public final String id;
        public final int nameRes;
        public final int minPhotos;
        public final int minDescribed;
        public final Factory factory;

        Info(String id, int nameRes, int minPhotos, int minDescribed, Factory factory) {
            this.id = id;
            this.nameRes = nameRes;
            this.minPhotos = minPhotos;
            this.minDescribed = minDescribed;
            this.factory = factory;
        }
    }

    private static final List<Info> ALL = new ArrayList<>();

    static {
        // a challenge is one line: id, name, the fewest photos it needs, factory
        register("flip", R.string.challenge_flip, 1, FlipChallenge::new);
        register("runaway", R.string.challenge_runaway, 1, RunawayChallenge::new);
        register("memory", R.string.challenge_memory, 3, MemoryChallenge::new);
        registerDescribed("find", R.string.challenge_find, 4, 4, FindDescribedChallenge::new);
    }

    private Challenges() { }

    private static void register(String id, int nameRes, int minPhotos, Factory factory) {
        ALL.add(new Info(id, nameRes, minPhotos, 0, factory));
    }

    /** For games that need photos with descriptions (random only picks them when enough exist). */
    private static void registerDescribed(String id, int nameRes, int minPhotos, int minDescribed, Factory factory) {
        ALL.add(new Info(id, nameRes, minPhotos, minDescribed, factory));
    }

    /** Challenges the user can pick in the editor (the fallback is not among them). */
    public static List<Info> all() { return new ArrayList<>(ALL); }

    /**
     * The challenge that should run for this alarm: the requested one, a random one, or
     * "hold to stop" when none fits (unknown id, or too few photos).
     */
    public static AlarmChallenge create(String requested, int photoCount, int describedCount, Random random) {
        List<ChallengeChooser.Option> options = new ArrayList<>();
        for (Info i : ALL) options.add(new ChallengeChooser.Option(i.id, i.minPhotos, i.minDescribed));
        String id = ChallengeChooser.choose(requested, options, photoCount, describedCount, random);
        for (Info i : ALL) if (i.id.equals(id)) return i.factory.create();
        return new HoldToStopChallenge();
    }
}
