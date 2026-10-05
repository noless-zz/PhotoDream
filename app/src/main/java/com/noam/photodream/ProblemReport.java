package com.noam.photodream;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.noam.photodream.cloud.CloudPrefs;
import com.noam.photodream.cloud.CloudProvider;
import com.noam.photodream.cloud.CloudProviders;
import com.noam.photodream.source.PhotoSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the text file students send to the teacher when something breaks.
 * Everything goes through {@link ReportRedactor}, so it contains no tokens, e-mails
 * or photo names. Call {@link #build} from a background thread (it lists photos and reads logcat).
 */
public final class ProblemReport {

    private static final String TAG = "ProblemReport";

    private ProblemReport() { }

    /** The report as text (already redacted). */
    public static String build(Context ctx) {
        Prefs prefs = new Prefs(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("PhotoDream problem report\n\n");
        sb.append("App version: ").append(AboutActivity.versionName(ctx)).append('\n');
        sb.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Language: ").append(java.util.Locale.getDefault()).append("\n\n");

        sb.append("Display settings\n");
        sb.append("  mode=").append(prefs.getDisplayMode())
                .append(", interval=").append(prefs.getIntervalSeconds()).append("s")
                .append(", transition=").append(prefs.getTransition())
                .append(", shuffle=").append(prefs.isShuffle())
                .append(", crop=").append(prefs.isCrop())
                .append(", clock=").append(prefs.isShowClock())
                .append(", dim=").append(prefs.isDim()).append('\n');
        sb.append("  table: cards=").append(prefs.getTableMaxCards())
                .append(", size=").append(prefs.getTableCardSize()).append('%')
                .append(", tilt=").append(prefs.getTableRotation())
                .append(", drift=").append(prefs.isTableDrift())
                .append(", entry=").append(prefs.getTableEntry()).append("\n\n");

        sb.append("Sources\n");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PhotoSource s : PhotoRepository.allSources(ctx)) {
            counts.merge(s.id(), s.listPhotos(ctx).size(), Integer::sum);
        }
        sb.append("  phone: ").append(prefs.getLocalFolders().size()).append(" folder(s), ")
                .append(counts.containsKey("local") ? counts.get("local") : 0).append(" photos, shown=")
                .append(prefs.isSourceEnabled("local")).append('\n');
        for (CloudProvider p : CloudProviders.all()) {
            CloudPrefs cp = new CloudPrefs(ctx, p);
            sb.append("  ").append(p.id()).append(": connected=").append(p.isSignedIn(ctx))
                    .append(", folder chosen=").append(cp.getFolderId() != null)
                    .append(", ").append(counts.containsKey(p.id()) ? counts.get(p.id()) : 0).append(" photos on phone")
                    .append(", shown=").append(prefs.isSourceEnabled(p.id()))
                    .append(", last sync: ").append(cp.getLastSyncMessage()).append('\n');
        }

        sb.append("\nPermissions\n");
        sb.append("  notifications allowed: ").append(NotificationManagerCompat.from(ctx).areNotificationsEnabled()).append('\n');
        sb.append("  battery unrestricted: ")
                .append(ctx.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(ctx.getPackageName())).append('\n');
        if (Build.VERSION.SDK_INT >= 31) {
            sb.append("  exact alarms allowed: ")
                    .append(ctx.getSystemService(AlarmManager.class).canScheduleExactAlarms()).append('\n');
        }

        sb.append("\nRecent app log\n").append(recentLog());
        return ReportRedactor.redact(sb.toString());
    }

    /** Writes the report to cache/reports/ and returns a share intent (WhatsApp, Gmail, …). */
    public static Intent shareIntent(Context ctx) throws IOException {
        File dir = new File(ctx.getCacheDir(), "reports");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File file = new File(dir, "photodream-report.txt");
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(build(ctx).getBytes(StandardCharsets.UTF_8));
        }
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.report_subject));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(send, ctx.getString(R.string.about_report));
    }

    /** The app's own last 500 log lines. Reading your own log needs no permission. */
    private static String recentLog() {
        try {
            Process proc = new ProcessBuilder("logcat", "-d", "-t", "500", "--pid=" + android.os.Process.myPid())
                    .redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "No log available", e);
            return "(log not available)\n";
        }
    }
}
