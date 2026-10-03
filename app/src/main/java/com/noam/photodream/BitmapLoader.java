package com.noam.photodream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.Log;
import android.util.Size;

import java.io.IOException;

/**
 * Decodes a photo at roughly screen size (a 50MP photo at full size would
 * use ~200MB of memory). ImageDecoder also applies the EXIF rotation for us.
 */
public final class BitmapLoader {

    private static final String TAG = "BitmapLoader";

    private BitmapLoader() { }

    /** @return the bitmap, or null if the file could not be read. */
    public static Bitmap load(Context context, Uri uri, int targetLongSide) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(context.getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                Size size = info.getSize();
                int longSide = Math.max(size.getWidth(), size.getHeight());
                int sample = 1;
                while (longSide / (sample * 2) >= targetLongSide) {
                    sample *= 2;
                }
                decoder.setTargetSampleSize(sample);
            });
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Skipping unreadable photo " + uri, e);
            return null;
        }
    }
}
