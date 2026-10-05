package com.noam.photodream.cloud;

/**
 * Item ids for files that live in <i>another person's</i> drive (a folder shared with me).
 * OneDrive needs the drive id next to the item id to open such an item, so the id we store is
 * {@code driveId|itemId}. Ids of the user's own files stay exactly as they were, so existing caches
 * keep working. ("|" is used because OneDrive item ids already contain "!", e.g. "9C6F!123".)
 * Pure Java, unit-tested.
 */
public final class CloudIds {

    private static final char SEPARATOR = '|';

    /** The two halves of an id; {@code driveId} is null for the user's own drive. */
    public static final class Parts {
        public final String driveId;
        public final String itemId;

        Parts(String driveId, String itemId) {
            this.driveId = driveId;
            this.itemId = itemId;
        }
    }

    private CloudIds() { }

    /** An id for an item in another drive; with no drive id the plain item id is returned unchanged. */
    public static String encode(String driveId, String itemId) {
        if (driveId == null || driveId.isEmpty()) return itemId;
        return driveId + SEPARATOR + itemId;
    }

    public static Parts decode(String id) {
        if (id == null) return new Parts(null, null);
        int i = id.indexOf(SEPARATOR);
        if (i <= 0) return new Parts(null, id);          // plain id (no drive part)
        return new Parts(id.substring(0, i), id.substring(i + 1));
    }
}
