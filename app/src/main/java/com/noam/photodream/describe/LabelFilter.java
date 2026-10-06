package com.noam.photodream.describe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Keeps the useful image labels: confidence at least 0.6, best first, at most 5, no duplicates. Pure Java. */
public final class LabelFilter {

    public static final float MIN_CONFIDENCE = 0.6f;
    public static final int MAX_LABELS = 5;

    private LabelFilter() { }

    /** {@code texts} and {@code confidences} are parallel lists in any order. */
    public static List<String> pick(List<String> texts, List<Float> confidences) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < texts.size() && i < confidences.size(); i++) {
            if (confidences.get(i) >= MIN_CONFIDENCE && texts.get(i) != null && !texts.get(i).trim().isEmpty()) order.add(i);
        }
        order.sort((a, b) -> Float.compare(confidences.get(b), confidences.get(a)));
        List<String> out = new ArrayList<>();
        for (int i : order) {
            String t = texts.get(i).trim().toLowerCase(Locale.ROOT);
            if (!out.contains(t)) out.add(t);
            if (out.size() == MAX_LABELS) break;
        }
        return out;
    }
}
