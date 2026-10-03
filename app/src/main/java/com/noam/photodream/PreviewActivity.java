package com.noam.photodream;

import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Runs the same slideshow as the screensaver, so you can test without waiting. */
public class PreviewActivity extends AppCompatActivity {

    private SlideshowController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_slideshow_overlay);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        View root = findViewById(android.R.id.content);
        controller = new SlideshowController(this, root, this::finish);
        Toast.makeText(this, R.string.gesture_hint, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        controller.start();
    }

    @Override
    protected void onPause() {
        controller.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        controller.release();
        super.onDestroy();
    }

    private void hideSystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
