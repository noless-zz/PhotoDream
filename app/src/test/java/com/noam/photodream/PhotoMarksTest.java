package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class PhotoMarksTest {

    private static Photo p(String source, String name) { return new Photo(null, source, name); }

    @Test
    public void favoriteToggles() {
        PhotoMarks m = new PhotoMarks();
        assertTrue(m.toggleFavorite("local:a"));
        assertTrue(m.isFavorite("local:a"));
        assertFalse(m.toggleFavorite("local:a"));
        assertFalse(m.isFavorite("local:a"));
    }

    @Test
    public void hidingRemovesTheHeartAndUndoBringsItBackAsNormal() {
        PhotoMarks m = new PhotoMarks();
        m.toggleFavorite("local:a");
        m.hide("local:a");
        assertTrue(m.isHidden("local:a"));
        assertFalse(m.isFavorite("local:a"));
        m.unhide("local:a");
        assertFalse(m.isHidden("local:a"));
        assertEquals(1.0, m.weight(p("local", "a")), 0.0);
    }

    @Test
    public void favoritingAHiddenPhotoUnhidesIt() {
        PhotoMarks m = new PhotoMarks();
        m.hide("local:a");
        m.toggleFavorite("local:a");
        assertFalse(m.isHidden("local:a"));
        assertTrue(m.isFavorite("local:a"));
    }

    @Test
    public void weights() {
        PhotoMarks m = new PhotoMarks(Arrays.asList("local:fav"), Arrays.asList("local:gone"));
        assertEquals(3.0, m.weight(p("local", "fav")), 0.0);
        assertEquals(0.0, m.weight(p("local", "gone")), 0.0);
        assertEquals(1.0, m.weight(p("local", "other")), 0.0);
    }

    @Test
    public void visibleDropsHiddenAndKeepsOrder() {
        PhotoMarks m = new PhotoMarks();
        m.hide("local:b");
        List<Photo> all = Arrays.asList(p("local", "a"), p("local", "b"), p("local", "c"));
        assertEquals(Arrays.asList(all.get(0), all.get(2)), m.visible(all));
    }

    @Test
    public void inconsistentLoadHidingWins() {
        PhotoMarks m = new PhotoMarks(Arrays.asList("x:1", "x:2"), Arrays.asList("x:2"));
        assertTrue(m.isFavorite("x:1"));
        assertFalse(m.isFavorite("x:2"));
        assertTrue(m.isHidden("x:2"));
    }

    @Test
    public void countsAndClearing() {
        PhotoMarks m = new PhotoMarks();
        m.toggleFavorite("a:1");
        m.toggleFavorite("a:2");
        m.hide("a:3");
        assertEquals(2, m.favoriteCount());
        assertEquals(1, m.hiddenCount());
        m.clearFavorites();
        assertEquals(0, m.favoriteCount());
        assertEquals(1, m.hiddenCount());
        m.clearHidden();
        assertEquals(0, m.hiddenCount());
    }

    @Test
    public void hasFavoriteIn() {
        PhotoMarks m = new PhotoMarks(Arrays.asList("local:a"), new ArrayList<>());
        assertTrue(m.hasFavoriteIn(Arrays.asList(p("local", "x"), p("local", "a"))));
        assertFalse(m.hasFavoriteIn(Arrays.asList(p("local", "x"))));
    }

    @Test
    public void favoriteNamesAreSplitPerSource() {
        PhotoMarks m = new PhotoMarks(Arrays.asList("onedrive:od_1.jpg", "onedrive:od_2.jpg", "gdrive:gd_9.jpg", "local:doc:5"), new ArrayList<>());
        assertEquals(new HashSet<>(Arrays.asList("od_1.jpg", "od_2.jpg")), m.favoriteNamesIn("onedrive"));
        assertEquals(new HashSet<>(Arrays.asList("gd_9.jpg")), m.favoriteNamesIn("gdrive"));
        assertEquals(new HashSet<>(Arrays.asList("doc:5")), m.favoriteNamesIn("local"));
    }
}
