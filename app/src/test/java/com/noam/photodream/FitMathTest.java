package com.noam.photodream;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FitMathTest {

    @Test
    public void landscapePhotoOnPortraitScreenIsLimitedByWidth() {
        // 400x300 on 1000x2000 with no margin: width decides
        assertEquals(2.5f, FitMath.fitScale(400, 300, 1000, 2000, 0f), 0.001f);
    }

    @Test
    public void portraitPhotoOnLandscapeScreenIsLimitedByHeight() {
        assertEquals(2f, FitMath.fitScale(300, 400, 2000, 800, 0f), 0.001f);
    }

    @Test
    public void marginShrinksTheResult() {
        // 5% margin on each side leaves 90% of the area
        assertEquals(2.25f, FitMath.fitScale(400, 300, 1000, 2000, 0.05f), 0.001f);
    }

    @Test
    public void bigCardIsShrunk() {
        assertEquals(0.5f, FitMath.fitScale(2000, 1000, 1000, 1000, 0f), 0.001f);
    }

    @Test
    public void degenerateInputFallsBackToOne() {
        assertEquals(1f, FitMath.fitScale(0, 100, 1000, 1000, 0.05f), 0f);
        assertEquals(1f, FitMath.fitScale(100, 100, 0, 1000, 0.05f), 0f);
    }

    @Test
    public void absurdMarginIsClamped() {
        // never zero or negative
        assertEquals(true, FitMath.fitScale(100, 100, 1000, 1000, 0.9f) > 0f);
    }
}
