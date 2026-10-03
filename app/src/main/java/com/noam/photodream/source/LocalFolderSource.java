package com.noam.photodream.source;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Photos from a folder the user picked with the system folder picker
 * (Storage Access Framework). Walks sub-folders too.
 */
public class LocalFolderSource implements PhotoSource {

    private static final String TAG = "LocalFolderSource";
    private static final int MAX_DEPTH = 4;
    private static final int MAX_PHOTOS = 5000;

    private final Uri treeUri;

    public LocalFolderSource(Uri treeUri) {
        this.treeUri = treeUri;
    }

    @Override
    public String getName() {
        return "Phone folder";
    }

    @Override
    public List<Uri> listPhotos(Context context) {
        List<Uri> out = new ArrayList<>();
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            walk(context.getContentResolver(), rootId, 0, out);
        } catch (RuntimeException e) {
            // e.g. permission was revoked or the folder was deleted
            Log.w(TAG, "Could not read folder " + treeUri, e);
        }
        return out;
    }

    private void walk(ContentResolver cr, String parentId, int depth, List<Uri> out) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor c = cr.query(children, columns, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext() && out.size() < MAX_PHOTOS) {
                String id = c.getString(0);
                String mime = c.getString(1);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (depth < MAX_DEPTH) walk(cr, id, depth + 1, out);
                } else if (mime != null && mime.startsWith("image/")) {
                    out.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, id));
                }
            }
        }
    }
}
