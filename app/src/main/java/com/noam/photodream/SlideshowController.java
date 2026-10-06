package com.noam.photodream;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.noam.photodream.cloud.CloudProvider;
import com.noam.photodream.cloud.CloudProviders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glue shared by the screensaver and the preview screen:
 * reads the settings, picks the display mode, loads the photo list in
 * the background, and starts the chosen {@link PhotoDisplay}.
 * Expects a view inflated from R.layout.view_slideshow_overlay.
 */
public class SlideshowController {

    private final Context context;
    private final SlideshowView slideshow;
    private final PhotoTableView table;
    private final View clockBox;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final View nightOverlay;
    private final android.widget.TextView dateCaption;
    private boolean showDate, onThisDayOn;
    private PhotoDisplay display;
    private List<Photo> loadedPhotos;
    private int normalIntervalSeconds;
    private boolean nightEnabled;
    private Prefs.NightStyle nightStyle = Prefs.NightStyle.DIM_WARM;
    private boolean night;                          // true while the night look is applied
    private final Runnable checkNight = this::applyNight;
    private boolean started;
    private BurnInWalk burnIn;
    private final Runnable moveClock = this::moveClock;

    public SlideshowController(Context context, View root, PhotoDisplay.Listener listener) {
        this.context = context;
        this.slideshow = root.findViewById(R.id.slideshow);
        this.table = root.findViewById(R.id.photo_table);
        this.clockBox = root.findViewById(R.id.clock_box);
        this.nightOverlay = root.findViewById(R.id.night_overlay);
        // our own listener: leave requests go to the caller, "photo shown" updates the date caption
        PhotoDisplay.Listener inner = new PhotoDisplay.Listener() {
            @Override public void onExitRequested() { listener.onExitRequested(); }
            @Override public void onPhotoShown(Photo photo) { showDateCaption(photo); }
        };
        slideshow.setListener(inner);
        table.setListener(inner);
        dateCaption = root.findViewById(R.id.photo_date);
    }

    public void start() {
        Prefs prefs = new Prefs(context);
        boolean tableMode = prefs.getDisplayMode() == Prefs.DisplayMode.TABLE;

        slideshow.setVisibility(tableMode ? View.GONE : View.VISIBLE);
        table.setVisibility(tableMode ? View.VISIBLE : View.GONE);

        if (tableMode) {
            table.setEntry(prefs.getTableEntry());
            table.setMaxCards(prefs.getTableMaxCards());
            table.setCardSizePercent(prefs.getTableCardSize());
            table.setMaxRotation(prefs.getTableRotation());
            table.setDrift(prefs.isTableDrift());
            table.setMarks(PhotoMarksStore.get(context));
            table.setFrameColors(frameColors(prefs));
            display = table;
        } else {
            slideshow.setTransition(prefs.getTransition());
            slideshow.setCrop(prefs.isCrop());
            slideshow.setSourceStrip(prefs.isSourceStrip(), frameColors(prefs));
            display = slideshow;
        }
        normalIntervalSeconds = prefs.getIntervalSeconds();
        display.setIntervalSeconds(normalIntervalSeconds);
        nightEnabled = prefs.isNightEnabled();
        nightStyle = prefs.getNightStyle();
        nightFrom = LocalTime.of(prefs.getNightFromMinutes() / 60, prefs.getNightFromMinutes() % 60);
        nightTo = LocalTime.of(prefs.getNightToMinutes() / 60, prefs.getNightToMinutes() % 60);
        night = false;
        showDate = prefs.isShowPhotoDate();
        onThisDayOn = prefs.isOnThisDay();
        dateCaption.setVisibility(View.GONE);
        table.setOnThisDay(onThisDayOn ? this::isOnThisDay : null);
        clockBox.setVisibility(prefs.isShowClock() ? View.VISIBLE : View.GONE);

        started = true;
        startBurnInProtection(prefs.isShowClock());
        applyNight();                                  // night look from the first frame, before photos arrive
        final PhotoDisplay target = display;
        io.execute(() -> {
            List<Photo> photos = PhotoRepository.loadAll(context);
            DateIndexer.indexLocal(context, photos, 300);        // phone photos: read EXIF dates a few hundred at a time
            main.post(() -> {
                if (started && display == target) {
                    // hidden photos never show; favorites come up 3x as often
                    PhotoMarksStore store = PhotoMarksStore.get(context);
                    List<Photo> visible = store.marks().visible(photos);
                    target.setWeights(buildWeights(store.marks(), visible));
                    loadedPhotos = visible;
                    applyNight();                 // sets the night look and the interval, then starts when appropriate
                    if (!(night && nightStyle == Prefs.NightStyle.CLOCK_ONLY)) target.start(visible);
                }
            });
        });
    }

    // ---------------------------------------------------------------- dates: caption and "On this day"

    private boolean isOnThisDay(Photo photo) {
        return PhotoDates.isOnThisDay(PhotoDateIndex.get(context).dateOf(photo), LocalDate.now());
    }

