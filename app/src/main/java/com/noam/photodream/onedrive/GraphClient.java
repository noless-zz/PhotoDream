package com.noam.photodream.onedrive;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import com.noam.photodream.cloud.CloudIds;
import com.noam.photodream.cloud.CloudItem;
import com.noam.photodream.cloud.CloudProvider;
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

    /**
     * Children of a folder; {@code folderId == null} means the OneDrive root, {@link CloudProvider#SHARED_WITH_ME}
     * the folders shared with the user. A folder in someone else's drive has an id from
     * {@link CloudIds} (drive id + item id). Follows paging.
     */
    public List<CloudItem> listChildren(String folderId) throws IOException {
        if (CloudProvider.SHARED_WITH_ME.equals(folderId)) return listSharedWithMe();
        CloudIds.Parts where = CloudIds.decode(folderId);
        String path;
        if (where.itemId == null || "root".equals(where.itemId)) {
            path = "/me/drive/root/children";
        } else if (where.driveId == null) {
            path = "/me/drive/items/" + Uri.encode(where.itemId) + "/children";
        } else {
            path = "/drives/" + Uri.encode(where.driveId) + "/items/" + Uri.encode(where.itemId) + "/children";
        }
        String url = OneDriveConfig.GRAPH + path + "?$top=200&$select=" + SELECT;
        List<CloudItem> out = new ArrayList<>();
        while (url != null) {
            JSONObject page = Http.getJson(url, OneDriveAuth.getAccessToken(context));
            JSONArray items = page.optJSONArray("value");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject j = items.optJSONObject(i);
                    // inside someone else's drive every item id carries that drive's id
                    if (j != null) out.add(toItem(j, where.driveId));
                }
            }
            url = page.has("@odata.nextLink") ? page.optString("@odata.nextLink") : null;
        }
        return out;
    }

    /** "Shared with me": each entry is a {@code remoteItem} that lives in the sharer's drive. */
    private List<CloudItem> listSharedWithMe() throws IOException {
        String url = OneDriveConfig.GRAPH + "/me/drive/sharedWithMe?$top=200";
        List<CloudItem> out = new ArrayList<>();
        while (url != null) {
            JSONObject page = Http.getJson(url, OneDriveAuth.getAccessToken(context));
            JSONArray items = page.optJSONArray("value");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject j = items.optJSONObject(i);
                    JSONObject remote = j == null ? null : j.optJSONObject("remoteItem");
                    if (remote == null) continue;
                    JSONObject parent = remote.optJSONObject("parentReference");
                    String driveId = parent == null ? null : parent.optString("driveId", null);
                    CloudItem item = toItem(remote, driveId);
                    if (item.folder) out.add(item);          // only folders can be chosen as a photo source
                }
            }
            url = page.has("@odata.nextLink") ? page.optString("@odata.nextLink") : null;
        }
        return out;
    }

    private static CloudItem toItem(JSONObject j, String driveId) {
        JSONObject f = j.optJSONObject("folder");
        JSONObject file = j.optJSONObject("file");
        String mime = file != null ? file.optString("mimeType", "") : "";
        boolean image = file != null && (mime.startsWith("image/") || j.has("image"));
        return new CloudItem(CloudIds.encode(driveId, j.optString("id")), j.optString("name"), f != null, image,
                f != null ? f.optInt("childCount") : -1);
    }

    /** Downloads a file's original content into {@code target}. */
    public void download(String itemId, File target) throws IOException {
        CloudIds.Parts where = CloudIds.decode(itemId);
        String url = where.driveId == null
                ? OneDriveConfig.GRAPH + "/me/drive/items/" + Uri.encode(where.itemId) + "/content"
                : OneDriveConfig.GRAPH + "/drives/" + Uri.encode(where.driveId) + "/items/" + Uri.encode(where.itemId) + "/content";
        Http.download(url, OneDriveAuth.getAccessToken(context), target);
    }
}
