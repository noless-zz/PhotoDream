package com.noam.photodream.describe.cloud;

import java.util.Map;

/**
 * One AI service that can describe a photo (Claude, ChatGPT, Gemini). Pure Java: it only builds
 * the HTTP request and reads the answer, so both halves are unit-tested with sample JSON.
 * The network call itself lives in {@link CloudDescriber}.
 */
public interface CloudDescriptionProvider {

    /** Short stable id stored in settings: "claude", "openai", "gemini". */
    String id();

    /** Name shown to the user. */
    String displayName();

    /** Small, cheap, vision-capable model used unless the user types another one. */
    String defaultModel();

    /** Where the key is created, shown as a hint in the setup dialog. */
    String keyHelpUrl();

    /** Everything needed to POST one request. */
    final class Request {
        public final String url;
        public final Map<String, String> headers;
        public final String body;

        public Request(String url, Map<String, String> headers, String body) {
            this.url = url;
            this.headers = headers;
            this.body = body;
        }
    }

    /** @param lang "en" or "he" – the language the sentence should be written in */
    Request buildRequest(String apiKey, String model, byte[] jpeg, String lang);

    /** The sentence in an answer body, trimmed; "" if the answer holds no text. Never throws. */
    String parseSentence(String responseBody);
}
