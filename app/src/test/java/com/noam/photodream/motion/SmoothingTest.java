package com.noam.photodream.motion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SmoothingTest {

    @Test
    public void noTimeMeansNoMovement() {
        assertEquals(0f, Smoothing.factor(0, 0.5), 0f);
        assertEquals(10f, Smoothing.step(10f, 50f, Smoothing.factor(0, 0.5)), 0f);
    }

    @Test
    public void zeroTauJumpsStraightThere() {
        assertEquals(1f, Smoothing.factor(0.016, 0), 0f);
    }

    @Test
    public void afterTauAbout63PercentIsCovered() {
        float v = 0f;
        float k = Smoothing.factor(1.0 / 60, 0.5);
        for (int i = 0; i < 30; i++) v = Smoothing.step(v, 100f, k);   // 0.5 s at 60 fps
        assertEquals(63.2f, v, 0.2f);
    }

    @Test
    public void sameResultAt30And60FramesPerSecond() {
        float a = 0f, b = 0f;
        float k60 = Smoothing.factor(1.0 / 60, 0.5), k30 = Smoothing.factor(1.0 / 30, 0.5);
        for (int i = 0; i < 60; i++) a = Smoothing.step(a, 100f, k60);
        for (int i = 0; i < 30; i++) b = Smoothing.step(b, 100f, k30);
        assertEquals(a, b, 0.01f);
    }
}
