package com.noam.photodream.describe.cloud;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Google Gemini API (generateContent) with inline image data. The key goes in the
 * x-goog-api-key header, not in the URL, so it cannot end up in a logged address.
 */
public final class GeminiProvider implements CloudDescriptionProvider {

    @Override public String id() { return "gemini"; }
    @Override public String displayName() { return "Gemini (Google)"; }
    @Override public String defaultModel() { return "gemini-2.0-flash"; }
    @Override public String keyHelpUrl() { return "aistudio.google.com"; }

    @Override
    public Request buildRequest(String apiKey, String model, byte[] jpeg, String lang) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-goog-api-key", apiKey);
        h.put("content-type", "application/json");
        String body = "{\"contents\":[{\"parts\":["
                + "{\"text\":" + MiniJson.quote(Prompts.forLanguage(lang)) + "},"
                + "{\"inline_data\":{\"mime_type\":\"image/jpeg\",\"data\":"
                + MiniJson.quote(java.util.Base64.getEncoder().encodeToString(jpeg)) + "}}]}],"
                + "\"generationConfig\":{\"maxOutputTokens\":120}}";
        return new Request("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent", h, body);
    }

    @Override
    public String parseSentence(String responseBody) {
        try {
            Object root = MiniJson.parse(responseBody);
            return Prompts.clean(MiniJson.getString(root, "candidates", 0, "content", "parts", 0, "text"));
        } catch (RuntimeException e) {
            return "";
        }
    }
}
