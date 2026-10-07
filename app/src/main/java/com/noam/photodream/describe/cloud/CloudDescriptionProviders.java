package com.noam.photodream.describe.cloud;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The AI services the user can choose from. A new one = a new class + one line here. */
public final class CloudDescriptionProviders {

    private static final List<CloudDescriptionProvider> ALL = Collections.unmodifiableList(
            Arrays.<CloudDescriptionProvider>asList(new ClaudeProvider(), new OpenAiProvider(), new GeminiProvider()));

    private CloudDescriptionProviders() { }

    public static List<CloudDescriptionProvider> all() { return ALL; }

    /** The provider with this id, or the first one if the id is unknown/empty. */
    public static CloudDescriptionProvider byId(String id) {
        for (CloudDescriptionProvider p : ALL) if (p.id().equals(id)) return p;
        return ALL.get(0);
    }
}
