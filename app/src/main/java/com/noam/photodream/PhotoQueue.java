package com.noam.photodream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Decides which photo comes next. Pure Java (no Android imports) so it is unit-tested.
 * Both display modes use it, so "which photo next?" lives in one place.
 *
 * <ul>
 *   <li>Without weights it walks the list in order; when the list is used up it starts again
 *       (reshuffled if {@link #setReshuffleOnWrap(boolean)} is on).</li>
 *   <li>With a {@link Weights} hook (favorites, "on this day" …) every pick is a weighted random
 *       choice, never the same photo twice in a row.</li>
 *   <li>{@link #markFailed(Photo)} remembers photos that could not be decoded and skips them.</li>
 *   <li>{@link #previous()} / {@link #next()} walk back and forth through what was already shown.</li>
 * </ul>
 */
public final class PhotoQueue {

    /** How often a photo should come up compared to the others (1.0 = normal, 0 = never). */
    public interface Weights {
        double weight(Photo photo);
    }

    private static final int MAX_HISTORY = 100;

    private final List<Photo> photos;
    private final List<Photo> order;           // the current round, used when there are no weights
    private final Random random;
    private final Set<String> failed = new HashSet<>();
    private final List<Photo> history = new ArrayList<>();
    private int cursor = -1;                   // position in history of the photo on screen
    private int orderPos;
    private Weights weights;
    private boolean reshuffleOnWrap = true;

    public PhotoQueue(List<Photo> photos, Random random) {
        this.photos = new ArrayList<>(photos);
        this.order = new ArrayList<>(photos);
        this.random = random;
    }

    public void setWeights(Weights weights) { this.weights = weights; }

    public void setReshuffleOnWrap(boolean on) { reshuffleOnWrap = on; }

    /** True when there is nothing (left) to show: no photos, or every one has failed. */
    public boolean isEmpty() { return playableCount() == 0; }

    public int playableCount() {
        int n = 0;
        for (Photo p : photos) if (!failed.contains(p.key())) n++;
        return n;
    }

    /** Remember that this photo can't be shown; it is skipped from now on. */
    public void markFailed(Photo photo) { failed.add(photo.key()); }

    /** Same effect, for photos the user hid: skipped from now on. */
    public void exclude(Photo photo) { failed.add(photo.key()); }

    /** The Undo after hiding: the photo may come up again. */
    public void include(Photo photo) { failed.remove(photo.key()); }

    /** The next photo, or null if nothing can be shown. */
    public Photo next() {
        for (int i = cursor + 1; i < history.size(); i++) {      // we went back earlier: replay forward
            if (!failed.contains(history.get(i).key())) {
                cursor = i;
                return history.get(i);
            }
        }
        while (history.size() > cursor + 1) history.remove(history.size() - 1);   // only failed ones were left

        Photo p = weights == null ? nextInOrder() : nextWeighted();
        if (p == null) return null;
        history.add(p);
        if (history.size() > MAX_HISTORY) history.remove(0);
        cursor = history.size() - 1;
        return p;
    }

    /** The photo shown before the current one, or null at the start of the history. */
    public Photo previous() {
        for (int i = cursor - 1; i >= 0; i--) {
            if (!failed.contains(history.get(i).key())) {
                cursor = i;
                return history.get(i);
            }
        }
        return null;
    }

    private Photo nextInOrder() {
        for (int tried = 0; tried < order.size(); tried++) {
            if (orderPos >= order.size()) {
                if (reshuffleOnWrap) Collections.shuffle(order, random);
                orderPos = 0;
            }
            Photo p = order.get(orderPos++);
            if (!failed.contains(p.key())) return p;
        }
        return null;
    }

    private Photo nextWeighted() {
        Photo last = cursor >= 0 ? history.get(cursor) : null;
        List<Photo> candidates = new ArrayList<>();
        for (Photo p : photos) if (!failed.contains(p.key())) candidates.add(p);
        if (candidates.size() > 1 && last != null) candidates.remove(last);   // no immediate repeat
        if (candidates.isEmpty()) return null;

        double total = 0;
        double[] w = new double[candidates.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = Math.max(0.0, weights.weight(candidates.get(i)));
            total += w[i];
        }
        if (total <= 0) return candidates.get(random.nextInt(candidates.size()));   // all weights 0: plain random
        double r = random.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0 && w[i] > 0) return candidates.get(i);
        }
        for (int i = w.length - 1; i >= 0; i--) if (w[i] > 0) return candidates.get(i);   // rounding safety net
        return null;
    }
}
