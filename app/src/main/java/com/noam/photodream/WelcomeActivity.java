package com.noam.photodream;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

/** First-run screen: explains PhotoDream in three steps. Shown once, and from "Show introduction". */
public class WelcomeActivity extends AppCompatActivity {

    private static final String PREFS = "photodream";
    private static final String KEY_SEEN = "welcome_seen";

    /** True until the user has gone through the welcome screen once. */
    public static boolean shouldShow(Context c) {
        return !c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SEEN, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        findViewById(R.id.btn_welcome_dream).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            }
        });
        findViewById(R.id.btn_welcome_start).setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply();
            finish();   // back to the settings screen underneath
        });
    }
}
