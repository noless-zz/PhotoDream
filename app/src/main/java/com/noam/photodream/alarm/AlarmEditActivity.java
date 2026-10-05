package com.noam.photodream.alarm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.noam.photodream.R;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Create or edit one alarm. Open with {@link #intent(Context, long)} (id 0 = new alarm). */
public class AlarmEditActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "alarm_id";
    private static final DayOfWeek[] WEEK = {DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};

    private Alarm alarm;
    private boolean isNew;
    private MaterialButton timeButton, soundButton;
    private ChipGroup daysGroup;
    private TextView daysHint, snoozeLabel;
    private TextInputEditText label;
    private Spinner challengeSpinner;
    private final List<String> challengeIds = new ArrayList<>();

    private final ActivityResultLauncher<Intent> pickSound =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != Activity.RESULT_OK || r.getData() == null) return;
                Uri uri = IntentCompat.getParcelableExtra(r.getData(), RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
                alarm.soundUri = uri == null ? null : uri.toString();     // "silent" is not offered; null = default alarm sound
                showSound();
            });

    public static Intent intent(Context context, long alarmId) {
        return new Intent(context, AlarmEditActivity.class).putExtra(EXTRA_ID, alarmId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_edit);

        long id = getIntent().getLongExtra(EXTRA_ID, 0);
        Alarm stored = id == 0 ? null : new AlarmStore(this).get(id);
        isNew = stored == null;
        alarm = isNew ? new Alarm() : stored;
        // keep edits across rotation
        if (savedInstanceState != null) restore(savedInstanceState);

        setTitle(isNew ? R.string.alarm_new : R.string.alarm_edit);
        ((TextView) findViewById(R.id.edit_title)).setText(isNew ? R.string.alarm_new : R.string.alarm_edit);

        timeButton = findViewById(R.id.edit_time);
        timeButton.setOnClickListener(v -> pickTime());

        setupDays();

        label = findViewById(R.id.edit_label);
        label.setText(alarm.label);

        setupChallenge();

        soundButton = findViewById(R.id.edit_sound);
        soundButton.setOnClickListener(v -> chooseSound());
        showSound();

        MaterialSwitch vibrate = findViewById(R.id.edit_vibrate);
        vibrate.setChecked(alarm.vibrate);
        vibrate.setOnCheckedChangeListener((b, on) -> alarm.vibrate = on);

        snoozeLabel = findViewById(R.id.edit_snooze_label);
        SeekBar snooze = findViewById(R.id.edit_snooze);
        snooze.setMax(29);                               // 1..30 minutes
        snooze.setProgress(alarm.snoozeMinutes - 1);
        showSnooze(snooze);
        snooze.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                alarm.snoozeMinutes = progress + 1;
                showSnooze(s);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        findViewById(R.id.edit_save).setOnClickListener(v -> save(false));
        findViewById(R.id.edit_test).setOnClickListener(v -> save(true));
        View delete = findViewById(R.id.edit_delete);
        delete.setVisibility(isNew ? View.GONE : View.VISIBLE);
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.alarm_delete_confirm)
                .setPositiveButton(R.string.alarm_delete, (d, w) -> {
                    new AlarmStore(this).delete(alarm.id);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        showTime();
    }

    // ---------------------------------------------------------------- time

    private void showTime() {
        timeButton.setText(AlarmText.time(this, alarm.hour, alarm.minute));
    }

    private void pickTime() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(alarm.hour)
                .setMinute(alarm.minute)
                .setTitleText(R.string.alarm_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            alarm.hour = picker.getHour();
            alarm.minute = picker.getMinute();
            showTime();
        });
        picker.show(getSupportFragmentManager(), "time");
    }

    // ---------------------------------------------------------------- days

    private void setupDays() {
        daysGroup = findViewById(R.id.edit_days);
        daysHint = findViewById(R.id.edit_days_hint);
        for (DayOfWeek d : WEEK) {
            Chip chip = new Chip(this);
            chip.setText(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            chip.setContentDescription(d.getDisplayName(TextStyle.FULL, Locale.getDefault()));
            chip.setCheckable(true);
            chip.setChecked(alarm.hasDay(d));
            chip.setOnCheckedChangeListener((b, on) -> {
                if (on) alarm.days |= Alarm.bit(d); else alarm.days &= ~Alarm.bit(d);
                showDaysHint();
            });
            daysGroup.addView(chip);
        }
        showDaysHint();
    }

    private void showDaysHint() {
        daysHint.setText(alarm.isRepeating() ? AlarmText.days(this, alarm.days) : getString(R.string.alarm_once_hint));
    }

    // ---------------------------------------------------------------- challenge

    private void setupChallenge() {
        List<Challenges.Info> all = Challenges.all();
        View group = findViewById(R.id.group_challenge);
        if (all.isEmpty()) {              // nothing implemented yet: only the fallback exists, no choice to make
            group.setVisibility(View.GONE);
            return;
        }
        challengeSpinner = findViewById(R.id.edit_challenge);
        List<String> names = new ArrayList<>();
        challengeIds.add(Alarm.CHALLENGE_RANDOM);
        names.add(getString(R.string.challenge_random));
        for (Challenges.Info i : all) {
            challengeIds.add(i.id);
            names.add(getString(i.nameRes));
        }
        challengeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        challengeSpinner.setSelection(Math.max(0, challengeIds.indexOf(alarm.challenge)));

        RadioGroup diff = findViewById(R.id.edit_difficulty);
        diff.check(alarm.difficulty == Alarm.Difficulty.EASY ? R.id.diff_easy
                : alarm.difficulty == Alarm.Difficulty.HARD ? R.id.diff_hard : R.id.diff_medium);
        diff.setOnCheckedChangeListener((g, checked) -> alarm.difficulty = checked == R.id.diff_easy
                ? Alarm.Difficulty.EASY : checked == R.id.diff_hard ? Alarm.Difficulty.HARD : Alarm.Difficulty.MEDIUM);
    }

    // ---------------------------------------------------------------- sound, snooze

    private void chooseSound() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        alarm.soundUri == null ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(alarm.soundUri));
        pickSound.launch(i);
    }

    private void showSound() {
        String name = getString(R.string.alarm_sound_default);
        if (alarm.soundUri != null) {
            try {
                Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(alarm.soundUri));
                if (r != null) name = r.getTitle(this);
            } catch (RuntimeException ignored) {
                // sound no longer exists: the service falls back to the default alarm sound
            }
        }
        soundButton.setText(name);
    }

    private void showSnooze(SeekBar bar) {
        snoozeLabel.setText(getString(R.string.alarm_snooze_label, alarm.snoozeMinutes));
        bar.setContentDescription(snoozeLabel.getText());
    }

    // ---------------------------------------------------------------- save / test

    private void save(boolean thenTest) {
        alarm.label = label.getText() == null ? "" : label.getText().toString().trim();
        if (challengeSpinner != null) {
            alarm.challenge = challengeIds.get(Math.max(0, challengeSpinner.getSelectedItemPosition()));
        }
        alarm.enabled = true;                              // saving an alarm switches it on
        new AlarmStore(this).clearSnooze(alarm.id);
        new AlarmStore(this).save(alarm);
        Toast.makeText(this, AlarmText.until(this, alarm), Toast.LENGTH_LONG).show();
        if (thenTest) {
            AlarmService.start(this, alarm.id, false);     // rings right now, exactly like the real thing
        } else {
            finish();
        }
    }

    // ---------------------------------------------------------------- state across rotation

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("hour", alarm.hour);
        out.putInt("minute", alarm.minute);
        out.putInt("days", alarm.days);
        out.putString("sound", alarm.soundUri);
        out.putBoolean("vibrate", alarm.vibrate);
        out.putInt("snooze", alarm.snoozeMinutes);
        out.putString("difficulty", alarm.difficulty.name());
    }

    private void restore(Bundle in) {
        alarm.hour = in.getInt("hour", alarm.hour);
        alarm.minute = in.getInt("minute", alarm.minute);
        alarm.days = in.getInt("days", alarm.days);
        alarm.soundUri = in.getString("sound", alarm.soundUri);
        alarm.vibrate = in.getBoolean("vibrate", alarm.vibrate);
        alarm.snoozeMinutes = in.getInt("snooze", alarm.snoozeMinutes);
        try {
            alarm.difficulty = Alarm.Difficulty.valueOf(in.getString("difficulty", alarm.difficulty.name()));
        } catch (IllegalArgumentException ignored) {
            // keep the stored difficulty
        }
    }
}
