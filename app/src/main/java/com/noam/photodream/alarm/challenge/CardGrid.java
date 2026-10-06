package com.noam.photodream.alarm.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Places N cards of the same shape on a "table" so that they never overlap: the table is cut into a
 * grid, every card sits somewhere inside its own cell (jittered, so it doesn't look like a grid).
 * Pure Java, unit-tested.
 */
public final class CardGrid {

    /** Position and size of one card in table pixels. */
    public static final class Slot {
        public final float x, y, w, h;

        Slot(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private CardGrid() { }

    /**
     * @param aspect card width / height (0.75 = portrait photo)
     * @param jitter 0 = every card centred in its cell, 1 = anywhere inside the cell
     */
    public static List<Slot> layout(int count, float areaW, float areaH, float aspect, float jitter, Random random) {
        List<Slot> out = new ArrayList<>();
        if (count <= 0 || areaW <= 0 || areaH <= 0 || aspect <= 0) return out;

        // choose the number of columns that makes the cards biggest
        int bestCols = 1;
        float bestW = -1;
        for (int cols = 1; cols <= count; cols++) {
            int rows = (count + cols - 1) / cols;
            float w = cardWidth(areaW / cols, areaH / rows, aspect);
            if (w > bestW) {
                bestW = w;
                bestCols = cols;
            }
        }
        int cols = bestCols;
        int rows = (count + cols - 1) / cols;
        float cellW = areaW / cols, cellH = areaH / rows;
        float w = bestW, h = w / aspect;

        for (int i = 0; i < count; i++) {
            int col = i % cols, row = i / cols;
            int inRow = row == rows - 1 ? count - row * cols : cols;
            float rowOffset = (cols - inRow) * cellW / 2f;           // a short last row is centred
            float slackX = cellW - w, slackY = cellH - h;
            float fx = 0.5f + (random.nextFloat() - 0.5f) * jitter;
            float fy = 0.5f + (random.nextFloat() - 0.5f) * jitter;
            out.add(new Slot(rowOffset + col * cellW + slackX * fx, row * cellH + slackY * fy, w, h));
        }
        return out;
    }

    /** Biggest card width that fits a cell, leaving ~12% for jitter and breathing room. */
    private static float cardWidth(float cellW, float cellH, float aspect) {
        return Math.min(cellW * 0.88f, cellH * 0.88f * aspect);
    }
}
