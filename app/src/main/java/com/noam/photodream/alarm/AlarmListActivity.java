package com.noam.photodream.alarm;

import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.noam.photodream.R;

import java.util.List;

/** Settings › Wake-up alarms: the list of alarms, "+" to add, and warnings about anything that blocks ringing. */
public class AlarmListActivity extends AppCompatActivity {

    private LinearLayout rows, banners;
    private View empty;
    private final Runnable onStoreChanged = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_list);
        setTitle(R.string.settings_alarms);
        rows = findViewById(R.id.alarm_rows);
        banners = findViewById(R.id.banners);
        empty = findViewById(R.id.txt_empty);
        findViewById(R.id.btn_add_alarm).setOnClickListener(v ->
                startActivity(AlarmEditActivity.intent(this, 0)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmStore.addListener(onStoreChanged);
        refresh();
    }

    @Override
    protected void onPause() {
        AlarmStore.removeListener(onStoreChanged);
        super.onPause();
    }

    private void refresh() {
        showBanners();
        rows.removeAllViews();
        List<Alarm> alarms = new AlarmStore(this).list();
        empty.setVisibility(alarms.isEmpty() ? View.VISIBLE : View.GONE);
        for (Alarm a : alarms) rows.addView(buildRow(a));
    }

    private View buildRow(Alarm a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(80));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> startActivity(AlarmEditActivity.intent(this, a.id)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView time = new TextView(this);
        time.setText(AlarmText.time(this, a.hour, a.minute));
        time.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall);
        texts.addView(time);

        StringBuilder sub = new StringBuilder(AlarmText.days(this, a.days));
        if (!a.label.isEmpty()) sub.append(" · ").append(a.label);
        if (!Challenges.all().isEmpty()) sub.append(" · ").append(AlarmText.challenge(this, a.challenge));
        TextView details = new TextView(this);
        details.setText(sub);
        details.setAlpha(0.7f);
        texts.addView(details);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch on = new MaterialSwitch(this);
        on.setChecked(a.enabled);
        on.setContentDescription(getString(R.string.alarm_on_off, AlarmText.time(this, a.hour, a.minute)));
        on.setOnCheckedChangeListener((b, checked) -> {
            Alarm fresh = new AlarmStore(this).get(a.id);
            if (fresh == null) return;
            fresh.enabled = checked;
            new AlarmStore(this).save(fresh);
        });
        row.addView(on);
        return row;
    }

    // ---------------------------------------------------------------- warnings

    /** One banner per problem that would stop an alarm from ringing reliably, each with a fix button. */
    private void showBanners() {
        banners.removeAllViews();
        if (!AlarmScheduler.canScheduleExactAlarms(this)) {
            addBanner(R.string.alarm_warn_exact, R.string.alarm_fix_exact, () -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                }
            });
        }
        if (!AlarmScheduler.canUseFullScreenIntent(this)) {
            addBanner(R.string.alarm_warn_fullscreen, R.string.alarm_fix_fullscreen, () -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                }
            });
        }
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            addBanner(R.string.alarm_warn_notifications, R.string.alarm_fix_notifications, () ->
                    open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        }
        if (!getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())) {
            addBanner(R.string.alarm_warn_battery, R.string.alarm_fix_battery, () ->
                    open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        }
    }

    private void addBanner(int messageRes, int buttonRes, Runnable fix) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setBackgroundResource(R.drawable.bg_banner);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);

        TextView text = new TextView(this);
        text.setText(messageRes);
        box.addView(text);

        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(buttonRes);
        button.setOnClickListener(v -> fix.run());
        box.addView(button);
        banners.addView(box, lp);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
