package com.noam.photodream.cloud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * One cloud photo source (OneDrive, Google Drive, …).
 *
 * Everything else – folder browser, background sync, settings section – is
 * shared and only talks to this interface. To add a new cloud, implement it
 * and register it in {@link CloudProviders}.
 *
 * Methods marked "blocking" do network I/O: call them on a background thread.
 */
public interface CloudProvider {

    /** Short stable id, used for preference files, cache folder and job names. */
    String id();

    /** Prefix for cached file names, e.g. "od_". Must never change (existing caches). */
    String filePrefix();

    String displayName(Context context);

    /** False if the app registration (client ID etc.) is missing. */
    boolean isConfigured(Context context);

    /** Explanation shown when {@link #isConfigured} is false. */
    String notConfiguredMessage(Context context);

    boolean isSignedIn(Context context);

    String accountName(Context context);

    /**
     * Start signing in. {@code resolution} is a launcher the activity registered
     * for screens Google Play services may need to show; {@code done} gets null
     * on success or an error message. May finish later (e.g. via a browser redirect).
     */
    void connect(Activity activity, ActivityResultLauncher<IntentSenderRequest> resolution,
                 Callback done);

    /** Result of the {@code resolution} launcher, forwarded by the activity. */
    void onResolutionResult(Activity activity, int resultCode, Intent data, Callback done);

    /** Forget the account on this phone. */
    void signOut(Context context);

    /** Id of the drive's root folder. */
    String rootId();

    /** Blocking. Children of a folder. */
    List<CloudItem> listChildren(Context context, String folderId) throws IOException;

    /** Blocking. Download a file's original content. */
    void download(Context context, String itemId, File target) throws IOException;

    interface Callback {
        /** @param error null when it worked */
        void onDone(String error);
    }
}
