package com.noam.photodream.motion;

/**
 * Where one card should be right now: the centre of the card in table pixels, an extra tilt
 * (added to the card's own random tilt), a size factor, an opacity and a depth.
 *
 * Depth runs from -1 (far behind) to +1 (close to the viewer). The view turns it into
 * translationZ so that, for example, a photo swirling behind the tornado is drawn behind the
 * ones in front of it.
 *
 * A plain mutable object so the per-frame loop can reuse one instead of allocating 60 per second.
 */
public final class Pose {
    public float x, y;
    public float rotation;
    public float scale = 1f;
    public float alpha = 1f;
    public float depth;

    public Pose set(float x, float y, float rotation, float scale, float alpha, float depth) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.scale = scale;
        this.alpha = alpha;
        this.depth = depth;
        return this;
    }
}
