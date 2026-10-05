package com.noam.photodream;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.SeekBar;

/** Settings › Display: mode, slideshow, photo table, clock, dim. */
public class DisplaySettingsActivity extends SettingsScreen {

    static final int MIN_INTERVAL = 5;

    private android.widget.TextView txtInterval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_display);
        setTitle(R.string.settings_display);
        txtInterval = findViewById(R.id.txt_interval);

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
}
