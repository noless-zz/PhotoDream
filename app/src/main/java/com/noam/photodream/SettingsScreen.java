package com.noam.photodream;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

/** Base for the settings screens: holds {@link Prefs} and the small helpers every screen uses. */
public abstract class SettingsScreen extends AppCompatActivity {

    protected Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
    }

    protected interface IntSetter { void set(int value); }

    /** A SeekBar from min..max with a "label: N" text above it. */
    protected void setupSeek(int seekId, int labelId, int formatRes, int min, int max,
                           int value, IntSetter setter) {
        setupSeek(seekId, labelId, n -> getString(formatRes, n), min, max, value, setter);
    }

    /** Same, but the label text comes from a function (needed for plurals). */
    protected void setupSeek(int seekId, int labelId, java.util.function.IntFunction<String> format,
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

    protected interface BoolSetter { void set(boolean value); }

    protected void setupSwitch(int id, boolean value, BoolSetter setter) {
        MaterialSwitch sw = findViewById(id);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, checked) -> setter.set(checked));
    }
}
