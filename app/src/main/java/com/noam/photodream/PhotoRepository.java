package com.noam.photodream;

import android.content.Context;

import com.noam.photodream.cloud.CloudProvider;
import com.noam.photodream.cloud.CloudProviders;
import com.noam.photodream.source.CacheFolderSource;
import com.noam.photodream.source.LocalFolderSource;
import com.noam.photodream.source.PhotoSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Collects photos from every enabled source. Call from a background thread. */
public class PhotoRepository {

    /** The phone folder (if chosen) plus one cache folder per cloud provider. */
    public static List<PhotoSource> enabledSources(Context context) {
        Prefs prefs = new Prefs(context);
        List<PhotoSource> sources = new ArrayList<>();
        if (prefs.getLocalFolder() != null) {
            sources.add(new LocalFolderSource(prefs.getLocalFolder()));
        }
        for (CloudProvider p : CloudProviders.all()) {
            sources.add(new CacheFolderSource(p.id()));
        }
        return sources;
    }

    public static List<Photo> loadAll(Context context) {
        return load(context, enabledSources(context), new Prefs(context).isShuffle(), new Random());
    }

    /** Testable core: merge the given sources, optionally shuffle with the given Random. */
    public static List<Photo> load(Context context, List<PhotoSource> sources, boolean shuffle, Random random) {
        List<Photo> all = new ArrayList<>();
        for (PhotoSource s : sources) {
            all.addAll(s.listPhotos(context));
        }
        if (shuffle) {
            Collections.shuffle(all, random);
        }
        return all;
    }
}
