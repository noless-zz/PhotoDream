package com.noam.photodream;

import java.util.List;

/**
 * A way of showing photos full screen. Implemented by
 * {@link SlideshowView} (one photo at a time) and
 * {@link PhotoTableView} (photos pile up like prints on a table).
 * {@link SlideshowController} talks only to this interface.
 */
public interface PhotoDisplay {

    /** The user asked to leave (long-press). */
    interface Listener {
        void onExitRequested();

        /** A photo just appeared on screen (for the date caption). Default: ignore. */
        default void onPhotoShown(Photo photo) { }
    }

    /** Start (or restart) with a list of photos. Main thread only. */
    void start(List<Photo> photos);

    /** Stop timers and animations. Safe to call more than once. */
    void stop();

    /** Stop for good and free background threads and bitmaps. */
    void release();

    void setIntervalSeconds(int seconds);

    /** How often each photo should come up (favorites more often); null = every photo equally, in order. */
    void setWeights(PhotoQueue.Weights weights);

    void setListener(Listener listener);
}
