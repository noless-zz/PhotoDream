package com.noam.photodream.source;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Photos stored inside the app's private storage (files/photo_cache/...).
 * This is where the future OneDrive / Google Drive sync will put downloads,
 * one sub-folder per cloud account.
 */
public class CacheFolderSource implements PhotoSource {

    public static File cacheRoot(Context context) {
        return new File(context.getFilesDir(), "photo_cache");
    }

    @Override
    public String getName() {
        return "Downloaded photos";
    }

    @Override
    public List<Uri> listPhotos(Context context) {
        List<Uri> out = new ArrayList<>();
        collect(cacheRoot(context), out);
        return out;
    }

    private void collect(File dir, List<Uri> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collect(f, out);
            } else {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                        || n.endsWith(".webp") || n.endsWith(".heic")) {
                    out.add(Uri.fromFile(f));
                }
            }
        }
    }
}
