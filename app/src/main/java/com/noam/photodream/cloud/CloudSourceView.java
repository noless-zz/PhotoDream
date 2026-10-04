package com.noam.photodream.cloud;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.noam.photodream.R;

import java.util.List;

/**
 * The settings section of one cloud source: connect / disconnect, folder,
 * sync options, "Sync now" and live status. Used once per {@link CloudProvider}.
 */
public class CloudSourceView extends LinearLayout {

    /** What the screen does when the user taps Connect (needs the activity's launcher). */
    public interface Host {
        void connect(CloudProvider provider);
        void onSyncRequested();          // e.g. ask for notification permission
        void onPhotosChanged();          // refresh the photo counter
    }

    private static final int STEP = 50;

    private CloudProvider provider;
    private Host host;
    private CloudPrefs prefs;

    private TextView status, folder, maxLabel, lastSync;
    private MaterialButton connect, sync;
    private View group;

    public CloudSourceView(Context context) { this(context, null); }

    public CloudSourceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_cloud_source, this, true);
    }

    public void bind(AppCompatActivity activity, CloudProvider p, Host h) {
        provider = p;
        host = h;
        prefs = new CloudPrefs(activity, p);

        ((TextView) findViewById(R.id.cs_title)).setText(p.displayName(activity));
        status = findViewById(R.id.cs_status);
        folder = findViewById(R.id.cs_folder);
        maxLabel = findViewById(R.id.cs_max_label);
        lastSync = findViewById(R.id.cs_last_sync);
        connect = findViewById(R.id.cs_connect);
        sync = findViewById(R.id.cs_sync);
        group = findViewById(R.id.cs_group);

        connect.setOnClickListener(v -> {
            if (p.isSignedIn(activity)) {
                new MaterialAlertDialogBuilder(activity)
                        .setMessage(activity.getString(R.string.cloud_disconnect_confirm, p.displayName(activity)))
                        .setPositiveButton(R.string.cloud_disconnect, (d, w) -> {
                            CloudScheduler.disconnect(activity, p);
                            refresh();
                            host.onPhotosChanged();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else if (!p.isConfigured(activity)) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.cloud_not_configured_title)
                        .setMessage(p.notConfiguredMessage(activity))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                host.connect(p);
            }
        });

        findViewById(R.id.cs_folder_btn).setOnClickListener(v -> {
            host.onSyncRequested();
            activity.startActivity(CloudFolderActivity.intent(activity, p));
        });

        MaterialSwitch subfolders = findViewById(R.id.cs_subfolders);
        subfolders.setChecked(prefs.isIncludeSubfolders());
        subfolders.setOnCheckedChangeListener((b, on) -> prefs.setIncludeSubfolders(on));

        MaterialSwitch wifi = findViewById(R.id.cs_wifi);
        wifi.setChecked(prefs.isWifiChargingOnly());
        wifi.setOnCheckedChangeListener((b, on) -> {
            prefs.setWifiChargingOnly(on);
            CloudScheduler.schedule(activity, p);
        });

        // photos to keep: 50..1000 in steps of 50
        SeekBar max = findViewById(R.id.cs_max);
        max.setProgress(Math.max(0, Math.min(19, prefs.getMaxPhotos() / STEP - 1)));
        maxLabel.setText(activity.getString(R.string.cloud_max_photos, prefs.getMaxPhotos()));
        max.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int n = (progress + 1) * STEP;
                maxLabel.setText(activity.getString(R.string.cloud_max_photos, n));
                prefs.setMaxPhotos(n);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        sync.setOnClickListener(v -> {
            host.onSyncRequested();
            CloudScheduler.syncNow(activity, p);
            Toast.makeText(activity, R.string.cloud_sync_started, Toast.LENGTH_SHORT).show();
        });

        // live status of "Sync now"
        WorkManager.getInstance(activity).getWorkInfosForUniqueWorkLiveData(CloudScheduler.nowName(p))
                .observe(activity, this::showWork);

        refresh();
    }

    private void showWork(List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) return;
        WorkInfo info = infos.get(0);
        WorkInfo.State state = info.getState();
        Context c = getContext();
        if (state == WorkInfo.State.RUNNING) {
            int done = info.getProgress().getInt(CloudSyncWorker.PROGRESS_DONE, 0);
            int total = info.getProgress().getInt(CloudSyncWorker.PROGRESS_TOTAL, 0);
            lastSync.setText(total > 0 ? c.getString(R.string.cloud_syncing_progress, done, total)
                    : c.getString(R.string.cloud_syncing));
        } else if (state == WorkInfo.State.ENQUEUED) {
            lastSync.setText(R.string.cloud_waiting);
        } else if (state.isFinished()) {
            refresh();
            host.onPhotosChanged();
        }
    }

    /** Re-read state (after connecting, returning from the folder browser, …). */
    public void refresh() {
        if (provider == null) return;
        Context c = getContext();
        boolean signedIn = provider.isSignedIn(c);
        status.setText(signedIn
                ? c.getString(R.string.cloud_connected_as, provider.accountName(c))
                : c.getString(R.string.cloud_not_connected));
        connect.setText(signedIn ? c.getString(R.string.cloud_disconnect)
                : c.getString(R.string.cloud_connect, provider.displayName(c)));
        group.setVisibility(signedIn ? VISIBLE : GONE);

        folder.setText(prefs.getFolderId() == null ? c.getString(R.string.cloud_no_folder) : prefs.getFolderPath());
        sync.setEnabled(prefs.getFolderId() != null);

        String msg = prefs.getLastSyncMessage();
        lastSync.setText(msg.isEmpty() ? c.getString(R.string.cloud_never_synced)
                : c.getString(R.string.cloud_last_sync, msg));
    }
}
