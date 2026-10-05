package com.noam.photodream.describe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a photo shows, in words: a few labels ("dog", "beach") from every phone, and a sentence
 * where the phone has Gemini Nano. Pure value class, unit-tested.
 */
public final class Description {

    /** One-sentence description, or "" when only labels are known. */
    public final String text;
    public final List<String> labels;
    /** Language of {@link #text} and {@link #labels}: "en" or "he". */
    public final String lang;
    /** Which engine produced it: "labels" or "genai". */
    public final String engine;

    public Description(String text, List<String> labels, String lang, String engine) {
        this.text = text == null ? "" : text;
        this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
        this.lang = lang;
        this.engine = engine;
    }

    public boolean hasSentence() { return !text.isEmpty(); }

    /** The same description with a sentence added (labels kept). */
    public Description withSentence(String sentence, String sentenceLang, String sentenceEngine) {
        return new Description(sentence, labels, sentenceLang, sentenceEngine);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("text", text);
        m.put("labels", new ArrayList<>(labels));
        m.put("lang", lang);
        m.put("engine", engine);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Description fromMap(Map<String, ?> m) {
        List<String> labels = new ArrayList<>();
        Object l = m.get("labels");
        if (l instanceof List) for (Object o : (List<Object>) l) if (o instanceof String) labels.add((String) o);
        Object text = m.get("text"), lang = m.get("lang"), engine = m.get("engine");
        return new Description(text instanceof String ? (String) text : "", labels,
                lang instanceof String ? (String) lang : "en", engine instanceof String ? (String) engine : "labels");
    }
}
