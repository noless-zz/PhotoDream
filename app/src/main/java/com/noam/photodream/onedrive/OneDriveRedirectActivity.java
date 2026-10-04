package com.noam.photodream.onedrive;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.noam.photodream.R;
import com.noam.photodream.SettingsActivity;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opened by the browser when Microsoft redirects to photodream://auth?code=...
 * Finishes the sign-in, then returns to the settings screen.
 */
public class OneDriveRedirectActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText(R.string.onedrive_connecting);
        tv.setPadding(48, 48, 48, 48);
        tv.setTextSize(18);
        setContentView(tv);

        Uri data = getIntent().getData();
        if (data == null) {
            backToSettings();
            return;
        }
        io.execute(() -> {
            String message;
            try {
                String name = OneDriveAuth.finishSignIn(this, data);
                message = getString(R.string.onedrive_connected_as, name);
            } catch (IOException e) {
                message = getString(R.string.onedrive_connect_failed, e.getMessage());
            }
            final String msg = message;
            runOnUiThread(() -> {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                backToSettings();
            });
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private void backToSettings() {
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
