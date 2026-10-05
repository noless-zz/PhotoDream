package com.noam.photodream.describe;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * One way of describing a photo (ML Kit labels, Gemini Nano sentences, a fake in tests).
 * Blocking: call from a background thread.
 */
public interface PhotoDescriber {

    /** "labels" or "genai". */
    String engine();

    /** Can this phone run it right now (model present, feature supported)? */
    boolean isAvailable(Context context);

    /**
     * True if Android only lets this engine run while PhotoDream is the foreground app
     * (Gemini Nano). Such engines are skipped by the background job.
     */
    boolean needsForeground();

    /** What this engine adds to a description; null if it could not describe the photo. */
    Result describe(Context context, Bitmap bitmap) throws Exception;

    /** Output of one engine: labels, or a sentence (both in English). */
    final class Result {
        public final java.util.List<String> labels;
        public final String sentence;

        public Result(java.util.List<String> labels, String sentence) {
            this.labels = labels;
            this.sentence = sentence;
        }
    }
}
