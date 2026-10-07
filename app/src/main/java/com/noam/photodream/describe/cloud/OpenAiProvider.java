package com.noam.photodream.describe.cloud;

import java.util.LinkedHashMap;
import java.util.Map;

/** OpenAI Chat Completions (ChatGPT) with the image given as a data URL. */
public final class OpenAiProvider implements CloudDescriptionProvider {

    @Override public String id() { return "openai"; }
    @Override public String displayName() { return "ChatGPT (OpenAI)"; }
    @Override public String defaultModel() { return "gpt-4o-mini"; }
    @Override public String keyHelpUrl() { return "platform.openai.com"; }

    @Override
    public Request buildRequest(String apiKey, String model, byte[] jpeg, String lang) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer " + apiKey);
        h.put("content-type", "application/json");
        String dataUrl = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(jpeg);
        String body = "{\"model\":" + MiniJson.quote(model)
                + ",\"max_tokens\":120,\"messages\":[{\"role\":\"user\",\"content\":["
                + "{\"type\":\"text\",\"text\":" + MiniJson.quote(Prompts.forLanguage(lang)) + "},"
                + "{\"type\":\"image_url\",\"image_url\":{\"url\":" + MiniJson.quote(dataUrl) + ",\"detail\":\"low\"}}]}]}";
        return new Request("https://api.openai.com/v1/chat/completions", h, body);
    }

    @Override
    public String parseSentence(String responseBody) {
        try {
            Object root = MiniJson.parse(responseBody);
            return Prompts.clean(MiniJson.getString(root, "choices", 0, "message", "content"));
        } catch (RuntimeException e) {
            return "";
        }
    }
}
