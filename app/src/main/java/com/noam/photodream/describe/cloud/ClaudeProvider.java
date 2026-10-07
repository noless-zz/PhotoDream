package com.noam.photodream.describe.cloud;

import java.util.LinkedHashMap;
import java.util.Map;

/** Anthropic Messages API (Claude) with an inline base64 image. */
public final class ClaudeProvider implements CloudDescriptionProvider {

    @Override public String id() { return "claude"; }
    @Override public String displayName() { return "Claude (Anthropic)"; }
    @Override public String defaultModel() { return "claude-haiku-4-5-20251001"; }
    @Override public String keyHelpUrl() { return "console.anthropic.com"; }

    @Override
    public Request buildRequest(String apiKey, String model, byte[] jpeg, String lang) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-api-key", apiKey);
        h.put("anthropic-version", "2023-06-01");
        h.put("content-type", "application/json");
        String body = "{\"model\":" + MiniJson.quote(model)
                + ",\"max_tokens\":120,\"messages\":[{\"role\":\"user\",\"content\":["
                + "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/jpeg\",\"data\":"
                + MiniJson.quote(java.util.Base64.getEncoder().encodeToString(jpeg)) + "}},"
                + "{\"type\":\"text\",\"text\":" + MiniJson.quote(Prompts.forLanguage(lang)) + "}]}]}";
        return new Request("https://api.anthropic.com/v1/messages", h, body);
    }

    @Override
    public String parseSentence(String responseBody) {
        try {
            Object root = MiniJson.parse(responseBody);
            return Prompts.clean(MiniJson.getString(root, "content", 0, "text"));
        } catch (RuntimeException e) {
            return "";
        }
    }
}
