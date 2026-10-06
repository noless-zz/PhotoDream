package com.noam.photodream;

import android.content.Context;

import com.noam.photodream.cloud.Http;

import org.json.JSONObject;

/**
 * PhotoDream is sideloaded, so nobody gets updates automatically. When settings open we ask GitHub
 * (no sign-in, at most once a day) for the newest release and remember its tag. The banner then shows
 * whenever that tag is newer than the installed version. Every network problem is ignored silently.
 */
public final class UpdateChecker {

    static final String LATEST_URL = "https://api.github.com/repos/noless-zz/PhotoDream/releases/latest";
    public static final String DOWNLOAD_URL = "https://github.com/noless-zz/PhotoDream/releases/latest/download/PhotoDream.apk";
    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

    private UpdateChecker() { }

    /** The newer version number (like "0.4") to offer, or null if we are up to date / don't know. Instant: no network. */
    public static String availableVersion(Context context) {
        String tag = new Prefs(context).getLatestTag();
        return VersionCompare.isNewer(tag, BuildConfig.VERSION_NAME) ? VersionCompare.display(tag) : null;
    }

    /** True if a day has passed since the last check. */
    static boolean isDue(long lastCheckedMs, long nowMs) {
        return nowMs - lastCheckedMs >= ONE_DAY_MS || lastCheckedMs > nowMs;
    }

    /** Ask GitHub if a day has passed. Blocks: call from a background thread. Never throws. */
    public static void checkIfDue(Context context) {
        Prefs prefs = new Prefs(context);
        long now = System.currentTimeMillis();
        if (!isDue(prefs.getUpdateCheckedAt(), now)) return;
        try {
            JSONObject release = Http.getJson(LATEST_URL, null);
            prefs.setLatestTag(release.optString("tag_name", ""));
            prefs.setUpdateCheckedAt(now);
        } catch (Exception e) {
            // offline, rate-limited, GitHub down …: stay silent, try again next time settings open
        }
    }
}
