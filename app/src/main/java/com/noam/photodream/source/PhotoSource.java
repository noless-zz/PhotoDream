package com.noam.photodream.source;

import android.content.Context;
import android.net.Uri;

import java.util.List;

/**
 * Anything that can give us a list of photos.
 *
 * Today: {@link LocalFolderSource} (a folder on the phone) and
 * {@link CacheFolderSource} (photos the app downloaded itself).
 *
 * Next step: OneDriveSource / GoogleDriveSource. Those will NOT be read
 * live by the screensaver – a background sync job downloads them into the
 * cache folder, so the slideshow never waits for the network.
 */
public interface PhotoSource {

    /** Short name for logs and the settings screen. */
    String getName();

    /**
     * Returns photo Uris that {@link com.noam.photodream.BitmapLoader} can open.
     * Runs on a background thread – may be slow.
     */
    List<Uri> listPhotos(Context context);
}
