package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WidgetMathTest {

    @Test
    public void budgetIsAboutOneAndAHalfScreens() {
        long bytes = WidgetMath.maxBitmapBytes(1080, 2400);
        assertTrue(bytes < 1080L * 2400 * 4 * 1.5);
        assertTrue(bytes > 1080L * 2400 * 4);
    }

    @Test
    public void smallWidgetsKeepTheirSize() {
        int[] s = WidgetMath.fit(500, 300, WidgetMath.maxBitmapBytes(1080, 2400));
        assertEquals(500, s[0]);
        assertEquals(300, s[1]);
    }

    @Test
    public void hugeWidgetIsScaledDownKeepingShapeAndFittingTheBudget() {
        long budget = WidgetMath.maxBitmapBytes(720, 1280);
        int[] s = WidgetMath.fit(4000, 2000, budget);
        assertTrue((long) s[0] * s[1] * 4 <= budget);
        assertEquals(2.0, s[0] / (double) s[1], 0.02);
        assertTrue(s[0] > 100);
    }

    @Test
    public void zeroOrNegativeSizesBecomeOnePixel() {
        int[] s = WidgetMath.fit(0, -5, 1_000_000);
        assertEquals(1, s[0]);
        assertEquals(1, s[1]);
    }

    @Test
    public void tinyBudgetStillGivesAtLeastOnePixel() {
        int[] s = WidgetMath.fit(1000, 1000, 1);
        assertEquals(1, s[0]);
        assertEquals(1, s[1]);
    }

    @Test
    public void dpToPxRounds() {
        assertEquals(330, WidgetMath.dpToPx(110, 3f));
        assertEquals(5, WidgetMath.dpToPx(3, 1.5f));   // 4.5 rounds up
    }
}
