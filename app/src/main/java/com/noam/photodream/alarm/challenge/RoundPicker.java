package com.noam.photodream.alarm.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Builds one round of "Find the described photo": a target photo plus distractors.
 * Fairness: no distractor shares any of the target's top labels, so there is exactly one sensible
 * answer ("dog · beach" must not also match a photo of a dog at home).
 * Pure Java with a seeded Random, unit-tested.
 */
public final class RoundPicker {

    /** How many of the target's best labels distractors must stay clear of. */
    public static final int TOP_LABELS = 3;

    /** A described photo: its key (see {@code Photo.key()}) and its labels, best first. */
    public static final class Candidate {
        public final String key;
        public final List<String> labels;

        public Candidate(String key, List<String> labels) {
            this.key = key;
            this.labels = labels;
        }
    }

    /** The cards to show and which one is right. */
    public static final class Round {
        public final Candidate target;
        public final List<Candidate> cards;      // shuffled, includes the target exactly once

        Round(Candidate target, List<Candidate> cards) {
            this.target = target;
            this.cards = cards;
        }

        public int targetIndex() { return cards.indexOf(target); }
    }

    private RoundPicker() { }

    /** @return a round with {@code cardCount} cards, or null if the photos can't make a fair one. */
    public static Round pick(List<Candidate> pool, int cardCount, Random random) {
        if (cardCount < 2) return null;
        List<Candidate> order = new ArrayList<>(pool);
        Collections.shuffle(order, random);
        for (Candidate target : order) {
            Set<String> top = topLabels(target);
            if (top.isEmpty()) continue;                        // nothing to describe it by
            List<Candidate> fair = new ArrayList<>();
            for (Candidate c : pool) {
                if (c != target && !c.key.equals(target.key) && disjoint(top, c)) fair.add(c);
            }
            if (fair.size() < cardCount - 1) continue;
            Collections.shuffle(fair, random);
            List<Candidate> cards = new ArrayList<>(fair.subList(0, cardCount - 1));
            cards.add(target);
            Collections.shuffle(cards, random);
            return new Round(target, cards);
        }
        return null;
    }

    private static Set<String> topLabels(Candidate c) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < c.labels.size() && out.size() < TOP_LABELS; i++) {
            out.add(c.labels.get(i).trim().toLowerCase(Locale.ROOT));
        }
        out.remove("");
        return out;
    }

    private static boolean disjoint(Set<String> top, Candidate other) {
        for (String l : other.labels) if (top.contains(l.trim().toLowerCase(Locale.ROOT))) return false;
        return true;
    }
}
