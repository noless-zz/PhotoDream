package com.noam.photodream.onedrive;

import android.content.Context;

import com.noam.photodream.R;

/**
 * App registration details for Microsoft sign-in.
 * The client ID comes from res/values/onedrive_config.xml – see docs/ONEDRIVE_SETUP.md.
 */
public final class OneDriveConfig {

    /** Must match the redirect URI registered in the Entra portal (Mobile and desktop platform). */
    public static final String REDIRECT_URI = "photodream://auth";

    /** "common" = personal Microsoft accounts and work/school accounts. */
    static final String AUTH_BASE = "https://login.microsoftonline.com/common/oauth2/v2.0/";

    /** Read-only access to files, plus a refresh token so we stay signed in. */
    static final String SCOPES = "Files.Read User.Read offline_access";

    static final String GRAPH = "https://graph.microsoft.com/v1.0";

    private static final String PLACEHOLDER = "PASTE-YOUR-CLIENT-ID-HERE";

    private OneDriveConfig() { }

    public static String clientId(Context context) {
        return context.getString(R.string.onedrive_client_id).trim();
    }

    /** False until the owner pastes a real client ID into onedrive_config.xml. */
    public static boolean isConfigured(Context context) {
        String id = clientId(context);
        return !id.isEmpty() && !id.equals(PLACEHOLDER);
    }
}
