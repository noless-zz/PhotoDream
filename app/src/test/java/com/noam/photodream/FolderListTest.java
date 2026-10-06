package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FolderListTest {

    @Test
    public void legacySingleFolderBecomesTheList() {
        assertEquals(Arrays.asList("content://a"), FolderList.migrate(null, "content://a"));
    }

    @Test
    public void nothingStoredAndNoLegacyIsEmpty() {
        assertTrue(FolderList.migrate(null, null).isEmpty());
    }

    @Test
    public void savedListWinsOverLegacy() {
        // once the new list exists (even empty, after the user removed everything) the old key is ignored
        assertEquals(Arrays.asList("content://b"), FolderList.migrate(Arrays.asList("content://b"), "content://a"));
        assertTrue(FolderList.migrate(new ArrayList<>(), "content://a").isEmpty());
    }

    @Test
    public void duplicatesAreIgnored() {
        List<String> l = new ArrayList<>();
        assertTrue(FolderList.add(l, "content://a"));
        assertFalse(FolderList.add(l, "content://a"));
        assertFalse(FolderList.add(l, ""));
        assertFalse(FolderList.add(l, null));
        assertEquals(1, l.size());
        assertEquals(2, FolderList.migrate(Arrays.asList("x", "x", "y", "x"), null).size());
    }

    @Test
    public void removeReportsWhetherItWasThere() {
        List<String> l = new ArrayList<>(Arrays.asList("a", "b"));
        assertTrue(FolderList.remove(l, "a"));
        assertFalse(FolderList.remove(l, "a"));
        assertEquals(Arrays.asList("b"), l);
    }
}
