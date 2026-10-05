package com.noam.photodream.source;

import android.content.Context;
import com.noam.photodream.Photo;

import java.util.List;

/**
 * Anything that can give us a list of photos.
 *
 * Today: {@link LocalFolderSource} (a folder on the phone) and
 * {@link CacheFolderSource} (photos the app downloaded from one cloud).
 *
 * Next step: OneDriveSource / GoogleDriveSource. Those will NOT be read
 * live by the screensaver – a background sync job downloads them into the
 * cache folder, so the slideshow never waits for the network.
 */
public interface PhotoSource {

    /** Stable id: "local" for the phone folder, the provider id for a cloud cache. */
    String id();

    /** Short name for logs and the settings screen. */
    String getName();

    /**
     * Returns photos whose uri {@link com.noam.photodream.BitmapLoader} can open.
     * Runs on a background thread – may be slow.
     */
    List<Photo> listPhotos(Context context);
}
