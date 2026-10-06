package com.noam.photodream.source;

import android.content.Context;
import android.net.Uri;

import com.noam.photodream.Photo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Photos the sync job downloaded from one cloud into the app's private
 * storage: files/photo_cache/&lt;providerId&gt;/. One instance per provider, so
 * every photo knows which cloud it came from.
 */
public class CacheFolderSource implements PhotoSource {

    private final String providerId;

    public CacheFolderSource(String providerId) {
        this.providerId = providerId;
    }

    public static File cacheRoot(Context context) {
        return new File(context.getFilesDir(), "photo_cache");
    }

    @Override
    public String id() {
        return providerId;
    }

    @Override
    public String getName() {
        return "Downloaded photos (" + providerId + ")";
    }

    @Override
    public List<Photo> listPhotos(Context context) {
        List<Photo> out = new ArrayList<>();
        collect(new File(cacheRoot(context), providerId), out);
        return out;
    }

    private void collect(File dir, List<Photo> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collect(f, out);
            } else {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                        || n.endsWith(".webp") || n.endsWith(".heic")) {
                    out.add(new Photo(Uri.fromFile(f), providerId, f.getName()));
                }
            }
        }
    }
}
