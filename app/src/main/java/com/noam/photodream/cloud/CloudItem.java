package com.noam.photodream.cloud;

/** A file or folder in a cloud drive. */
public final class CloudItem {
    public final String id;
    public final String name;
    public final boolean folder;
    public final boolean image;
    /** Number of children of a folder, or -1 if the cloud doesn't tell us. */
    public final int childCount;

    public CloudItem(String id, String name, boolean folder, boolean image, int childCount) {
        this.id = id;
        this.name = name;
        this.folder = folder;
        this.image = image;
        this.childCount = childCount;
    }
}
