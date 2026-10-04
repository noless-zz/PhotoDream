package com.noam.photodream.onedrive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.noam.photodream.R;
import com.noam.photodream.cloud.CloudItem;
import com.noam.photodream.cloud.CloudProvider;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** OneDrive (personal Microsoft accounts) via Microsoft Graph. */
public class OneDriveProvider implements CloudProvider {

    @Override public String id() { return "onedrive"; }

    @Override public String filePrefix() { return "od_"; }

    @Override public String displayName(Context c) { return c.getString(R.string.onedrive_name); }

    @Override public boolean isConfigured(Context c) { return OneDriveConfig.isConfigured(c); }

    @Override public String notConfiguredMessage(Context c) { return c.getString(R.string.onedrive_not_configured); }

    @Override public boolean isSignedIn(Context c) { return OneDriveAuth.isSignedIn(c); }

    @Override public String accountName(Context c) { return OneDriveAuth.accountName(c); }

    /** Opens the Microsoft login in the browser; it comes back via {@link OneDriveRedirectActivity}. */
    @Override
    public void connect(Activity activity, ActivityResultLauncher<IntentSenderRequest> resolution,
                        Callback done) {
        OneDriveAuth.startSignIn(activity);
    }

    @Override
    public void onResolutionResult(Activity activity, int resultCode, Intent data, Callback done) {
        // not used: the browser returns through OneDriveRedirectActivity
    }

    @Override public void signOut(Context c) { OneDriveAuth.signOut(c); }

    @Override public String rootId() { return "root"; }

    @Override
    public List<CloudItem> listChildren(Context c, String folderId) throws IOException {
        return new GraphClient(c).listChildren(folderId);
    }

    @Override
    public void download(Context c, String itemId, File target) throws IOException {
        new GraphClient(c).download(itemId, target);
    }
}
