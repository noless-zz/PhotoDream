package com.noam.photodream.describe;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.noam.photodream.BitmapLoader;
import com.noam.photodream.Photo;
import com.noam.photodream.PhotoMarksStore;
import com.noam.photodream.PhotoRepository;
import com.noam.photodream.Prefs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Describes photos that have no description yet and keeps the cache tidy.
 * The background job ({@code foreground = false}) writes labels only; the "Prepare descriptions"
 * button and the alarm screen run it in the foreground, where Gemini Nano sentences are allowed too.
 * No network is used except ML Kit's own model downloads (Hebrew, Gemini Nano).
 */
public final class DescriptionJob {

    private static final String TAG = "DescriptionJob";
    private static final int DESCRIBE_LONG_SIDE = 640;

    public interface Progress { void onProgress(int done, int total); }

    /** What a run did. */
    public static final class Outcome {
        public int total;          // photos that may be described
        public int processed;      // photos handled in this run
        public int remaining;      // photos still without (full) description
    }

    private DescriptionJob() { }

    /**
     * Blocking. @param maxPhotos stop after this many photos (keeps background runs short)
     * @param cancel  set to true to stop early
     */
    public static Outcome run(Context context, boolean foreground, int maxPhotos, AtomicBoolean cancel, Progress progress) {
        Context app = context.getApplicationContext();
        DescriptionStore store = DescriptionStore.get(app);
        DescriptionCache cache = store.cache();
        List<Photo> photos = PhotoMarksStore.get(app).marks().visible(PhotoRepository.loadAll(app));

        Set<String> keys = new HashSet<>();
        for (Photo p : photos) keys.add(p.key());
        cache.retainOnly(keys);                                   // entries of vanished photos go

        LabelDescriber labels = new LabelDescriber();
        GenAiDescriber genAi = new GenAiDescriber();
        List<PhotoDescriber> usable = DescriberSelector.select(app, java.util.Arrays.<PhotoDescriber>asList(labels, genAi), foreground);
        boolean useLabels = false, useSentences = false;
        for (PhotoDescriber d : usable) {
            if ("labels".equals(d.engine())) useLabels = true;
            if ("genai".equals(d.engine())) useSentences = true;
        }

        Outcome out = new Outcome();
        out.total = photos.size();
        List<Photo> todo = new ArrayList<>();
        for (Photo p : photos) {
            Description d = cache.get(p.key());
            if ((useLabels && d == null) || (useSentences && (d == null || !d.hasSentence()))) todo.add(p);
        }

        HebrewTranslator hebrew = null;
        try {
            if (new Prefs(app).isDescribeHebrew() && !todo.isEmpty()) {
                hebrew = new HebrewTranslator();
                hebrew.prepare(foreground ? 120 : 20);
            }
            for (Photo p : todo) {
                if (out.processed >= maxPhotos || (cancel != null && cancel.get())) break;
                describeOne(app, p, cache, useLabels, useSentences, labels, genAi, hebrew);
                out.processed++;
                if (progress != null) progress.onProgress(out.processed, Math.min(todo.size(), maxPhotos));
                if (out.processed % 10 == 0) store.save();
            }
        } finally {
            if (hebrew != null) hebrew.close();
            labels.close();
            genAi.close();
            store.save();
        }
        out.remaining = Math.max(0, todo.size() - out.processed);
        return out;
    }

    private static void describeOne(Context ctx, Photo photo, DescriptionCache cache, boolean useLabels, boolean useSentences,
                                    LabelDescriber labels, GenAiDescriber genAi, HebrewTranslator hebrew) {
        Bitmap hw = BitmapLoader.load(ctx, photo.uri, DESCRIBE_LONG_SIDE);
        if (hw == null) return;
        Bitmap soft = null;
        try {
            soft = hw.copy(Bitmap.Config.ARGB_8888, false);       // decoded bitmaps can be hardware ones, which ML Kit can't read
            if (soft == null) return;
            Description d = cache.get(photo.key());
            List<String> labelList = d == null ? new ArrayList<String>() : new ArrayList<>(d.labels);
            String sentence = d == null ? "" : d.text;
            String lang = d == null ? "en" : d.lang;
            boolean changed = false;

            if (useLabels && d == null) {
                labelList = labels.describe(ctx, soft).labels;
                changed = true;
            }
            if (useSentences && (d == null || !d.hasSentence())) {
                String s = genAi.describe(ctx, soft).sentence;
                if (!s.isEmpty()) {
                    sentence = s;
                    changed = true;
                }
            }
            if (!changed) return;

            boolean translate = hebrew != null && !"he".equals(lang);
            if (translate) {
                String t = hebrew.translate(sentence);
                List<String> tl = hebrew.translateAll(labelList);
                boolean worked = !sentence.isEmpty() ? !t.equals(sentence) : (labelList.isEmpty() || !tl.equals(labelList));
                if (worked) {
                    sentence = t;
                    labelList = tl;
                    lang = "he";
                }
            }
            cache.put(photo.key(), new Description(sentence, labelList, lang, sentence.isEmpty() ? "labels" : "genai"));
        } catch (Exception | LinkageError e) {
            Log.w(TAG, "Could not describe a photo", e);
        } finally {
            if (soft != null) soft.recycle();
        }
    }
}
