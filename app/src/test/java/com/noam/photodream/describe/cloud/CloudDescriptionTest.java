package com.noam.photodream.describe.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class CloudDescriptionTest {

    private static final byte[] JPEG = new byte[]{1, 2, 3, 4};

    // ---------------------------------------------------------------- MiniJson

    @Test
    public void parsesNestedJsonAndEscapes() {
        Object root = MiniJson.parse("{\"a\":[{\"b\":\"x\\n\\\"y\\\" \\u05e9\"}],\"n\":1.5,\"t\":true,\"z\":null}");
        assertEquals("x\n\"y\" \u05e9", MiniJson.getString(root, "a", 0, "b"));
        assertEquals(1.5, (Double) MiniJson.get(root, "n"), 0.0);
        assertEquals(Boolean.TRUE, MiniJson.get(root, "t"));
        assertNull(MiniJson.get(root, "z"));
        assertNull(MiniJson.get(root, "a", 3, "b"));
        assertNull(MiniJson.getString(root, "n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void brokenJsonThrows() {
        MiniJson.parse("{\"a\":");
    }

    @Test
    public void quoteRoundTrips() {
        String nasty = "he said \"hi\"\n\\ \u0001 \u05e9\u05dc\u05d5\u05dd";
        assertEquals(nasty, MiniJson.parse(MiniJson.quote(nasty)));
    }

    // ---------------------------------------------------------------- Claude

    @Test
    public void claudeRequestHasKeyHeaderImageAndPrompt() {
        CloudDescriptionProvider.Request r = new ClaudeProvider().buildRequest("sk-ant-1", "claude-haiku-4-5-20251001", JPEG, "en");
        assertEquals("https://api.anthropic.com/v1/messages", r.url);
        assertEquals("sk-ant-1", r.headers.get("x-api-key"));
        assertTrue(r.headers.containsKey("anthropic-version"));
        assertFalse(r.url.contains("sk-ant-1"));
        Object body = MiniJson.parse(r.body);                              // valid JSON
        assertEquals("claude-haiku-4-5-20251001", MiniJson.getString(body, "model"));
        assertEquals("image/jpeg", MiniJson.getString(body, "messages", 0, "content", 0, "source", "media_type"));
        assertEquals("AQIDBA==", MiniJson.getString(body, "messages", 0, "content", 0, "source", "data"));
        assertTrue(MiniJson.getString(body, "messages", 0, "content", 1, "text").contains("one short"));
    }

    @Test
    public void claudeParsesAnswer() {
        String ok = "{\"content\":[{\"type\":\"text\",\"text\":\"  A dog on a beach. \"}]}";
        assertEquals("A dog on a beach.", new ClaudeProvider().parseSentence(ok));
        assertEquals("", new ClaudeProvider().parseSentence("{\"content\":[]}"));
        assertEquals("", new ClaudeProvider().parseSentence("{\"type\":\"error\",\"error\":{\"message\":\"bad\"}}"));
        assertEquals("", new ClaudeProvider().parseSentence("not json"));
    }

    // ---------------------------------------------------------------- OpenAI

    @Test
    public void openAiRequestUsesBearerAndDataUrl() {
        CloudDescriptionProvider.Request r = new OpenAiProvider().buildRequest("sk-1", "gpt-4o-mini", JPEG, "he");
        assertEquals("Bearer sk-1", r.headers.get("Authorization"));
        Object body = MiniJson.parse(r.body);
        assertEquals("gpt-4o-mini", MiniJson.getString(body, "model"));
        assertEquals("data:image/jpeg;base64,AQIDBA==",
                MiniJson.getString(body, "messages", 0, "content", 1, "image_url", "url"));
        assertTrue(MiniJson.getString(body, "messages", 0, "content", 0, "text").contains("בעברית"));
    }

    @Test
    public void openAiParsesAnswer() {
        String ok = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\\\"A red car.\\\"\"}}]}";
        assertEquals("A red car.", new OpenAiProvider().parseSentence(ok));
        assertEquals("", new OpenAiProvider().parseSentence("{\"choices\":[]}"));
        assertEquals("", new OpenAiProvider().parseSentence("{\"error\":{\"message\":\"Incorrect API key\"}}"));
    }

    // ---------------------------------------------------------------- Gemini

    @Test
    public void geminiKeyGoesInHeaderNotUrl() {
        CloudDescriptionProvider.Request r = new GeminiProvider().buildRequest("AIza-secret", "gemini-2.0-flash", JPEG, "en");
        assertEquals("AIza-secret", r.headers.get("x-goog-api-key"));
        assertFalse(r.url.contains("AIza-secret"));
        assertFalse(r.url.contains("key="));
        assertTrue(r.url.endsWith("/models/gemini-2.0-flash:generateContent"));
        Object body = MiniJson.parse(r.body);
        assertEquals("AQIDBA==", MiniJson.getString(body, "contents", 0, "parts", 1, "inline_data", "data"));
    }

    @Test
    public void geminiParsesAnswer() {
        String ok = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Snow on a mountain.\\n\"}]}}]}";
        assertEquals("Snow on a mountain.", new GeminiProvider().parseSentence(ok));
        assertEquals("", new GeminiProvider().parseSentence("{\"candidates\":[]}"));
        assertEquals("", new GeminiProvider().parseSentence("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
    }

    // ---------------------------------------------------------------- registry

    @Test
    public void registryHasThreeServicesAndFallsBackToFirst() {
        assertEquals(3, CloudDescriptionProviders.all().size());
        assertEquals("openai", CloudDescriptionProviders.byId("openai").id());
        assertEquals("gemini", CloudDescriptionProviders.byId("gemini").id());
        assertSame(CloudDescriptionProviders.all().get(0), CloudDescriptionProviders.byId("nope"));
        for (CloudDescriptionProvider p : CloudDescriptionProviders.all()) {
            Map<String, String> h = p.buildRequest("k", p.defaultModel(), JPEG, "en").headers;
            assertFalse(h.isEmpty());
        }
    }

    // ---------------------------------------------------------------- daily cap

    @Test
    public void capCountsUpAndBlocksAtTheLimit() {
        DailyCap cap = new DailyCap("2026-10-07", "2026-10-07", 28, 30);
        assertTrue(cap.allows());
        assertEquals(2, cap.remaining());
        cap.record();
        cap.record();
        assertFalse(cap.allows());
        assertEquals(0, cap.remaining());
        assertEquals(30, cap.used());
    }

    @Test
    public void capResetsOnANewDay() {
        DailyCap cap = new DailyCap("2026-10-08", "2026-10-07", 30, 30);
        assertTrue(cap.allows());
        assertEquals(0, cap.used());
        assertEquals("2026-10-08", cap.day());
    }

    @Test
    public void capLimitIsClampedAndZeroBlocksEverything() {
        assertEquals(0, DailyCap.clampLimit(-5));
        assertEquals(DailyCap.MAX_LIMIT, DailyCap.clampLimit(1_000_000));
        assertFalse(new DailyCap("d", "d", 0, 0).allows());
    }
}
