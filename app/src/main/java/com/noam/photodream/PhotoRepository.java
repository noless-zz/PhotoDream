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
import java.util.function.Predicate;

/** Collects photos from every enabled source. Call from a background thread. */
public class PhotoRepository {

    /** Every source that exists, shown or hidden: the phone folder (if chosen) plus one cache per cloud. */
    public static List<PhotoSource> allSources(Context context) {
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

    /** Only the sources the user left switched on in settings. */
    public static List<PhotoSource> enabledSources(Context context) {
        Prefs prefs = new Prefs(context);
        return filterEnabled(allSources(context), prefs::isSourceEnabled);
    }

    /** Keeps the sources whose id the predicate accepts. Pure, so it is unit-tested. */
    public static List<PhotoSource> filterEnabled(List<PhotoSource> sources, Predicate<String> isEnabled) {
        List<PhotoSource> out = new ArrayList<>();
        for (PhotoSource s : sources) {
            if (isEnabled.test(s.id())) out.add(s);
        }
        return out;
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
