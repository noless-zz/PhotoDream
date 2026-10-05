package com.noam.photodream;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.noam.photodream.alarm.Alarm;
import com.noam.photodream.alarm.AlarmListActivity;
import com.noam.photodream.alarm.AlarmStore;
import com.noam.photodream.cloud.CloudProvider;
import com.noam.photodream.cloud.CloudProviders;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher screen: a short list of categories (Photos, Display, Wake-up alarms, About),
 * each opening its own screen, plus the two buttons you need most: Preview and the
 * system screen-saver settings.
 */
public class SettingsActivity extends SettingsScreen {

    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null && WelcomeActivity.shouldShow(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
        }

        findViewById(R.id.btn_update).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateChecker.DOWNLOAD_URL)));
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.cat_photos).setOnClickListener(v ->
                startActivity(new Intent(this, PhotosSettingsActivity.class)));
        findViewById(R.id.cat_display).setOnClickListener(v ->
                startActivity(new Intent(this, DisplaySettingsActivity.class)));
        findViewById(R.id.cat_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        findViewById(R.id.cat_alarms).setOnClickListener(v ->
                startActivity(new Intent(this, AlarmListActivity.class)));

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
        refreshSummaries();
        showUpdateBanner();
        // at most once a day: ask GitHub in the background, then show the banner if there is news
        io.execute(() -> {
            UpdateChecker.checkIfDue(this);
            runOnUiThread(this::showUpdateBanner);
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void showUpdateBanner() {
        String version = UpdateChecker.availableVersion(this);
        View banner = findViewById(R.id.banner_update);
        banner.setVisibility(version == null ? View.GONE : View.VISIBLE);
        if (version != null) {
            ((TextView) findViewById(R.id.txt_update)).setText(getString(R.string.update_available, version));
        }
    }

    /** One-line summaries under each category title; cheap (no photo listing). */
    private void refreshSummaries() {
        List<String> parts = new ArrayList<>();
        int folders = prefs.getLocalFolders().size();
        if (folders > 0) parts.add(getResources().getQuantityString(R.plurals.summary_phone_folders, folders, folders));
        for (CloudProvider p : CloudProviders.all()) {
            if (p.isSignedIn(this)) parts.add(p.displayName(this));
        }
        ((TextView) findViewById(R.id.sum_photos)).setText(parts.isEmpty()
                ? getString(R.string.summary_photos_none) : String.join(" · ", parts));

        String mode = getString(prefs.getDisplayMode() == Prefs.DisplayMode.TABLE
                ? R.string.mode_table : R.string.mode_single);
        ((TextView) findViewById(R.id.sum_display)).setText(
                getString(R.string.summary_display, mode, prefs.getIntervalSeconds()));

        int alarmsOn = 0;
        for (Alarm a : new AlarmStore(this).list()) if (a.enabled) alarmsOn++;
        ((TextView) findViewById(R.id.sum_alarms)).setText(alarmsOn == 0
                ? getString(R.string.summary_alarms_none)
                : getResources().getQuantityString(R.plurals.summary_alarms_on, alarmsOn, alarmsOn));

        ((TextView) findViewById(R.id.sum_about)).setText(
                getString(R.string.about_version, AboutActivity.versionName(this)));
    }
}
