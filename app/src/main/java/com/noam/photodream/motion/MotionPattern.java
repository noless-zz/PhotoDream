package com.noam.photodream.motion;

/**
 * A choreography for the photo table: given a card's place in the queue and the time, says
 * where that card should be.
 *
 * Patterns are pure maths (no Android classes) so they can be unit-tested on the computer.
 * The view does not jump cards straight to the pose; it eases them toward it every frame
 * (see {@link Smoothing}), so a pattern may change a card's target suddenly – for example
 * when a new photo arrives and every card moves one place along – and the card still glides.
 */
public interface MotionPattern {

    /**
     * @param frame table size, number of places and time
     * @param rank  0 for the newest card, 1 for the one before it, … up to {@code frame.count - 1}
     * @param id    a number that stays with the card for its whole life (patterns that give each
     *              card its own personality use it; most ignore it)
     * @param out   filled in with the target pose
     */
    void pose(MotionFrame frame, int rank, long id, Pose out);
}
