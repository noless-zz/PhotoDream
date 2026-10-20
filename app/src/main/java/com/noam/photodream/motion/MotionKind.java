package com.noam.photodream.motion;

/**
 * The "Movement" choices for the photo table. NONE and DRIFT are the original behaviours
 * (still photos, or each photo floating slowly on its own) and have no pattern.
 */
public enum MotionKind {
    NONE, DRIFT, SUNFLOWER, GALAXY, TORNADO, WAVES, FIBONACCI, SNAKE, WARP, ORBITS, FLOCK, SURPRISE;

    /** A fresh pattern for this choice, or null for NONE / DRIFT. */
    public MotionPattern newPattern() {
        switch (this) {
            case SUNFLOWER: return new SunflowerMotion();
            case GALAXY: return new GalaxyMotion();
            case TORNADO: return new TornadoMotion();
            case WAVES: return new WavesMotion();
            case FIBONACCI: return new FibonacciMotion();
            case SNAKE: return new SnakeMotion();
            case WARP: return new WarpMotion();
            case ORBITS: return new OrbitsMotion();
            case FLOCK: return new FlockMotion();
            case SURPRISE: return new SurpriseMotion();
            default: return null;
        }
    }

    /** Reads a saved name; unknown or missing names fall back to {@code fallback}. */
    public static MotionKind parse(String name, MotionKind fallback) {
        if (name == null) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
