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
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;


import com.google.android.material.materialswitch.MaterialSwitch;
import com.noam.photodream.cloud.CloudProvider;
import com.noam.photodream.cloud.CloudProviders;
import com.noam.photodream.cloud.CloudSourceView;
import com.noam.photodream.source.PhotoSource;

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
        if (savedInstanceState == null && WelcomeActivity.shouldShow(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
        }
        findViewById(R.id.btn_show_intro).setOnClickListener(v ->
                startActivity(new Intent(this, WelcomeActivity.class)));
        prefs = new Prefs(this);

        txtFolder = findViewById(R.id.txt_folder);
        txtCount = findViewById(R.id.txt_count);
        txtInterval = findViewById(R.id.txt_interval);

        Button choose = findViewById(R.id.btn_choose_folder);
        choose.setOnClickListener(v -> pickFolder.launch(prefs.getLocalFolder()));
        setupSwitch(R.id.sw_local_show, prefs.isSourceEnabled("local"), on -> {
            prefs.setSourceEnabled("local", on);
            refreshFolderInfo();
        });
        findViewById(R.id.btn_remove_folder).setOnClickListener(v -> removeFolder());

        setupClouds();
        setupMode();
        setupInterval();
        setupTransition();
        setupEntry();
        setupSeek(R.id.seek_max_cards, R.id.txt_max_cards,
                n -> getResources().getQuantityString(R.plurals.max_cards_label, n, n),
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
        refreshClouds();
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
                p.connect(SettingsActivity.this, cloudResolution, err -> onConnected(p, err));
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
        findViewById(R.id.group_local).setVisibility(folder == null ? View.GONE : View.VISIBLE);
        txtCount.setText(R.string.counting);
        io.execute(() -> {
            // "Phone 120 · OneDrive 200 (hidden) · Google Drive 0"
            StringBuilder perSource = new StringBuilder();
            for (PhotoSource s : PhotoRepository.allSources(this)) {
                if (perSource.length() > 0) perSource.append(" · ");
                perSource.append(sourceLabel(s.id())).append(' ').append(s.listPhotos(this).size());
                if (!prefs.isSourceEnabled(s.id())) perSource.append(" (").append(getString(R.string.source_hidden)).append(')');
            }
            int n = PhotoRepository.loadAll(this).size();
            String found = getResources().getQuantityString(R.plurals.photo_count, n, n);
            runOnUiThread(() -> txtCount.setText(perSource.length() > 0
                    ? getString(R.string.count_with_sources, found, perSource.toString())
                    : found));
        });
    }

    private String sourceLabel(String sourceId) {
        return "local".equals(sourceId) ? getString(R.string.source_phone)
                : CloudProviders.get(sourceId).displayName(this);
    }

    /**
     * Forget the phone folder: give back the read permission Android kept for us
     * (otherwise the system keeps a limited list of them) and clear the setting.
     * Cloud sources are not touched.
     */
    private void removeFolder() {
        Uri folder = prefs.getLocalFolder();
        if (folder == null) return;
        try {
            getContentResolver().releasePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // permission was already gone – nothing to release
        }
        prefs.setLocalFolder(null);
        refreshFolderInfo();
    }

    private void setupInterval() {
        SeekBar seek = findViewById(R.id.seek_interval);
        seek.setProgress(prefs.getIntervalSeconds() - MIN_INTERVAL);
        txtInterval.setText(getString(R.string.interval_label, prefs.getIntervalSeconds()));
        seek.setContentDescription(txtInterval.getText());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int sec = progress + MIN_INTERVAL;
                txtInterval.setText(getString(R.string.interval_label, sec));
                s.setContentDescription(txtInterval.getText());
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
        setupSeek(seekId, labelId, n -> getString(formatRes, n), min, max, value, setter);
    }

    /** Same, but the label text comes from a function (needed for plurals). */
    private void setupSeek(int seekId, int labelId, java.util.function.IntFunction<String> format,
                           int min, int max, int value, IntSetter setter) {
        SeekBar seek = findViewById(seekId);
        TextView label = findViewById(labelId);
        seek.setMax(max - min);
        int v = Math.max(min, Math.min(max, value));
        seek.setProgress(v - min);
        label.setText(format.apply(v));
        seek.setContentDescription(label.getText());   // TalkBack reads the label with the slider
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int n = progress + min;
                label.setText(format.apply(n));
                s.setContentDescription(label.getText());
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
