package com.noam.photodream.describe;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Descriptions by {@code Photo.key()}. Pure Java: the store saves it as JSON (files/descriptions.json).
 * Entries are removed when their photo disappears, so the file never grows without limit.
 */
public final class DescriptionCache {

    private final Map<String, Description> entries = new LinkedHashMap<>();

    public synchronized Description get(String key) { return entries.get(key); }

    public synchronized void put(String key, Description d) { entries.put(key, d); }

    public synchronized boolean has(String key) { return entries.containsKey(key); }

    public synchronized int size() { return entries.size(); }

    /** Drop every entry whose photo is gone. Returns how many were removed. */
    public synchronized int retainOnly(Set<String> keysThatStillExist) {
        int before = entries.size();
        entries.keySet().retainAll(keysThatStillExist);
        return before - entries.size();
    }

    /** How many of these photos already have a description (a sentence if {@code needSentence}). */
    public synchronized int countDescribed(Iterable<String> keys, boolean needSentence) {
        int n = 0;
        for (String k : keys) {
            Description d = entries.get(k);
            if (d != null && (!needSentence || d.hasSentence())) n++;
        }
        return n;
    }

    /** For saving: key → description map. */
    public synchronized Map<String, Map<String, Object>> toMap() {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (Map.Entry<String, Description> e : entries.entrySet()) out.put(e.getKey(), e.getValue().toMap());
        return out;
    }

    public static DescriptionCache fromMap(Map<String, ? extends Map<String, ?>> saved) {
        DescriptionCache c = new DescriptionCache();
        for (Map.Entry<String, ? extends Map<String, ?>> e : saved.entrySet()) c.entries.put(e.getKey(), Description.fromMap(e.getValue()));
        return c;
    }
}
