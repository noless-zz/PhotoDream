package com.noam.photodream.cloud;

import com.noam.photodream.gdrive.GoogleDriveProvider;
import com.noam.photodream.onedrive.OneDriveProvider;

import java.util.Arrays;
import java.util.List;

/** All cloud sources the app knows about. */
public final class CloudProviders {

    public static final CloudProvider ONEDRIVE = new OneDriveProvider();
    public static final CloudProvider GOOGLE_DRIVE = new GoogleDriveProvider();

    private static final List<CloudProvider> ALL = Arrays.asList(ONEDRIVE, GOOGLE_DRIVE);

    private CloudProviders() { }

    public static List<CloudProvider> all() { return ALL; }

    public static CloudProvider get(String id) {
        for (CloudProvider p : ALL) if (p.id().equals(id)) return p;
        throw new IllegalArgumentException("Unknown cloud " + id);
    }
}
