package com.noam.photodream.describe;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * English → Hebrew on the phone (ML Kit Translation). The language model (~30 MB) is downloaded
 * once, on Wi-Fi only; if it isn't there yet and can't be fetched, texts simply stay English.
 */
public class HebrewTranslator implements AutoCloseable {

    private static final String TAG = "HebrewTranslator";

    private final Translator translator = Translation.getClient(new TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.HEBREW)
            .build());
    private boolean ready, tried;

    /** Blocking. True if the model is on the phone (downloading it on Wi-Fi if needed). */
    public boolean prepare(long timeoutSeconds) {
        if (tried) return ready;
        tried = true;
        try {
            Tasks.await(translator.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build()),
                    timeoutSeconds, TimeUnit.SECONDS);
            ready = true;
        } catch (Exception e) {
            Log.i(TAG, "Hebrew model not available now: " + e);
            ready = false;
        }
        return ready;
    }

    /** Blocking. The Hebrew text, or the original if translating failed. */
    public String translate(String english) {
        if (!ready || english == null || english.isEmpty()) return english;
        try {
            return Tasks.await(translator.translate(english), 20, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.i(TAG, "Translation failed: " + e);
            return english;
        }
    }

    public List<String> translateAll(List<String> english) {
        List<String> out = new ArrayList<>();
        for (String s : english) out.add(translate(s));
        return out;
    }

    @Override
    public void close() { translator.close(); }
}
