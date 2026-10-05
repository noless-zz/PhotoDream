package com.noam.photodream;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null && WelcomeActivity.shouldShow(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
        }

        findViewById(R.id.cat_photos).setOnClickListener(v ->
                startActivity(new Intent(this, PhotosSettingsActivity.class)));
        findViewById(R.id.cat_display).setOnClickListener(v ->
                startActivity(new Intent(this, DisplaySettingsActivity.class)));
        findViewById(R.id.cat_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        // Wake-up alarms arrive in a later version; the row is a visible placeholder for now
        View alarms = findViewById(R.id.cat_alarms);
        alarms.setAlpha(0.6f);
        alarms.setOnClickListener(v -> Toast.makeText(this, R.string.summary_alarms_soon, Toast.LENGTH_SHORT).show());
        ((TextView) findViewById(R.id.sum_alarms)).setText(R.string.summary_alarms_soon);

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

        ((TextView) findViewById(R.id.sum_about)).setText(
                getString(R.string.about_version, AboutActivity.versionName(this)));
    }
}
