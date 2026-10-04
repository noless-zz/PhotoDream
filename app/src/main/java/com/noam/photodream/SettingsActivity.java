package com.noam.photodream;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.noam.photodream.onedrive.OneDriveAuth;
import com.noam.photodream.onedrive.OneDriveConfig;
import com.noam.photodream.onedrive.OneDriveFolderActivity;
import com.noam.photodream.onedrive.OneDrivePrefs;
import com.noam.photodream.onedrive.OneDriveScheduler;
import com.noam.photodream.onedrive.OneDriveSyncWorker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Launcher screen: choose photos and tune the slideshow. */
public class SettingsActivity extends AppCompatActivity {

    private static final int MIN_INTERVAL = 5;

    private Prefs prefs;
    private TextView txtFolder, txtCount, txtInterval;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** System folder picker. We keep read access permanently so the screensaver can use it later. */
    private final ActivityResultLauncher<Uri> pickFolder =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.setLocalFolder(uri);
                refreshFolderInfo();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        txtFolder = findViewById(R.id.txt_folder);
        txtCount = findViewById(R.id.txt_count);
        txtInterval = findViewById(R.id.txt_interval);

        Button choose = findViewById(R.id.btn_choose_folder);
        choose.setOnClickListener(v -> pickFolder.launch(prefs.getLocalFolder()));

        setupOneDrive();
        setupMode();
        setupInterval();
        setupTransition();
        setupEntry();
        setupSeek(R.id.seek_max_cards, R.id.txt_max_cards, R.string.max_cards_label,
                3, 20, prefs.getTableMaxCards(), prefs::setTableMaxCards);
        setupSeek(R.id.seek_card_size, R.id.txt_card_size, R.string.card_size_label,
                30, 80, prefs.getTableCardSize(), prefs::setTableCardSize);
        setupSeek(R.id.seek_rotation, R.id.txt_rotation, R.string.rotation_label,
                0, 30, prefs.getTableRotation(), prefs::setTableRotation);
        setupSwitch(R.id.sw_drift, prefs.isTableDrift(), prefs::setTableDrift);
        setupSwitch(R.id.sw_shuffle, prefs.isShuffle(), prefs::setShuffle);
        setupSwitch(R.id.sw_crop, prefs.isCrop(), prefs::setCrop);
        setupSwitch(R.id.sw_clock, prefs.isShowClock(), prefs::setShowClock);
        setupSwitch(R.id.sw_dim, prefs.isDim(), prefs::setDim);

        findViewById(R.id.btn_preview).setOnClickListener(v ->
                startActivity(new Intent(this, PreviewActivity.class)));

        findViewById(R.id.btn_system_settings).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                Toast.makeText(this, R.string.system_hint, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFolderInfo();
        refreshOneDrive();
    }

    // ---------------------------------------------------------------- OneDrive

    private static final int ONEDRIVE_STEP = 50;

