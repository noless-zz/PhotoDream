package com.noam.photodream.onedrive;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.noam.photodream.R;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Simple OneDrive folder browser: tap a folder to open it, "Use this folder" to choose it. */
public class OneDriveFolderActivity extends AppCompatActivity {

    /** One level of the path we walked down. id == null is the root. */
    private static final class Level {
        final String id, name;
        Level(String id, String name) { this.id = id; this.name = name; }
    }

    private final Deque<Level> path = new ArrayDeque<>();
    private final List<GraphClient.DriveItem> folders = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private GraphClient graph;
    private ArrayAdapter<String> adapter;
    private TextView txtPath, txtImagesHere;
    private View progress;
    private int loadId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onedrive_folders);
        graph = new GraphClient(this);

        txtPath = findViewById(R.id.txt_path);
        txtImagesHere = findViewById(R.id.txt_images_here);
        progress = findViewById(R.id.progress);
        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            GraphClient.DriveItem f = folders.get(position);
            path.push(new Level(f.id, f.name));
            load();
        });

        findViewById(R.id.btn_up).setOnClickListener(v -> goUp());
        findViewById(R.id.btn_use).setOnClickListener(v -> useCurrent());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (path.size() > 1) goUp(); else finish();
            }
        });

        path.push(new Level(null, getString(R.string.onedrive_root)));
        load();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void goUp() {
        if (path.size() > 1) {
            path.pop();
            load();
        }
    }

    private void useCurrent() {
        Level here = path.peek();
        if (here == null) return;
        // the root has no item id in our list; Graph accepts the alias "root"
        new OneDrivePrefs(this).setFolder(here.id == null ? "root" : here.id, pathText());
        OneDriveScheduler.syncNow(this);   // also sets up the regular sync when done
        Toast.makeText(this, R.string.onedrive_sync_started, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String pathText() {
        StringBuilder sb = new StringBuilder();
        List<Level> levels = new ArrayList<>(path);
        for (int i = levels.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(levels.get(i).name);
        }
        return sb.toString();
    }

    private void load() {
        final Level here = path.peek();
        if (here == null) return;
        final int id = ++loadId;
        txtPath.setText(pathText());
        txtImagesHere.setText("");
        progress.setVisibility(View.VISIBLE);
        folders.clear();
        adapter.clear();
        findViewById(R.id.btn_up).setEnabled(path.size() > 1);

        io.execute(() -> {
            try {
                List<GraphClient.DriveItem> items = graph.listChildren(here.id);
                List<GraphClient.DriveItem> dirs = new ArrayList<>();
                int images = 0;
                for (GraphClient.DriveItem it : items) {
                    if (it.folder) dirs.add(it);
                    else if (it.image) images++;
                }
                dirs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                final int imageCount = images;
                runOnUiThread(() -> {
                    if (id != loadId) return;
                    progress.setVisibility(View.GONE);
                    folders.addAll(dirs);
                    for (GraphClient.DriveItem d : dirs) {
                        adapter.add("📁  " + d.name + "   (" + d.childCount + ")");
                    }
                    txtImagesHere.setText(getString(R.string.onedrive_images_here, imageCount));
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (id != loadId) return;
                    progress.setVisibility(View.GONE);
                    txtImagesHere.setText(getString(R.string.onedrive_error, e.getMessage()));
                });
            }
        });
    }
}
