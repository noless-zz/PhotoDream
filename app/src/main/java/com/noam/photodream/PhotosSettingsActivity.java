package com.noam.photodream;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.work.ExistingWorkPolicy;

import com.google.android.material.button.MaterialButton;
import com.noam.photodream.cloud.CloudProvider;
import com.noam.photodream.cloud.CloudProviders;
import com.noam.photodream.cloud.CloudSourceView;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.noam.photodream.describe.DescriptionJob;
import com.noam.photodream.describe.DescriptionStore;
import com.noam.photodream.describe.DescriptionWorker;
import com.noam.photodream.describe.GenAiDescriber;
import com.noam.photodream.source.LocalFolderSource;
import com.noam.photodream.source.PhotoSource;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings › Photos: phone folders, OneDrive, Google Drive, background sync. */
public class PhotosSettingsActivity extends SettingsScreen {

    private TextView txtFolder, txtCount;
    private LinearLayout listFolders;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** System folder picker. We keep read access permanently so the screensaver can use it later. */
    private final ActivityResultLauncher<Uri> pickFolder =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (!prefs.addLocalFolder(uri)) {
                    // same folder picked twice: nothing to add (keep the permission, it is still in use)
                    Toast.makeText(this, R.string.folder_already_added, Toast.LENGTH_SHORT).show();
                }
                refreshFolderInfo();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_photos);
        setTitle(R.string.section_photos);

        txtFolder = findViewById(R.id.txt_folder);
        listFolders = findViewById(R.id.list_folders);
        txtCount = findViewById(R.id.txt_count);

        Button choose = findViewById(R.id.btn_choose_folder);
        choose.setOnClickListener(v -> pickFolder.launch(null));
        setupSwitch(R.id.sw_local_show, prefs.isSourceEnabled("local"), on -> {
            prefs.setSourceEnabled("local", on);
            refreshFolderInfo();
        });
        SwatchPicker localFrame = findViewById(R.id.sw_local_frame);
        localFrame.setSelected(prefs.getFrameColor("local"));
        localFrame.setOnPick(id -> prefs.setFrameColor("local", id));
        setupSwitch(R.id.sw_strip, prefs.isSourceStrip(), prefs::setSourceStrip);
        findViewById(R.id.btn_clear_favorites).setOnClickListener(v -> {
            PhotoMarksStore store = PhotoMarksStore.get(this);
            store.marks().clearFavorites();
            store.save();
            showMarkCounts();
        });
        findViewById(R.id.btn_show_hidden).setOnClickListener(v -> {
            PhotoMarksStore store = PhotoMarksStore.get(this);
            store.marks().clearHidden();
            store.save();
            showMarkCounts();
            refreshFolderInfo();
        });

        setupDescriptions();
        setupClouds();
    }

    // ---------------------------------------------------------------- photo descriptions

    private final GenAiDescriber genAiProbe = new GenAiDescriber();
    private final ExecutorService describeIo = Executors.newSingleThreadExecutor();
    private final AtomicBoolean describeCancel = new AtomicBoolean();
    private boolean preparing;

    private void setupDescriptions() {
        setupSwitch(R.id.sw_describe_hebrew, prefs.isDescribeHebrew(), prefs::setDescribeHebrew);
        findViewById(R.id.btn_prepare_descriptions).setOnClickListener(v -> {
            if (preparing) describeCancel.set(true); else prepareDescriptions();
        });
    }

    /** "12 of 200 photos described · labels only" – counted off the main thread. */
    private void refreshDescriptionStatus() {
        describeIo.execute(() -> {
            List<Photo> photos = PhotoMarksStore.get(this).marks().visible(PhotoRepository.loadAll(this));
            List<String> keys = new ArrayList<>();
            for (Photo p : photos) keys.add(p.key());
            int described = DescriptionStore.get(this).cache().countDescribed(keys, false);
            int engine = genAiProbe.status(this);
            int engineText = engine == FeatureStatus.AVAILABLE ? R.string.describe_engine_sentences
                    : engine == FeatureStatus.DOWNLOADABLE || engine == FeatureStatus.DOWNLOADING
                    ? R.string.describe_engine_downloadable : R.string.describe_engine_labels;
            runOnUiThread(() -> {
                if (!preparing) {
                    ((TextView) findViewById(R.id.txt_describe_status)).setText(
                            getString(R.string.describe_status, described, keys.size(), getString(engineText)));
                }
            });
        });
    }

    /**
     * Runs the description job in the foreground: Gemini Nano sentences are only allowed while
     * PhotoDream is the top app, so the screen stays on until it is finished (or cancelled).
     */
    private void prepareDescriptions() {
        preparing = true;
        describeCancel.set(false);
        MaterialButton button = findViewById(R.id.btn_prepare_descriptions);
        button.setText(R.string.describe_cancel);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView status = findViewById(R.id.txt_describe_status);
        status.setText(R.string.describe_working);

        describeIo.execute(() -> {
            try {
                if (genAiProbe.status(this) == FeatureStatus.DOWNLOADABLE) downloadGeminiNano(status);
                DescriptionJob.run(this, true, Integer.MAX_VALUE, describeCancel, (done, total) ->
                        runOnUiThread(() -> status.setText(getString(R.string.describe_progress, done, total))));
            } finally {
                runOnUiThread(() -> {
                    preparing = false;
                    button.setText(R.string.describe_prepare);
                    getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    refreshDescriptionStatus();
                });
            }
        });
    }

    /** Waits (up to 15 min) for the on-device Gemini Nano model to download. */
    private void downloadGeminiNano(TextView status) {
        CountDownLatch done = new CountDownLatch(1);
        runOnUiThread(() -> status.setText(R.string.describe_downloading));
        genAiProbe.download(this, new DownloadCallback() {
            @Override public void onDownloadStarted(long bytesToDownload) { }
            @Override public void onDownloadProgress(long bytesDownloaded) { }
            @Override public void onDownloadCompleted() { done.countDown(); }
            @Override public void onDownloadFailed(GenAiException e) { done.countDown(); }
        });
        try {
            done.await(15, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void showMarkCounts() {
        PhotoMarks m = PhotoMarksStore.get(this).marks();
        ((TextView) findViewById(R.id.txt_favorites)).setText(getString(R.string.marks_favorites, m.favoriteCount()));
        ((TextView) findViewById(R.id.txt_hidden)).setText(getString(R.string.marks_hidden, m.hiddenCount()));
        findViewById(R.id.btn_clear_favorites).setEnabled(m.favoriteCount() > 0);
        findViewById(R.id.btn_show_hidden).setEnabled(m.hiddenCount() > 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showMarkCounts();
        refreshFolderInfo();
        refreshDescriptionStatus();
        DescriptionWorker.schedule(this, ExistingWorkPolicy.KEEP);      // label new photos while charging
        refreshClouds();
    }

    @Override
    protected void onDestroy() {
        describeCancel.set(true);
        describeIo.shutdownNow();
        genAiProbe.close();
        io.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- clouds

    private CloudSourceView oneDriveView, googleDriveView;
    private CloudProvider pendingConnect;   // waiting for Google's sign-in screen

    /** Google Play services screens (account picker, consent) return here. */
    private final ActivityResultLauncher<IntentSenderRequest> cloudResolution =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), r -> {
                if (pendingConnect == null) return;
                CloudProvider p = pendingConnect;
                pendingConnect = null;
                p.onResolutionResult(this, r.getResultCode(), r.getData(), err -> onConnected(p, err));
            });

    private void setupClouds() {
        CloudSourceView.Host host = new CloudSourceView.Host() {
            @Override public void connect(CloudProvider p) {
                pendingConnect = p;
                p.connect(PhotosSettingsActivity.this, cloudResolution, err -> onConnected(p, err));
            }
            @Override public void onSyncRequested() { askNotificationPermission(); }
            @Override public void onPhotosChanged() { refreshFolderInfo(); }
        };
        oneDriveView = findViewById(R.id.source_onedrive);
        oneDriveView.bind(this, CloudProviders.ONEDRIVE, host);
        googleDriveView = findViewById(R.id.source_gdrive);
        googleDriveView.bind(this, CloudProviders.GOOGLE_DRIVE, host);

        // Ask Android/Samsung not to restrict our background internet (battery optimisation)
        findViewById(R.id.btn_battery).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
    }

    private void onConnected(CloudProvider p, String error) {
        pendingConnect = null;
        String msg = error == null
                ? getString(R.string.cloud_connected_as, p.accountName(this))
                : getString(R.string.cloud_connect_failed, p.displayName(this), error);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        refreshClouds();
    }

    private void refreshClouds() {
        oneDriveView.refresh();
        googleDriveView.refresh();
        boolean anyConnected = false;
        for (CloudProvider p : CloudProviders.all()) anyConnected |= p.isSignedIn(this);
        boolean unrestricted = getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations(getPackageName());
        int vis = anyConnected && !unrestricted ? View.VISIBLE : View.GONE;
        findViewById(R.id.btn_battery).setVisibility(vis);
        findViewById(R.id.txt_battery_hint).setVisibility(vis);
    }

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    /** The sync progress notification needs this on Android 13+ (sync works without it). */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ---------------------------------------------------------------- phone folders

    private void refreshFolderInfo() {
        List<Uri> folders = prefs.getLocalFolders();
        txtFolder.setVisibility(folders.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.group_local).setVisibility(folders.isEmpty() ? View.GONE : View.VISIBLE);
        txtCount.setText(R.string.counting);
        io.execute(() -> {
            // count each folder and each source off the main thread (listing can be slow)
            List<Integer> folderCounts = new ArrayList<>();
            for (Uri f : folders) folderCounts.add(new LocalFolderSource(f).listPhotos(this).size());

            // "Phone 120 · OneDrive 200 (hidden) · Google Drive 0" – all phone folders add up to one "Phone"
            Map<String, Integer> perSource = new LinkedHashMap<>();
            for (PhotoSource s : PhotoRepository.allSources(this)) {
                perSource.merge(s.id(), s.listPhotos(this).size(), Integer::sum);
            }
            StringBuilder summary = new StringBuilder();
            for (Map.Entry<String, Integer> e : perSource.entrySet()) {
                if (summary.length() > 0) summary.append(" · ");
                summary.append(sourceLabel(e.getKey())).append(' ').append(e.getValue());
                if (!prefs.isSourceEnabled(e.getKey())) {
                    summary.append(" (").append(getString(R.string.source_hidden)).append(')');
                }
            }
            int n = PhotoRepository.loadAll(this).size();
            String found = getResources().getQuantityString(R.plurals.photo_count, n, n);
            runOnUiThread(() -> {
                showFolderRows(folders, folderCounts);
                txtCount.setText(summary.length() > 0
                        ? getString(R.string.count_with_sources, found, summary.toString())
                        : found);
            });
        });
    }

    /** One row per phone folder: "DCIM/Camera · 120" and a remove button. */
    private void showFolderRows(List<Uri> folders, List<Integer> counts) {
        listFolders.removeAllViews();
        for (int i = 0; i < folders.size(); i++) {
            Uri folder = folders.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            // "primary:DCIM/Camera" -> "DCIM/Camera"
            String id = DocumentsContract.getTreeDocumentId(folder);
            String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            if (name.isEmpty()) name = id;
            TextView label = new TextView(this);
            label.setText(getString(R.string.folder_row, name, counts.get(i)));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton remove = new MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle);
            remove.setText("\u2715");   // ✕ – a symbol, described for TalkBack below
            remove.setContentDescription(getString(R.string.remove_folder_named, name));
            remove.setMinimumWidth(0);
            remove.setOnClickListener(v -> removeFolder(folder));
            row.addView(remove, new LinearLayout.LayoutParams(
                    (int) (48 * getResources().getDisplayMetrics().density),
                    (int) (48 * getResources().getDisplayMetrics().density)));
            listFolders.addView(row);
        }
    }

    private String sourceLabel(String sourceId) {
        return "local".equals(sourceId) ? getString(R.string.source_phone)
                : CloudProviders.get(sourceId).displayName(this);
    }

    /**
     * Forget one phone folder: give back the read permission Android kept for us
     * (otherwise the system keeps a limited list of them) and drop it from the list.
     * Other folders and the cloud sources are not touched.
     */
    private void removeFolder(Uri folder) {
        try {
            getContentResolver().releasePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // permission was already gone – nothing to release
        }
        prefs.removeLocalFolder(folder);
        refreshFolderInfo();
    }
}
