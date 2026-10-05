package com.noam.photodream.alarm;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.noam.photodream.Photo;
import com.noam.photodream.PhotoRepository;
import com.noam.photodream.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ringing screen. Shows over the lock screen (the phone stays locked behind it) and hosts
 * the challenge. The sound belongs to {@link AlarmService}, not to this screen, so leaving the
 * screen (Home/Back) never silences the alarm; the notification brings the screen back.
 */
public class AlarmActivity extends AppCompatActivity {

    private static final String EXTRA_ALARM_ID = "alarm_id";
    private static final int CHALLENGE_PHOTOS = 20;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private long alarmId;
    private Alarm alarm;
    private AlarmChallenge challenge;
    private FrameLayout challengeHost;
    private TextView progress;
    private MaterialButton snooze;
    private View fallbackLink, fallbackGroup;
    private String fallbackCode;
    private boolean solved;
    private long lastTouchSent;

    public static Intent intent(Context context, long alarmId) {
        return new Intent(context, AlarmActivity.class)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);          // over the lock screen; the phone stays locked behind us
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_alarm);

        alarmId = getIntent().getLongExtra(EXTRA_ALARM_ID, 0);
        alarm = new AlarmStore(this).get(alarmId);
        if (alarm == null || !AlarmService.isRinging()) {      // stale notification / already stopped
            finish();
            return;
        }

        ((TextView) findViewById(R.id.alarm_label)).setText(alarm.label);
        challengeHost = findViewById(R.id.alarm_challenge);
        progress = findViewById(R.id.alarm_progress);
        snooze = findViewById(R.id.alarm_snooze);
        fallbackLink = findViewById(R.id.alarm_fallback_link);
        fallbackGroup = findViewById(R.id.alarm_fallback_group);

        snooze.setOnClickListener(v -> {
            AlarmService.snooze(this);
            finish();
        });
        fallbackLink.setOnClickListener(v -> showFallback());
        findViewById(R.id.alarm_code_ok).setOnClickListener(v -> checkCode());
        ((EditText) findViewById(R.id.alarm_code_input)).setInputType(InputType.TYPE_CLASS_NUMBER);

        updateSnoozeButton();
        loadChallenge();
        handler.post(watch);
    }

    /** Photos come only from the phone folders and the already-synced cloud caches: never the network. */
    private void loadChallenge() {
        io.execute(() -> {
            List<Photo> all = new ArrayList<>(PhotoRepository.loadAll(this));
            Collections.shuffle(all, random);
            List<Photo> chosen = all.subList(0, Math.min(CHALLENGE_PHOTOS, all.size()));
            runOnUiThread(() -> {
                if (isFinishing() || solved) return;
                challenge = Challenges.create(alarm.challenge, all.size(), random);
                View view = challenge.createView(this, new ArrayList<>(chosen), alarm.difficulty,
                        new AlarmChallenge.Listener() {
                            @Override public void onProgress(int done, int total) {
                                progress.setText(total == 100 ? "" : getString(R.string.alarm_progress, done, total));
                            }
                            @Override public void onSolved() { solved(); }
                        });
                challengeHost.removeAllViews();
                challengeHost.addView(view);
                challenge.start();
            });
        });
    }

    /** Once a second: show the fallback link after 3 minutes, close if the service has stopped. */
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (solved) return;
            if (!AlarmService.isRinging()) {       // snoozed, auto-stopped or stopped elsewhere
                finish();
                return;
            }
            if (AlarmPolicy.showFallback(AlarmService.ringingMillis()) && fallbackGroup.getVisibility() != View.VISIBLE) {
                fallbackLink.setVisibility(View.VISIBLE);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void updateSnoozeButton() {
        int used = new AlarmStore(this).snoozesUsed(alarmId);
        int left = AlarmPolicy.MAX_SNOOZES - used;
        snooze.setEnabled(left > 0);
        snooze.setText(getString(R.string.alarm_snooze, alarm.snoozeMinutes, Math.max(0, left)));
    }

    /** "Can't solve it?" – type the 4-digit code shown on screen instead. */
    private void showFallback() {
        fallbackCode = AlarmPolicy.newFallbackCode(random);
        ((TextView) findViewById(R.id.alarm_code)).setText(fallbackCode);
        fallbackLink.setVisibility(View.GONE);
        fallbackGroup.setVisibility(View.VISIBLE);
    }

    private void checkCode() {
        String typed = ((EditText) findViewById(R.id.alarm_code_input)).getText().toString();
        if (AlarmPolicy.codeMatches(fallbackCode, typed)) {
            solved();
        } else {
            ((EditText) findViewById(R.id.alarm_code_input)).setError(getString(R.string.alarm_code_wrong));
        }
    }

    /** Stop the sound, show "Good morning" with today's date for a moment, then close. */
    private void solved() {
        if (solved) return;
        solved = true;
        if (challenge != null) challenge.stop();
        new AlarmStore(this).resetSnoozesUsed(alarmId);
        AlarmService.stop(this);

        findViewById(R.id.alarm_main).setVisibility(View.GONE);
        View morning = findViewById(R.id.alarm_morning);
        morning.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.alarm_morning_date)).setText(
                DateFormat.getDateInstance(DateFormat.FULL).format(new Date()));
        handler.postDelayed(this::finish, 2500);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // every touch lowers the alarm volume for a while so you can concentrate (at most once per 500 ms)
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastTouchSent > 500) {
            lastTouchSent = now;
            AlarmService.userTouched();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (challenge != null) challenge.stop();
        io.shutdownNow();
        super.onDestroy();
    }
}
