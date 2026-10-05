package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.noam.photodream.source.PhotoSource;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PhotoRepositoryTest {

    /** Fake source: the Uri is null because android.net.Uri isn't available in JVM tests. */
    private static class FakeSource implements PhotoSource {
        private final String id;
        private final int count;

        FakeSource(String id, int count) {
            this.id = id;
            this.count = count;
        }

        @Override public String id() { return id; }
        @Override public String getName() { return id; }

        @Override
        public List<Photo> listPhotos(Context context) {
            List<Photo> out = new ArrayList<>();
            for (int i = 0; i < count; i++) out.add(new Photo(null, id, "p" + i + ".jpg"));
            return out;
        }
    }

    @Test
    public void keyIsSourceIdPlusName() {
        assertEquals("onedrive:a.jpg", new Photo(null, "onedrive", "a.jpg").key());
    }

    @Test
    public void keyIgnoresUri_andDiffersBetweenSources() {
        Photo a = new Photo(null, "onedrive", "a.jpg");
        Photo b = new Photo(null, "gdrive", "a.jpg");
        assertEquals(a, new Photo(null, "onedrive", "a.jpg"));
        assertNotEquals(a, b);
        assertEquals(a.key(), new Photo(null, "onedrive", "a.jpg").key());
    }

    @Test
    public void mergesSourcesAndKeepsSourceIds() {
        List<PhotoSource> sources = Arrays.asList(new FakeSource("local", 2), new FakeSource("gdrive", 3));
        List<Photo> all = PhotoRepository.load(null, sources, false, new Random(1));
        assertEquals(5, all.size());
        assertEquals("local", all.get(0).sourceId);
        assertEquals("gdrive", all.get(4).sourceId);
    }

    @Test
    public void shuffleKeepsEveryPhoto() {
        List<PhotoSource> sources = Arrays.asList(new FakeSource("local", 20), new FakeSource("onedrive", 20));
        List<Photo> plain = PhotoRepository.load(null, sources, false, new Random(1));
        List<Photo> shuffled = PhotoRepository.load(null, sources, true, new Random(1));
        assertEquals(plain.size(), shuffled.size());
        Set<String> keys = new HashSet<>();
        for (Photo p : shuffled) keys.add(p.key());
        assertEquals(40, keys.size());
        assertTrue(keys.containsAll(new HashSet<>(Arrays.asList("local:p0.jpg", "onedrive:p19.jpg"))));
        assertNotEquals(plain, shuffled);
    }
}
