package com.noam.photodream.alarm;

import android.content.Context;
import android.view.View;

import com.noam.photodream.Photo;

import java.util.List;

/**
 * A mini game that stops the alarm when it is solved. The ringing screen (the "host") creates one,
 * puts its view on screen and listens for {@link Listener#onSolved()}. Implementations must be
 * playable one-handed, never wait for the network, and use only the photos they are given.
 */
public interface AlarmChallenge {

    interface Listener {
        /** Progress for the "3 / 6" text; {@code total} may be 100 for a percentage. */
        void onProgress(int done, int total);

        /** The user solved it – the host stops the alarm. */
        void onSolved();
    }

    View createView(Context context, List<Photo> photos, Alarm.Difficulty difficulty, Listener listener);

    /** The view is on screen: timers may start. */
    void start();

    /** The view is going away: cancel timers and animators. */
    void stop();
}
