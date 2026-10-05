package com.noam.photodream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CropMathTest {

    private static float[] box(float l, float t, float r, float b) { return new float[]{l, t, r, b}; }

    @Test
    public void noFacesIsTheCentreCrop() {
        // 4000x3000 photo on a 1080x1920 portrait view (aspect 0.5625): full height, centred
        float[] c = CropMath.bestCrop(4000, 3000, Collections.<float[]>emptyList(), 1080f / 1920f);
        assertEquals(3000f, c[3], 0.01f);
        assertEquals(1687.5f, c[2], 0.01f);
        assertEquals((4000 - 1687.5f) / 2f, c[0], 0.01f);
        assertEquals(0f, c[1], 0.01f);
    }

    @Test
    public void facesOnTheLeftPullTheCropLeft() {
        List<float[]> faces = Arrays.asList(box(0.05f, 0.3f, 0.15f, 0.5f));
        float[] c = CropMath.bestCrop(4000, 3000, faces, 1080f / 1920f);
        assertEquals(0f, c[0], 0.01f);                         // clamped to the left edge
        assertTrue("face inside the crop", 0.15f * 4000 <= c[0] + c[2]);
    }

    @Test
    public void facesOnTheRightPullTheCropRight() {
        List<float[]> faces = Arrays.asList(box(0.85f, 0.3f, 0.95f, 0.5f));
        float[] c = CropMath.bestCrop(4000, 3000, faces, 1080f / 1920f);
        assertEquals(4000f, c[0] + c[2], 0.01f);
        assertTrue(0.85f * 4000 >= c[0]);
    }

    @Test
    public void tallPhotoOnWideViewMovesVertically() {
        // portrait photo on a landscape screen: full width, vertical position follows the face (near the top)
        List<float[]> faces = Arrays.asList(box(0.4f, 0.05f, 0.6f, 0.2f));
        float[] c = CropMath.bestCrop(3000, 4000, faces, 16f / 9f);
        assertEquals(3000f, c[2], 0.01f);
        assertEquals(0f, c[1], 0.01f);
        assertTrue(c[1] + c[3] >= 0.2f * 4000);
    }

    @Test
    public void cropAlwaysHasTheViewShapeAndStaysInsideTheImage() {
        Random r = new Random(5);
        for (int i = 0; i < 2000; i++) {
            int w = 800 + r.nextInt(4000), h = 800 + r.nextInt(4000);
            float aspect = 0.4f + r.nextFloat() * 2f;
            List<float[]> faces = new ArrayList<>();
            for (int f = r.nextInt(4); f > 0; f--) {
                float l = r.nextFloat() * 0.8f, t = r.nextFloat() * 0.8f;
                faces.add(box(l, t, l + 0.05f + r.nextFloat() * 0.15f, t + 0.05f + r.nextFloat() * 0.15f));
            }
            float[] c = CropMath.bestCrop(w, h, faces, aspect);
            assertEquals(aspect, c[2] / c[3], 0.001f);
            assertTrue(c[0] >= -0.01f && c[1] >= -0.01f);
            assertTrue(c[0] + c[2] <= w + 0.01f && c[1] + c[3] <= h + 0.01f);
        }
    }

    @Test
    public void facesAreKeptInsideWheneverTheyFit() {
        Random r = new Random(11);
        for (int i = 0; i < 2000; i++) {
            int w = 1000 + r.nextInt(3000), h = 1000 + r.nextInt(3000);
            float aspect = 0.5f + r.nextFloat() * 1.5f;
            float l = r.nextFloat() * 0.7f, t = r.nextFloat() * 0.7f;
            float[] face = box(l, t, l + 0.1f, t + 0.1f);
            float[] c = CropMath.bestCrop(w, h, Arrays.asList(face), aspect);
            boolean fitsHorizontally = 0.1f * w <= c[2] + 0.01f, fitsVertically = 0.1f * h <= c[3] + 0.01f;
            if (fitsHorizontally) {
                assertTrue(face[0] * w >= c[0] - 0.01f && face[2] * w <= c[0] + c[2] + 0.01f);
            }
            if (fitsVertically) {
                assertTrue(face[1] * h >= c[1] - 0.01f && face[3] * h <= c[1] + c[3] + 0.01f);
            }
        }
    }

    @Test
    public void twoFarApartFacesCentreOnTheirMiddle() {
        List<float[]> faces = Arrays.asList(box(0.02f, 0.4f, 0.08f, 0.5f), box(0.92f, 0.4f, 0.98f, 0.5f));
        float[] focus = CropMath.focusPoint(faces);
        assertEquals(0.5f, focus[0], 0.001f);
        assertEquals(0.45f, focus[1], 0.001f);
        float[] c = CropMath.bestCrop(4000, 3000, faces, 1080f / 1920f);
        assertEquals(2000f, c[0] + c[2] / 2f, 0.1f);
    }

    @Test
    public void focusPointOfNothingIsNull() {
        assertNull(CropMath.focusPoint(null));
        assertNull(CropMath.focusPoint(new ArrayList<>()));
        assertArrayEquals(new float[]{0.3f, 0.4f}, CropMath.focusPoint(Arrays.asList(box(0.2f, 0.3f, 0.4f, 0.5f))), 0.0001f);
    }
}
