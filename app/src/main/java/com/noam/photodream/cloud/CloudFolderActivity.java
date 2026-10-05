package com.noam.photodream.cloud;

import android.content.Context;
import android.content.Intent;
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

/**
 * Simple cloud folder browser: tap a folder to open it, "Use this folder" to choose it.
 * Start with {@link #intent(Context, CloudProvider)}.
 */
public class CloudFolderActivity extends AppCompatActivity {

    /** One level of the path we walked down. id == null is the root. */
    private static final class Level {
        final String id, name;
        Level(String id, String name) { this.id = id; this.name = name; }
    }

    private static final String EXTRA_PROVIDER = "provider";

    public static Intent intent(Context context, CloudProvider provider) {
        return new Intent(context, CloudFolderActivity.class).putExtra(EXTRA_PROVIDER, provider.id());
    }

    private final Deque<Level> path = new ArrayDeque<>();
    private final List<CloudItem> folders = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private CloudProvider provider;
    private ArrayAdapter<String> adapter;
    private TextView txtPath, txtImagesHere;
    private View progress;
    private int loadId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_folders);
        provider = CloudProviders.get(getIntent().getStringExtra(EXTRA_PROVIDER));
        setTitle(provider.displayName(this));
        TextView title = findViewById(R.id.txt_title);
        title.setText(getString(R.string.cloud_pick_title, provider.displayName(this)));

        txtPath = findViewById(R.id.txt_path);
        txtImagesHere = findViewById(R.id.txt_images_here);
        progress = findViewById(R.id.progress);
        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            CloudItem f = folders.get(position);
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

        path.push(new Level(provider.rootId(), getString(R.string.cloud_root)));
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
        new CloudPrefs(this, provider).setFolder(here.id, pathText());
        CloudScheduler.syncNow(this, provider);   // also sets up the regular sync when done
        Toast.makeText(this, R.string.cloud_sync_started, Toast.LENGTH_SHORT).show();
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
                List<CloudItem> items = provider.listChildren(this, here.id);
                List<CloudItem> dirs = new ArrayList<>();
                int images = 0;
                for (CloudItem it : items) {
                    if (it.folder) dirs.add(it);
                    else if (it.image) images++;
                }
                dirs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                final int imageCount = images;
                runOnUiThread(() -> {
                    if (id != loadId) return;
                    progress.setVisibility(View.GONE);
                    folders.addAll(dirs);
                    for (CloudItem d : dirs) {
                        adapter.add("📁  " + d.name + (d.childCount >= 0 ? "   (" + d.childCount + ")" : ""));
                    }
                    txtImagesHere.setText(getResources().getQuantityString(R.plurals.cloud_images_here, imageCount, imageCount));
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (id != loadId) return;
                    progress.setVisibility(View.GONE);
                    txtImagesHere.setText(getString(R.string.cloud_error, e.getMessage()));
                });
            }
        });
    }
}
