package com.noam.photodream.onedrive;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The few Microsoft Graph (OneDrive) calls the app needs. All blocking –
 * call from a background thread.
 */
public final class GraphClient {

    /** A file or folder in OneDrive. */
    public static final class DriveItem {
        public final String id;
        public final String name;
        public final boolean folder;
        public final int childCount;
        public final boolean image;
        public final long size;

        DriveItem(JSONObject j) {
            id = j.optString("id");
            name = j.optString("name");
            JSONObject f = j.optJSONObject("folder");
            folder = f != null;
            childCount = f != null ? f.optInt("childCount") : 0;
            JSONObject file = j.optJSONObject("file");
            String mime = file != null ? file.optString("mimeType", "") : "";
            image = file != null && (mime.startsWith("image/") || j.has("image"));
            size = j.optLong("size");
        }
    }

    private static final String SELECT = "id,name,folder,file,image,size";

    private final Context context;

    public GraphClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Children of a folder; {@code folderId == null} means the OneDrive root. Follows paging. */
    public List<DriveItem> listChildren(String folderId) throws IOException {
        String path = folderId == null ? "/me/drive/root/children" : "/me/drive/items/" + Uri.encode(folderId) + "/children";
        String url = OneDriveConfig.GRAPH + path + "?$top=200&$select=" + SELECT;
        List<DriveItem> out = new ArrayList<>();
        while (url != null) {
            JSONObject page = Http.getJson(url, OneDriveAuth.getAccessToken(context));
            JSONArray items = page.optJSONArray("value");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject j = items.optJSONObject(i);
                    if (j != null) out.add(new DriveItem(j));
                }
            }
            url = page.has("@odata.nextLink") ? page.optString("@odata.nextLink") : null;
        }
        return out;
    }

    /**
     * All images in a folder, optionally walking sub-folders.
     * @param maxDepth 0 = only this folder
     */
    public List<DriveItem> listImages(String folderId, int maxDepth, int limit) throws IOException {
        List<DriveItem> out = new ArrayList<>();
        collect(folderId, maxDepth, limit, out);
        return out;
    }

    private void collect(String folderId, int depthLeft, int limit, List<DriveItem> out) throws IOException {
        for (DriveItem item : listChildren(folderId)) {
            if (out.size() >= limit) return;
            if (item.image) {
                out.add(item);
            } else if (item.folder && depthLeft > 0) {
                collect(item.id, depthLeft - 1, limit, out);
            }
        }
    }

    /** Downloads a file's original content into {@code target}. */
    public void download(String itemId, File target) throws IOException {
        String url = OneDriveConfig.GRAPH + "/me/drive/items/" + Uri.encode(itemId) + "/content";
        Http.download(url, OneDriveAuth.getAccessToken(context), target);
    }
}
