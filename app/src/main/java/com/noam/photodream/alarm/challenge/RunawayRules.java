package com.noam.photodream.alarm.challenge;

import com.noam.photodream.alarm.Alarm;

import java.util.Random;

/**
 * Rules of "Catch the runaway photo": a photo dodges your finger. Pure Java (seeded Random), unit-tested.
 *
 * <ul>
 *   <li>When a touch lands near the card it jumps away with probability Easy 50% / Medium 70% / Hard 85%.</li>
 *   <li>Fair play: never more than 4 dodges in a row – the 5th touch near the card always stays put.</li>
 *   <li>A catch counts when the finger lifts inside the card's <i>current</i> bounds.</li>
 *   <li>Every catch makes the card ~10% smaller and a bit faster.</li>
 * </ul>
 */
public final class RunawayRules {

    public static final int MAX_DODGES_IN_A_ROW = 4;
    private static final float SHRINK_PER_CATCH = 0.90f;
    private static final float MIN_SCALE = 0.45f;
    public static final long BASE_DODGE_MS = 200;

    private final int catchesNeeded;
    private final double dodgeProbability;
    private final Random random;
    private int catches;
    private int dodgesInARow;

    public RunawayRules(Alarm.Difficulty difficulty, Random random) {
        this.random = random;
        switch (difficulty) {
            case EASY:
                catchesNeeded = 3;
                dodgeProbability = 0.50;
                break;
            case HARD:
                catchesNeeded = 7;
                dodgeProbability = 0.85;
                break;
            default:
                catchesNeeded = 5;
                dodgeProbability = 0.70;
        }
    }

    public int catchesNeeded() { return catchesNeeded; }

    public int catches() { return catches; }

    public boolean isSolved() { return catches >= catchesNeeded; }

    /** Hard mode adds two decoy photos that don't count. */
    public static int decoys(Alarm.Difficulty difficulty) { return difficulty == Alarm.Difficulty.HARD ? 2 : 0; }

    /**
     * A finger touched down.
     * @param nearCard true if the touch is on or close to the card
     * @return true if the card should jump away now
     */
    public boolean onTouchDown(boolean nearCard) {
        if (!nearCard || isSolved()) return false;
        if (dodgesInARow >= MAX_DODGES_IN_A_ROW) {      // fairness: this touch is allowed to land
            dodgesInARow = 0;
            return false;
        }
        if (random.nextDouble() < dodgeProbability) {
            dodgesInARow++;
            return true;
        }
        dodgesInARow = 0;
        return false;
    }

    /** The finger lifted. Returns true if that was a catch. */
    public boolean onTouchUp(boolean insideCurrentBounds) {
        if (!insideCurrentBounds || isSolved()) return false;
        catches++;
        dodgesInARow = 0;
        return true;
    }

    /** Card size factor after the catches so far: 1.0, 0.9, 0.81 … never below 0.45. */
    public float scale() {
        return Math.max(MIN_SCALE, (float) Math.pow(SHRINK_PER_CATCH, catches));
    }

    /** How long a dodge takes: 200 ms at first, quicker with every catch (never below 90 ms). */
    public long dodgeMillis() {
        return Math.max(90, Math.round(BASE_DODGE_MS * Math.pow(SHRINK_PER_CATCH, catches)));
    }

    /**
     * A new centre for the card, always fully on screen and – when there is room – well away from
     * where it is now. Sizes are the card's current (scaled) size.
     * @return {centerX, centerY}
     */
    public float[] nextCenter(float cardW, float cardH, float areaW, float areaH, float curX, float curY) {
        float minX = cardW / 2f, maxX = Math.max(minX, areaW - cardW / 2f);
        float minY = cardH / 2f, maxY = Math.max(minY, areaH - cardH / 2f);
        float wantDistance = 0.35f * Math.min(areaW, areaH);
        float[] best = {(minX + maxX) / 2f, (minY + maxY) / 2f};
        double bestDist = -1;
        for (int attempt = 0; attempt < 12; attempt++) {
            float x = minX + random.nextFloat() * (maxX - minX);
            float y = minY + random.nextFloat() * (maxY - minY);
            double d = Math.hypot(x - curX, y - curY);
            if (d > bestDist) {
                bestDist = d;
                best = new float[]{x, y};
            }
            if (d >= wantDistance) break;
        }
        return best;
    }
}
