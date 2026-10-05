package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class FrameColorsTest {

    @Test
    public void defaultIsClassicWhite() {
        assertEquals("white", FrameColors.DEFAULT);
        assertEquals(0xFFFFFFFF, FrameColors.argb(FrameColors.DEFAULT));
        assertEquals(FrameColors.DEFAULT, FrameColors.ids()[0]);
    }

    @Test
    public void unknownIdsFallBackToDefault() {
        assertEquals("white", FrameColors.normalize(null));
        assertEquals("white", FrameColors.normalize("hotpink"));
        assertEquals("teal", FrameColors.normalize("teal"));
        assertEquals(0xFFFFFFFF, FrameColors.argb("nonsense"));
    }

    @Test
    public void everyColorIsDistinctAndOpaque() {
        Set<Integer> seen = new HashSet<>();
        for (String id : FrameColors.ids()) {
            int c = FrameColors.argb(id);
            assertEquals(0xFF, c >>> 24);
            assertTrue("duplicate color for " + id, seen.add(c));
        }
        assertEquals(9, seen.size());
        assertFalse(FrameColors.isKnown(""));
    }
}
