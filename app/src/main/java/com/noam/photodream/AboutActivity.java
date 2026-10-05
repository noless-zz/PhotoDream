package com.noam.photodream;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Settings › About: version, links, privacy note, introduction. */
public class AboutActivity extends AppCompatActivity {

    static final String GUIDE_URL = "https://github.com/noless-zz/PhotoDream/blob/main/docs/STUDENT_INSTALL.md";
    static final String REPO_URL = "https://github.com/noless-zz/PhotoDream";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle(R.string.settings_about);

        ((TextView) findViewById(R.id.txt_version)).setText(getString(R.string.about_version, versionName(this)));
        findViewById(R.id.btn_guide).setOnClickListener(v -> openLink(GUIDE_URL));
        findViewById(R.id.btn_repo).setOnClickListener(v -> openLink(REPO_URL));
        findViewById(R.id.btn_show_intro).setOnClickListener(v ->
                startActivity(new Intent(this, WelcomeActivity.class)));
    }

    /** "0.3" – from the manifest, so it always matches the installed APK. */
    static String versionName(android.content.Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "?" : v;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_LONG).show();
        }
    }
}
