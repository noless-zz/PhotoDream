package com.noam.photodream.onedrive;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import com.noam.photodream.cloud.CloudItem;
import com.noam.photodream.cloud.Http;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The few Microsoft Graph (OneDrive) calls the app needs. All blocking –
 * call from a background thread.
 */
public final class GraphClient {

    private static final String SELECT = "id,name,folder,file,image,size";

    private final Context context;

    public GraphClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Children of a folder; {@code folderId == null} means the OneDrive root. Follows paging. */
    public List<CloudItem> listChildren(String folderId) throws IOException {
        String path = folderId == null || "root".equals(folderId) ? "/me/drive/root/children" : "/me/drive/items/" + Uri.encode(folderId) + "/children";
        String url = OneDriveConfig.GRAPH + path + "?$top=200&$select=" + SELECT;
        List<CloudItem> out = new ArrayList<>();
        while (url != null) {
            JSONObject page = Http.getJson(url, OneDriveAuth.getAccessToken(context));
            JSONArray items = page.optJSONArray("value");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject j = items.optJSONObject(i);
                    if (j != null) out.add(toItem(j));
                }
            }
            url = page.has("@odata.nextLink") ? page.optString("@odata.nextLink") : null;
        }
        return out;
    }

    private static CloudItem toItem(JSONObject j) {
        JSONObject f = j.optJSONObject("folder");
        JSONObject file = j.optJSONObject("file");
        String mime = file != null ? file.optString("mimeType", "") : "";
        boolean image = file != null && (mime.startsWith("image/") || j.has("image"));
        return new CloudItem(j.optString("id"), j.optString("name"), f != null, image,
                f != null ? f.optInt("childCount") : -1);
    }

    /** Downloads a file's original content into {@code target}. */
    public void download(String itemId, File target) throws IOException {
        String url = OneDriveConfig.GRAPH + "/me/drive/items/" + Uri.encode(itemId) + "/content";
        Http.download(url, OneDriveAuth.getAccessToken(context), target);
    }
}
