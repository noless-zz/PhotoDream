package com.noam.photodream.cloud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Remembers cloud photos that Android could not decode (some HEIC/RAW files),
 * so every sync doesn't download them again. Pure Java, unit-tested.
 * Only decode failures count – a network error says nothing about the file.
 */
public final class DecodeFailures {

    /** After this many failed decodes the photo is given up on. */
    public static final int MAX_ATTEMPTS = 2;

    private final Map<String, Integer> attempts = new HashMap<>();

    public DecodeFailures() { }

    public DecodeFailures(Map<String, Integer> saved) {
        attempts.putAll(saved);
    }

    public void recordDecodeFailure(String id) {
        attempts.merge(id, 1, Integer::sum);
    }

    /** A photo that decoded fine is not a problem (any more). */
    public void recordSuccess(String id) {
        attempts.remove(id);
    }

    /** Ids to leave out of the sync plan. */
    public Set<String> excluded() {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Integer> e : attempts.entrySet()) {
            if (e.getValue() >= MAX_ATTEMPTS) out.add(e.getKey());
        }
        return out;
    }

    /** Raw counts, for saving. */
    public Map<String, Integer> asMap() {
        return new HashMap<>(attempts);
    }

    /** Forget ids that are no longer in the cloud folder, so the file doesn't grow forever. */
    public void retainOnly(Set<String> remoteIds) {
        attempts.keySet().retainAll(remoteIds);
    }
}
