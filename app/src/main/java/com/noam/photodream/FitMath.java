package com.noam.photodream;

/** Small pure helpers for sizing things on screen (no Android imports, so they are unit-tested). */
public final class FitMath {

    private FitMath() { }

    /**
     * Scale factor that makes a w×h rectangle as large as possible inside an
     * areaW×areaH area while keeping its aspect ratio and leaving
     * {@code marginFraction} of the area free on every side (0.05 = 5%).
     * May be above 1 – a small card is enlarged to fill the screen.
     */
    public static float fitScale(float w, float h, float areaW, float areaH, float marginFraction) {
        if (w <= 0 || h <= 0 || areaW <= 0 || areaH <= 0) return 1f;
        float usable = 1f - 2f * Math.max(0f, Math.min(0.49f, marginFraction));
        return Math.min(areaW * usable / w, areaH * usable / h);
    }
}