    /**
     * Favorites ×3 and "on this day" photos ×4 (they multiply). Null – plain order – when nothing
     * deserves more attention, so the shuffle/in-order setting keeps working as before.
     */
    private PhotoQueue.Weights buildWeights(PhotoMarks marks, List<Photo> visible) {
        boolean anyFavorite = marks.hasFavoriteIn(visible);
        boolean anyOnThisDay = false;
        if (onThisDayOn) {
            for (Photo p : visible) {
                if (isOnThisDay(p)) {
                    anyOnThisDay = true;
                    break;
                }
            }
        }
        if (!anyFavorite && !anyOnThisDay) return null;
        final boolean boost = onThisDayOn;
        return p -> marks.weight(p) * (boost && isOnThisDay(p) ? PhotoDates.ON_THIS_DAY_WEIGHT : 1.0);
    }

    /** "June 2023 · 3 years ago" above the clock, if the setting is on and the date is known. */
    private void showDateCaption(Photo photo) {
        if (!showDate) return;
        LocalDate date = PhotoDateIndex.get(context).dateOf(photo);
        if (date == null) {
            dateCaption.setVisibility(View.GONE);
            return;
        }
        String month = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.getDefault());
        String text = month + " " + date.getYear();
        int years = PhotoDates.yearsAgo(date, LocalDate.now());
        if (years >= 1) {
            text = context.getString(R.string.photo_date_caption, text,
                    context.getResources().getQuantityString(R.plurals.years_ago, years, years));
        }
        dateCaption.setText(text);
        dateCaption.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- night mode

    private static final long MAX_NIGHT_CHECK_MS = 5 * 60_000L;   // re-check at least this often: the clock can be changed
    private static final int DIM_WARM_OVERLAY = 0x73402000;       // ~45% dark amber: dims and warms the photos
    private LocalTime nightFrom = LocalTime.of(22, 0), nightTo = LocalTime.of(6, 30);

    /**
     * Switches between the day and night look when the schedule says so, and sleeps until the next
     * boundary (a Handler delay, capped at 5 min) instead of polling. "Dim & warm": a dark amber
     * overlay and photos change half as often. "Clock only": black screen, photos stopped, dim clock.
     */
    private void applyNight() {
        main.removeCallbacks(checkNight);
        if (!started || !nightEnabled) return;
        LocalTime now = LocalTime.now();
        boolean shouldBeNight = NightSchedule.isNight(now, nightFrom, nightTo);

        if (shouldBeNight != night) {
            night = shouldBeNight;
            boolean clockOnly = nightStyle == Prefs.NightStyle.CLOCK_ONLY;
            if (night) {
                nightOverlay.setBackgroundColor(clockOnly ? 0xFF000000 : DIM_WARM_OVERLAY);
                nightOverlay.setVisibility(View.VISIBLE);
                clockBox.setAlpha(clockOnly ? 0.45f : 0.75f);
                if (clockOnly) {
                    display.stop();
                } else {
                    display.setIntervalSeconds(normalIntervalSeconds * 2);
                }
            } else {
                nightOverlay.setVisibility(View.GONE);
                clockBox.setAlpha(1f);
                display.setIntervalSeconds(normalIntervalSeconds);
                if (clockOnly && loadedPhotos != null) display.start(loadedPhotos);   // photos come back
            }
        }

        java.time.Duration until = NightSchedule.untilNextSwitch(now, nightFrom, nightTo);
        long delay = until == null ? MAX_NIGHT_CHECK_MS : Math.min(MAX_NIGHT_CHECK_MS, until.toMillis() + 500);
        main.postDelayed(checkNight, Math.max(1000, delay));
    }

    // ---------------------------------------------------------------- burn-in protection

    private static final long BURN_IN_INTERVAL_MS = 60_000;

    /** OLED screens can burn in a clock that never moves: nudge it a few dp every minute. */
    private void startBurnInProtection(boolean clockShown) {
        main.removeCallbacks(moveClock);
        if (!clockShown) return;
        float dp = context.getResources().getDisplayMetrics().density;
        burnIn = new BurnInWalk(24 * dp, 8 * dp, new java.util.Random());
        main.postDelayed(moveClock, BURN_IN_INTERVAL_MS);
    }

    private void moveClock() {
        if (!started || burnIn == null) return;
        burnIn.step();
        // "Remove animations" in the phone's settings means: move without animating
        float scale = android.provider.Settings.Global.getFloat(context.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (scale == 0f) {
            clockBox.setTranslationX(burnIn.x());
            clockBox.setTranslationY(burnIn.y());
        } else {
            clockBox.animate().translationX(burnIn.x()).translationY(burnIn.y()).setDuration(1000).start();
        }
        main.postDelayed(moveClock, BURN_IN_INTERVAL_MS);
    }

    /** Frame color (ARGB) for every source id; sources not listed use classic white. */
    private static Map<String, Integer> frameColors(Prefs prefs) {
        Map<String, Integer> map = new HashMap<>();
        map.put("local", FrameColors.argb(prefs.getFrameColor("local")));
        for (CloudProvider p : CloudProviders.all()) {
            map.put(p.id(), FrameColors.argb(prefs.getFrameColor(p.id())));
        }
        return map;
    }

    public void stop() {
        started = false;
        main.removeCallbacks(moveClock);
        main.removeCallbacks(checkNight);
        nightOverlay.setVisibility(View.GONE);
        clockBox.setAlpha(1f);
        clockBox.animate().cancel();
        clockBox.setTranslationX(0f);
        clockBox.setTranslationY(0f);
        if (display != null) display.stop();
    }

    public void release() {
        stop();
        slideshow.release();
        table.release();
        io.shutdownNow();
    }
}