    private void setupOneDrive() {
        OneDrivePrefs od = new OneDrivePrefs(this);

        findViewById(R.id.btn_onedrive_connect).setOnClickListener(v -> {
            if (OneDriveAuth.isSignedIn(this)) {
                new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.onedrive_disconnect_confirm)
                        .setPositiveButton(R.string.onedrive_disconnect, (d, w) -> {
                            OneDriveScheduler.disconnect(this);
                            refreshOneDrive();
                            refreshFolderInfo();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else if (!OneDriveConfig.isConfigured(this)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.onedrive_not_configured_title)
                        .setMessage(R.string.onedrive_not_configured)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                OneDriveAuth.startSignIn(this);
            }
        });

        setupSwitch(R.id.sw_onedrive_subfolders, od.isIncludeSubfolders(), b -> {
            od.setIncludeSubfolders(b);
        });
        setupSwitch(R.id.sw_onedrive_wifi, od.isWifiChargingOnly(), b -> {
            od.setWifiChargingOnly(b);
            OneDriveScheduler.schedule(this);
        });
        // 50..1000 in steps of 50
        TextView maxLabel = findViewById(R.id.txt_onedrive_max);
        maxLabel.setText(getString(R.string.onedrive_max_photos, od.getMaxPhotos()));
        SeekBar maxSeek = findViewById(R.id.seek_onedrive_max);
        maxSeek.setMax(19);
        maxSeek.setProgress(Math.max(0, Math.min(19, od.getMaxPhotos() / ONEDRIVE_STEP - 1)));
        maxSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int n = (progress + 1) * ONEDRIVE_STEP;
                maxLabel.setText(getString(R.string.onedrive_max_photos, n));
                od.setMaxPhotos(n);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        findViewById(R.id.btn_onedrive_sync).setOnClickListener(v -> {
            askNotificationPermission();
            OneDriveScheduler.syncNow(this);
            Toast.makeText(this, R.string.onedrive_sync_started, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_onedrive_folder).setOnClickListener(v -> {
            askNotificationPermission();
            startActivity(new Intent(this, OneDriveFolderActivity.class));
        });

        // Ask Android/Samsung not to restrict our background internet (battery optimisation)
        findViewById(R.id.btn_onedrive_battery).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });

        // live status of the "Sync now" job
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(OneDriveScheduler.NOW)
                .observe(this, infos -> {
                    TextView last = findViewById(R.id.txt_onedrive_last_sync);
                    if (infos == null || infos.isEmpty()) return;
                    WorkInfo info = infos.get(0);
                    WorkInfo.State state = info.getState();
                    if (state == WorkInfo.State.RUNNING) {
                        int done = info.getProgress().getInt(OneDriveSyncWorker.PROGRESS_DONE, 0);
                        int total = info.getProgress().getInt(OneDriveSyncWorker.PROGRESS_TOTAL, 0);
                        if (total > 0) last.setText(getString(R.string.onedrive_syncing_progress, done, total));
                        else last.setText(R.string.onedrive_syncing);
                    } else if (state == WorkInfo.State.ENQUEUED) {
                        last.setText(R.string.onedrive_waiting);
                    } else if (state.isFinished()) {
                        refreshOneDrive();
                        refreshFolderInfo();
                    }
                });
    }

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    /** The sync progress notification needs this on Android 13+ (sync works without it). */
    private void askNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void refreshOneDrive() {
        boolean signedIn = OneDriveAuth.isSignedIn(this);
        OneDrivePrefs od = new OneDrivePrefs(this);
        TextView status = findViewById(R.id.txt_onedrive_status);
        Button connect = findViewById(R.id.btn_onedrive_connect);
        status.setText(signedIn
                ? getString(R.string.onedrive_connected_as, OneDriveAuth.accountName(this))
                : getString(R.string.onedrive_not_connected));
        connect.setText(signedIn ? R.string.onedrive_disconnect : R.string.onedrive_connect);
        findViewById(R.id.group_onedrive).setVisibility(signedIn ? View.VISIBLE : View.GONE);

        TextView folder = findViewById(R.id.txt_onedrive_folder);
        folder.setText(od.getFolderId() == null ? getString(R.string.onedrive_no_folder) : od.getFolderPath());
        findViewById(R.id.btn_onedrive_sync).setEnabled(od.getFolderId() != null);

        boolean unrestricted = getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations(getPackageName());
        findViewById(R.id.btn_onedrive_battery).setVisibility(unrestricted ? View.GONE : View.VISIBLE);
        findViewById(R.id.txt_onedrive_battery_hint).setVisibility(unrestricted ? View.GONE : View.VISIBLE);

        TextView last = findViewById(R.id.txt_onedrive_last_sync);
        String msg = od.getLastSyncMessage();
        last.setText(msg.isEmpty() ? getString(R.string.onedrive_never_synced)
                : getString(R.string.onedrive_last_sync, msg));
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void refreshFolderInfo() {
        Uri folder = prefs.getLocalFolder();
        if (folder == null) {
            txtFolder.setText(R.string.no_folder);
        } else {
            // "primary:DCIM/Camera" -> "DCIM/Camera"
            String id = DocumentsContract.getTreeDocumentId(folder);
            txtFolder.setText(id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
        }
        txtCount.setText(R.string.counting);
        io.execute(() -> {
            int n = PhotoRepository.loadAll(this).size();
            runOnUiThread(() -> txtCount.setText(getString(R.string.photo_count, n)));
        });
    }

    private void setupInterval() {
        SeekBar seek = findViewById(R.id.seek_interval);
        seek.setProgress(prefs.getIntervalSeconds() - MIN_INTERVAL);
        txtInterval.setText(getString(R.string.interval_label, prefs.getIntervalSeconds()));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int sec = progress + MIN_INTERVAL;
                txtInterval.setText(getString(R.string.interval_label, sec));
                prefs.setIntervalSeconds(sec);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
    }

    private void setupTransition() {
        RadioGroup group = findViewById(R.id.group_transition);
        switch (prefs.getTransition()) {
            case FADE: group.check(R.id.radio_fade); break;
            case KEN_BURNS: group.check(R.id.radio_ken_burns); break;
            default: group.check(R.id.radio_slide);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == R.id.radio_fade) prefs.setTransition(SlideshowView.Transition.FADE);
            else if (checkedId == R.id.radio_ken_burns) prefs.setTransition(SlideshowView.Transition.KEN_BURNS);
            else prefs.setTransition(SlideshowView.Transition.SLIDE);
        });
    }

    /** One photo at a time vs. photo table; shows only the options that apply. */
    private void setupMode() {
        RadioGroup group = findViewById(R.id.group_mode);
        group.check(prefs.getDisplayMode() == Prefs.DisplayMode.TABLE
                ? R.id.radio_mode_table : R.id.radio_mode_single);
        applyModeVisibility();
        group.setOnCheckedChangeListener((g, checkedId) -> {
            prefs.setDisplayMode(checkedId == R.id.radio_mode_table
                    ? Prefs.DisplayMode.TABLE : Prefs.DisplayMode.SINGLE);
            applyModeVisibility();
        });
    }

    private void applyModeVisibility() {
        boolean table = prefs.getDisplayMode() == Prefs.DisplayMode.TABLE;
        findViewById(R.id.group_single_only).setVisibility(table ? View.GONE : View.VISIBLE);
        findViewById(R.id.group_table_only).setVisibility(table ? View.VISIBLE : View.GONE);
    }

    private void setupEntry() {
        RadioGroup group = findViewById(R.id.group_entry);
        switch (prefs.getTableEntry()) {
            case DROP: group.check(R.id.radio_entry_drop); break;
            case FLY_IN: group.check(R.id.radio_entry_fly); break;
            case POP: group.check(R.id.radio_entry_pop); break;
            case FADE: group.check(R.id.radio_entry_fade); break;
            default: group.check(R.id.radio_entry_random);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == R.id.radio_entry_drop) prefs.setTableEntry(Prefs.Entry.DROP);
            else if (checkedId == R.id.radio_entry_fly) prefs.setTableEntry(Prefs.Entry.FLY_IN);
            else if (checkedId == R.id.radio_entry_pop) prefs.setTableEntry(Prefs.Entry.POP);
            else if (checkedId == R.id.radio_entry_fade) prefs.setTableEntry(Prefs.Entry.FADE);
            else prefs.setTableEntry(Prefs.Entry.RANDOM);
        });
    }

    private interface IntSetter { void set(int value); }

    /** A SeekBar from min..max with a "label: N" text above it. */
    private void setupSeek(int seekId, int labelId, int formatRes, int min, int max,
                           int value, IntSetter setter) {
        SeekBar seek = findViewById(seekId);
        TextView label = findViewById(labelId);
        seek.setMax(max - min);
        int v = Math.max(min, Math.min(max, value));
        seek.setProgress(v - min);
        label.setText(getString(formatRes, v));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int n = progress + min;
                label.setText(getString(formatRes, n));
                setter.set(n);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
    }

    private interface BoolSetter { void set(boolean value); }

    private void setupSwitch(int id, boolean value, BoolSetter setter) {
        MaterialSwitch sw = findViewById(id);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, checked) -> setter.set(checked));
    }
}
