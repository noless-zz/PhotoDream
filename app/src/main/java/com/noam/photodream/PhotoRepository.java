package com.noam.photodream;

import android.content.Context;
import android.net.Uri;

import com.noam.photodream.source.CacheFolderSource;
import com.noam.photodream.source.LocalFolderSource;
import com.noam.photodream.source.PhotoSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects photos from every enabled source. Call from a background thread. */
public class PhotoRepository {

    public static List<PhotoSource> enabledSources(Context context) {
        Prefs prefs = new Prefs(context);
        List<PhotoSource> sources = new ArrayList<>();
        if (prefs.getLocalFolder() != null) {
            sources.add(new LocalFolderSource(prefs.getLocalFolder()));
        }
        sources.add(new CacheFolderSource());
        // TODO next: add OneDrive / Google Drive here (they fill the cache folder)
        return sources;
    }

    public static List<Uri> loadAll(Context context) {
        List<Uri> all = new ArrayList<>();
        for (PhotoSource s : enabledSources(context)) {
            all.addAll(s.listPhotos(context));
        }
        if (new Prefs(context).isShuffle()) {
            Collections.shuffle(all);
        }
        return all;
    }
}
