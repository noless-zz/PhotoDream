package com.noam.photodream;

import android.net.Uri;

/**
 * One photo plus the source it came from.
 *
 * {@link #key()} is a stable identity that survives app restarts and cloud
 * re-syncs (unlike the Uri, which can change). Features that remember something
 * about a photo – favorites, hidden photos, AI descriptions, frame colors per
 * source – should store that key, never the Uri.
 */
public final class Photo {

    /** Where the pixels can be read from; pass this to BitmapLoader. */
    public final Uri uri;
    /** Id of the source: "local", "onedrive", "gdrive", ... */
    public final String sourceId;
    /** Stable name inside the source: document id (phone) or file name (cache). */
    public final String name;

    public Photo(Uri uri, String sourceId, String name) {
        this.uri = uri;
        this.sourceId = sourceId;
        this.name = name;
    }

    /** Stable key, e.g. "onedrive:abc123.jpg". Same photo → same key. */
    public String key() {
        return sourceId + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Photo && key().equals(((Photo) o).key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    @Override
    public String toString() {
        return key();
    }
}
