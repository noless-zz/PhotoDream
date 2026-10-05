package com.noam.photodream.alarm.challenge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Random;

public class CardGridTest {

    private static boolean overlap(CardGrid.Slot a, CardGrid.Slot b) {
        return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
    }

    private static void check(int count, float w, float h, float aspect, float jitter, long seed) {
        List<CardGrid.Slot> slots = CardGrid.layout(count, w, h, aspect, jitter, new Random(seed));
        assertEquals(count, slots.size());
        for (CardGrid.Slot s : slots) {
            assertTrue("inside left/top", s.x >= -0.01f && s.y >= -0.01f);
            assertTrue("inside right/bottom", s.x + s.w <= w + 0.01f && s.y + s.h <= h + 0.01f);
            assertEquals(aspect, s.w / s.h, 0.001f);
        }
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                assertTrue("cards " + i + " and " + j + " overlap", !overlap(slots.get(i), slots.get(j)));
            }
        }
    }

    @Test
    public void easyMediumHardOnAPortraitPhone() {
        for (int count : new int[]{6, 9, 12}) {
            for (long seed = 0; seed < 20; seed++) check(count, 1080, 1500, 0.75f, 1f, seed);
        }
    }

    @Test
    public void worksOnASmallScreenAndOnLandscape() {
        check(12, 720, 900, 0.75f, 1f, 3);
        check(9, 1600, 700, 0.75f, 1f, 4);
        check(8, 800, 800, 1.33f, 1f, 5);       // landscape cards
    }

    @Test
    public void shortLastRowDoesNotOverlap() {
        check(7, 1000, 1200, 0.75f, 1f, 9);
        check(5, 1000, 1200, 0.75f, 1f, 10);
        check(1, 1000, 1200, 0.75f, 1f, 11);
    }

    @Test
    public void sameSeedGivesSameLayout() {
        List<CardGrid.Slot> a = CardGrid.layout(9, 1000, 1000, 0.75f, 1f, new Random(5));
        List<CardGrid.Slot> b = CardGrid.layout(9, 1000, 1000, 0.75f, 1f, new Random(5));
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).x, b.get(i).x, 0f);
    }

    @Test
    public void noJitterCentresEachCardInItsCell() {
        List<CardGrid.Slot> s = CardGrid.layout(4, 1000, 1000, 1f, 0f, new Random(1));
        // 2x2 grid, cells of 500: the card centre is the cell centre
        assertEquals(250f, s.get(0).x + s.get(0).w / 2f, 0.01f);
        assertEquals(250f, s.get(0).y + s.get(0).h / 2f, 0.01f);
    }

    @Test
    public void degenerateInputGivesNoSlots() {
        assertTrue(CardGrid.layout(0, 100, 100, 1f, 1f, new Random()).isEmpty());
        assertTrue(CardGrid.layout(3, 0, 100, 1f, 1f, new Random()).isEmpty());
    }
}
