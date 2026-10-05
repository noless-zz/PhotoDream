package com.noam.photodream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which photos the user marked: favorites (shown more often, kept in the cloud cache) and hidden
 * (never shown). Stored by {@link Photo#key()}, so marks survive restarts and cloud re-syncs.
 * Pure Java; {@link PhotoMarksStore} saves it to files/photo_marks.json.
 */
public final class PhotoMarks {

    /** How much more often a favorite comes up than a normal photo. */
    public static final double FAVORITE_WEIGHT = 3.0;

    private final Set<String> favorites = new LinkedHashSet<>();
    private final Set<String> hidden = new LinkedHashSet<>();

    public PhotoMarks() { }

    public PhotoMarks(Collection<String> favorites, Collection<String> hidden) {
        this.favorites.addAll(favorites);
        this.hidden.addAll(hidden);
        this.favorites.removeAll(this.hidden);       // hiding wins if a file is inconsistent
    }

    public synchronized boolean isFavorite(String key) { return favorites.contains(key); }

    public synchronized boolean isHidden(String key) { return hidden.contains(key); }

    public synchronized int favoriteCount() { return favorites.size(); }

    public synchronized int hiddenCount() { return hidden.size(); }

    public synchronized List<String> favoriteKeys() { return new ArrayList<>(favorites); }

    public synchronized List<String> hiddenKeys() { return new ArrayList<>(hidden); }

    /** Heart on / off. Returns the new state. A hidden photo can't be a favorite. */
    public synchronized boolean toggleFavorite(String key) {
        if (favorites.remove(key)) return false;
        hidden.remove(key);
        favorites.add(key);
        return true;
    }

    /** Never show this photo again (also removes its heart). */
    public synchronized void hide(String key) {
        favorites.remove(key);
        hidden.add(key);
    }

    /** The Undo button. */
    public synchronized void unhide(String key) { hidden.remove(key); }

    public synchronized void clearHidden() { hidden.clear(); }

    public synchronized void clearFavorites() { favorites.clear(); }

    /** 3.0 for a favorite, 0 for a hidden photo, 1.0 for everything else (see {@link PhotoQueue.Weights}). */
    public synchronized double weight(Photo photo) {
        String key = photo.key();
        if (hidden.contains(key)) return 0.0;
        return favorites.contains(key) ? FAVORITE_WEIGHT : 1.0;
    }

    /** The photos that may be shown (everything not hidden), in the same order. */
    public synchronized List<Photo> visible(List<Photo> photos) {
        List<Photo> out = new ArrayList<>();
        for (Photo p : photos) if (!hidden.contains(p.key())) out.add(p);
        return out;
    }

    /** True if any of these photos is a favorite (then weighted picking is worth switching on). */
    public synchronized boolean hasFavoriteIn(List<Photo> photos) {
        for (Photo p : photos) if (favorites.contains(p.key())) return true;
        return false;
    }

    /**
     * File names of the favorites that belong to one source, e.g. for "onedrive" the keys
     * "onedrive:od_abc.jpg" give "od_abc.jpg". The sync uses this to never delete them from the cache.
     */
    public synchronized Set<String> favoriteNamesIn(String sourceId) {
        String prefix = sourceId + ":";
        Set<String> out = new LinkedHashSet<>();
        for (String k : favorites) if (k.startsWith(prefix)) out.add(k.substring(prefix.length()));
        return out;
    }
}
