package com.noam.photodream.describe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class DescribeTest {

    // ---------------------------------------------------------------- Description / cache

    @Test
    public void descriptionSurvivesTheMapRoundTrip() {
        Description d = new Description("A dog on the beach", Arrays.asList("dog", "beach"), "en", "genai");
        Description back = Description.fromMap(d.toMap());
        assertEquals("A dog on the beach", back.text);
        assertEquals(Arrays.asList("dog", "beach"), back.labels);
        assertEquals("en", back.lang);
        assertEquals("genai", back.engine);
        assertTrue(back.hasSentence());
    }

    @Test
    public void labelsOnlyHasNoSentence() {
        Description d = new Description(null, Arrays.asList("cat"), "en", "labels");
        assertFalse(d.hasSentence());
        Description withText = d.withSentence("A cat on a sofa", "en", "genai");
        assertTrue(withText.hasSentence());
        assertEquals(Arrays.asList("cat"), withText.labels);
    }

    @Test
    public void brokenMapGivesAnEmptyEnglishDescription() {
        Description d = Description.fromMap(new HashMap<String, Object>());
        assertEquals("", d.text);
        assertTrue(d.labels.isEmpty());
        assertEquals("en", d.lang);
    }

    @Test
    public void cacheRoundTripsThroughItsMap() {
        DescriptionCache c = new DescriptionCache();
        c.put("local:a", new Description("", Arrays.asList("tree"), "en", "labels"));
        c.put("onedrive:b", new Description("A red balloon", Arrays.asList("balloon"), "he", "genai"));
        DescriptionCache back = DescriptionCache.fromMap(c.toMap());
        assertEquals(2, back.size());
        assertEquals("A red balloon", back.get("onedrive:b").text);
        assertEquals("he", back.get("onedrive:b").lang);
    }

    @Test
    public void cleanupRemovesEntriesOfVanishedPhotos() {
        DescriptionCache c = new DescriptionCache();
        for (String k : new String[]{"a", "b", "c", "d"}) c.put(k, new Description("", new ArrayList<String>(), "en", "labels"));
        int removed = c.retainOnly(new HashSet<>(Arrays.asList("a", "c")));
        assertEquals(2, removed);
        assertEquals(2, c.size());
        assertTrue(c.has("a") && c.has("c"));
        assertFalse(c.has("b"));
    }

    @Test
    public void countDescribedCanRequireASentence() {
        DescriptionCache c = new DescriptionCache();
        c.put("a", new Description("", Arrays.asList("x"), "en", "labels"));
        c.put("b", new Description("A sentence", Arrays.asList("y"), "en", "genai"));
        List<String> keys = Arrays.asList("a", "b", "c");
        assertEquals(2, c.countDescribed(keys, false));
        assertEquals(1, c.countDescribed(keys, true));
    }

    // ---------------------------------------------------------------- labels

    @Test
    public void labelsBelowSixtyPercentAreDropped() {
        List<String> out = LabelFilter.pick(Arrays.asList("dog", "grass", "blur"), Arrays.asList(0.9f, 0.6f, 0.59f));
        assertEquals(Arrays.asList("dog", "grass"), out);
    }

    @Test
    public void bestFirstAndAtMostFive() {
        List<String> texts = Arrays.asList("a", "b", "c", "d", "e", "f", "g");
        List<Float> conf = Arrays.asList(0.61f, 0.99f, 0.7f, 0.8f, 0.65f, 0.9f, 0.75f);
        assertEquals(Arrays.asList("b", "f", "d", "g", "c"), LabelFilter.pick(texts, conf));
    }

    @Test
    public void labelsAreTrimmedLowercasedAndDeduplicated() {
        List<String> out = LabelFilter.pick(Arrays.asList(" Dog ", "dog", "", "Sky"), Arrays.asList(0.9f, 0.8f, 0.95f, 0.7f));
        assertEquals(Arrays.asList("dog", "sky"), out);
    }

    // ---------------------------------------------------------------- selecting describers (with fakes)

    private static final class Fake implements PhotoDescriber {
        private final String engine;
        private final boolean available, foregroundOnly;

        Fake(String engine, boolean available, boolean foregroundOnly) {
            this.engine = engine;
            this.available = available;
            this.foregroundOnly = foregroundOnly;
        }

        @Override public String engine() { return engine; }
        @Override public boolean isAvailable(Context c) { return available; }
        @Override public boolean needsForeground() { return foregroundOnly; }
        @Override public Result describe(Context c, Bitmap b) { return new Result(new ArrayList<String>(), ""); }
    }

    private static List<String> engines(List<PhotoDescriber> l) {
        List<String> out = new ArrayList<>();
        for (PhotoDescriber d : l) out.add(d.engine());
        return out;
    }

    @Test
    public void phoneWithoutGeminiNanoUsesLabelsOnly() {
        List<PhotoDescriber> all = Arrays.<PhotoDescriber>asList(new Fake("genai", false, true), new Fake("labels", true, false));
        assertEquals(Arrays.asList("labels"), engines(DescriberSelector.select(null, all, true)));
        assertFalse(DescriberSelector.sentencesPossible(null, all));
    }

    @Test
    public void backgroundJobSkipsForegroundOnlyEngines() {
        List<PhotoDescriber> all = Arrays.<PhotoDescriber>asList(new Fake("genai", true, true), new Fake("labels", true, false));
        assertEquals(Arrays.asList("labels"), engines(DescriberSelector.select(null, all, false)));
        assertTrue(DescriberSelector.sentencesPossible(null, all));
    }

    @Test
    public void inTheForegroundBothRunAndLabelsComeFirst() {
        List<PhotoDescriber> all = Arrays.<PhotoDescriber>asList(new Fake("genai", true, true), new Fake("labels", true, false));
        assertEquals(Arrays.asList("labels", "genai"), engines(DescriberSelector.select(null, all, true)));
    }

    @Test
    public void nothingAvailableMeansNothingToRun() {
        List<PhotoDescriber> all = Arrays.<PhotoDescriber>asList(new Fake("genai", false, true), new Fake("labels", false, false));
        assertTrue(DescriberSelector.select(null, all, true).isEmpty());
    }

    @Test
    public void mapsAreIndependentCopies() {
        Description d = new Description("x", Arrays.asList("a"), "en", "labels");
        Map<String, Object> m = d.toMap();
        m.put("text", "changed");
        assertEquals("x", d.text);
    }
}
