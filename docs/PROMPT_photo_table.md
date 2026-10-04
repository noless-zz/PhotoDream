# Task: add a "Photo Table" mode to the PhotoDream Android app

You are working in an EXISTING Android project. Read this whole prompt before touching any file.
Work in small steps (listed at the end). After each step, stop and report what you changed.

---

## 1. Facts about the project (do not guess, do not search for other structures)

- Project root: `C:/Users/ADMIN/AndroidStudioProjects/PhotoDream`
- Language: **Java only**. NOT Kotlin. NOT Jetpack Compose. Classic Android Views + XML layouts.
- Package: `com.noam.photodream`
- Source folder: `app/src/main/java/com/noam/photodream/`
- Build: Gradle Groovy DSL, Gradle 9.8.0, AGP 9.4.1, compileSdk 36, **minSdk 34**, Java 11.
- Dependencies available: appcompat, material, activity. **Do not add any new dependency.**

Existing files (read the ones you change before editing them):

| File | What it does |
|---|---|
| `PhotoDreamService.java` | The screensaver (DreamService). Inflates `R.layout.view_slideshow_overlay` and uses `SlideshowController`. |
| `PreviewActivity.java` | Same as the screensaver, but as a normal activity for testing. |
| `SlideshowController.java` | Reads `Prefs`, loads the photo list on a background thread with `PhotoRepository.loadAll(context)`, then calls `slideshow.start(photos)`. Constructor: `SlideshowController(Context, View root, SlideshowView.Listener)`. |
| `SlideshowView.java` | The current mode: ONE full-screen photo at a time with slide/fade/Ken Burns. Public API: `start(List<Uri>)`, `stop()`, `release()`, `setIntervalSeconds(int)`, `setTransition(Transition)`, `setCrop(boolean)`, `setListener(Listener)`. `Listener` has one method `onExitRequested()`. |
| `BitmapLoader.java` | `static Bitmap load(Context, Uri, int targetLongSide)` – decodes a photo at a given size. Returns null on failure. **Must be called on a background thread.** |
| `PhotoRepository.java` | `static List<Uri> loadAll(Context)` – all photos, already shuffled if the user chose shuffle. |
| `Prefs.java` | SharedPreferences wrapper. Pattern: a `KEY_...` constant + getter with default + setter using `.apply()`. |
| `SettingsActivity.java` + `res/layout/activity_settings.xml` | Settings screen. Uses SeekBar, RadioGroup and `MaterialSwitch` with helper methods `setupSwitch(...)`, `setupInterval()`, `setupTransition()`. |
| `res/layout/view_slideshow_overlay.xml` | FrameLayout containing `com.noam.photodream.SlideshowView` (id `slideshow`) and a clock box (id `clock_box`). |
| `res/values/strings.xml` | All UI text lives here. |

Do NOT rename, move or delete any existing file, class, id or preference key.
The existing single-photo mode must keep working exactly as before.

---

## 2. The feature: "Photo Table" mode

Visual reference (from the owner's video): a black screen. Small photos that look like
**printed photos with a white border** drop onto the screen one at a time, at **random
positions**, each **slightly rotated**. They pile up and overlap like photos tossed onto
a table. When there are too many, the oldest one leaves. The user can **drag photos
around with a finger and flick them away**.

### 2.1 Cards
- Each photo is a "card": an `ImageView` with a white border.
  - White background + padding = border. Padding = 4% of card width (min 6px).
  - `scaleType = CENTER_CROP` is NOT wanted here: keep the photo's own aspect ratio.
    Size the card so its long side = `cardSizePercent` % of the screen's SHORT side,
    and the other side follows the photo's aspect ratio.
  - `setElevation(8dp)` for a shadow. Use `setOutlineProvider(ViewOutlineProvider.BOUNDS)`.
- Position: random, but the card's center must stay inside the screen with a margin of
  10% of the card size, so most of every card is visible.
- Rotation: random between `-maxRotation` and `+maxRotation` degrees.
- Each new card is placed on top (`bringToFront()`, then keep the clock above if present).

### 2.2 Timing
- A new card arrives every `intervalSeconds` (reuse the existing interval preference).
- The first card arrives immediately when the screensaver starts.
- When the number of cards on screen is greater than `maxCards`, remove the **oldest**
  card with an exit animation (fade to 0 + scale to 0.8 over 600 ms), then `removeView` it
  and drop its bitmap reference.

### 2.3 Entry animations (user chooses one in settings; "RANDOM" picks one per card)
All durations around 700–900 ms. Every animation ends at the card's final x, y, rotation, scale 1, alpha 1.
1. **DROP** – card starts at scale 1.6, alpha 0, rotation final+10°, and "lands":
   animate to scale 1, alpha 1, final rotation. Interpolator: `DecelerateInterpolator`.
2. **FLY_IN** – card starts fully outside a random screen edge (left/right/top/bottom),
   rotated an extra ±90°, and flies to its spot. Interpolator: `DecelerateInterpolator(1.5f)`.
3. **POP** – starts at scale 0, alpha 1, animates to scale 1 with
   `OvershootInterpolator(1.4f)`.
4. **FADE** – starts at alpha 0, animates to alpha 1. No movement.
5. **RANDOM** – choose one of the four above for each card.

Use `view.animate()` (ViewPropertyAnimator) for these. Do not use Compose, MotionLayout or any library.

### 2.4 Optional slow drift ("floating")
If `drift` is ON: after a card has landed, give it a very slow endless movement:
translate by a random ±(2–4% of screen width) in x and y and rotate by ±3° over
20–30 seconds, then back (use `ObjectAnimator` with `setRepeatMode(REVERSE)` and
`setRepeatCount(INFINITE)`). Cancel the drift animator when the card is touched or removed.

### 2.5 Touch
- **Drag a card**: it follows the finger and comes to the front. While dragging, cancel its drift.
- **Flick a card** (finger released with speed > 2500 px/s, measure with `VelocityTracker`):
  animate it off screen in the flick direction (400 ms) and remove it.
- **Tap a card**: does nothing special (just brings it to the front).
- **Long-press anywhere (empty area OR on a card)**: call `listener.onExitRequested()`
  – same exit gesture as the existing `SlideshowView`. Use `GestureDetector` for long-press.
- Make sure touch handling works inside a DreamService: the dream is already
  `setInteractive(true)`, so touches reach the views.

### 2.6 Memory / threading rules (important – phones crash if these are ignored)
- Decode with `BitmapLoader.load(context, uri, targetLongSide)` on a single-thread
  `ExecutorService`, where `targetLongSide` = card long side in pixels (NOT screen size).
- Post the result back to the main thread with a `Handler(Looper.getMainLooper())`.
- If `load` returns null, skip that photo and try the next one after 300 ms.
- Keep at most `maxCards + 1` bitmaps alive.
- `stop()` must remove all pending callbacks and cancel all animators.
- `release()` = `stop()` + `executor.shutdownNow()` + remove all cards.
- Ignore results that arrive after `stop()` (use a `requestId` counter like `SlideshowView` does).

---

## 3. Settings to add

Add to `Prefs.java` (same style as the existing keys):

| Key constant | Type | Default | Meaning |
|---|---|---|---|
| `KEY_DISPLAY_MODE` = `"display_mode"` | String enum `DisplayMode { SINGLE, TABLE }` | `SINGLE` | Which mode to show |
| `KEY_TABLE_ENTRY` = `"table_entry"` | String enum `Prefs.Entry { DROP, FLY_IN, POP, FADE, RANDOM }` (already exists in Prefs.java) | `RANDOM` | Entry animation |
| `KEY_TABLE_MAX_CARDS` = `"table_max_cards"` | int, 3..20 | 8 | Cards on screen before the oldest leaves |
| `KEY_TABLE_CARD_SIZE` = `"table_card_size"` | int percent, 30..80 | 50 | Card long side as % of screen short side |
| `KEY_TABLE_ROTATION` = `"table_rotation"` | int degrees, 0..30 | 12 | Max random rotation |
| `KEY_TABLE_DRIFT` = `"table_drift"` | boolean | true | Slow floating movement |

Put `DisplayMode` as a public enum inside `Prefs.java`.
Enum getters must catch `IllegalArgumentException` and return the default (copy the pattern of `getTransition()`).

Add to the settings screen (`activity_settings.xml` + `SettingsActivity.java`):
- New section title **"Display mode"** (above the existing "Slideshow" section) with a
  RadioGroup: "One photo at a time" / "Photo table (photos pile up)".
- New section **"Photo table"** below "Slideshow", with:
  - RadioGroup for entry animation: Drop / Fly in / Pop / Fade / Random
  - SeekBar + label "Photos on table: N" (3..20)
  - SeekBar + label "Photo size: N%" (30..80)
  - SeekBar + label "Max tilt: N°" (0..30)
  - MaterialSwitch "Slow floating movement"
- All new text goes into `strings.xml` (use `%1$d` placeholders like the existing `interval_label`).
- The "Photo table" section should be hidden (`View.GONE`) when the mode is SINGLE,
  and the existing "Transition" and "Fill screen (crop edges)" controls hidden when the mode is TABLE.
  (Wrap each group in a `LinearLayout` with an id so you can show/hide it as one.)

---

## 4. How to plug it in (keep changes minimal)

1. Create interface `PhotoDisplay.java`:
   ```java
   public interface PhotoDisplay {
       interface Listener { void onExitRequested(); }
       void start(java.util.List<android.net.Uri> photos);
       void stop();
       void release();
       void setIntervalSeconds(int seconds);
       void setListener(Listener listener);
   }
   ```
2. Make `SlideshowView` implement `PhotoDisplay`. Delete the nested `SlideshowView.Listener`
   interface and use `PhotoDisplay.Listener` instead (field type and `setListener` parameter).
   Add `@Override` to `start`, `stop`, `release`, `setIntervalSeconds`, `setListener`.
3. Create `PhotoTableView extends FrameLayout implements PhotoDisplay`. Use the EXISTING
   enum `Prefs.Entry` (step 1 put it in Prefs.java) – do NOT create a second Entry enum. Setters:
   `setEntry(Prefs.Entry)`, `setMaxCards(int)`, `setCardSizePercent(int)`, `setMaxRotation(int)`, `setDrift(boolean)`.
   Two constructors: `(Context)` and `(Context, AttributeSet)` so it can be used in XML.
4. In `view_slideshow_overlay.xml` add, right after the `SlideshowView`:
   ```xml
   <com.noam.photodream.PhotoTableView
       android:id="@+id/photo_table"
       android:layout_width="match_parent"
       android:layout_height="match_parent"
       android:visibility="gone" />
   ```
   (The clock box stays last so it is drawn on top.)
5. In `SlideshowController`: read `prefs.getDisplayMode()`. For TABLE, make `photo_table`
   VISIBLE and `slideshow` GONE, configure the table from Prefs, and use it as the
   `PhotoDisplay`. For SINGLE, do exactly what the code does today.
   Store the active one in a field `PhotoDisplay display` and call `display.start/stop/release`.
   Change the constructor's listener parameter type to `PhotoDisplay.Listener`
   (`PhotoDreamService` and `PreviewActivity` pass `this::finish`, which still fits).

---

## 5. Steps – do ONE at a time, then stop and report

1. **Prefs**: add `DisplayMode` enum and the 6 new keys with getters/setters. Build. ✅ DONE (2026-10-04, validated)
2. **Interface**: create `PhotoDisplay`, make `SlideshowView` implement it, update
   `SlideshowController` to use a `PhotoDisplay` field (still always SINGLE). Build.
   The app must behave exactly as before.
3. **PhotoTableView – cards only**: cards appear every interval at random position/rotation
   with the FADE entry, oldest removed when over `maxCards`. No touch yet. Hook it into the
   layout and controller. Build.
4. **Entry animations**: add DROP, FLY_IN, POP, RANDOM. Build.
5. **Touch**: drag, flick-to-remove, long-press-to-exit. Build.
6. **Drift**. Build.
7. **Settings UI**: new sections, show/hide logic. Build.

Build command (Windows, from the project root): `gradlew.bat assembleDebug`
If you cannot run commands, say so and list the exact files you changed so the owner can build in Android Studio.

## 6. Done means
- [ ] Project builds with no errors.
- [ ] Mode = "One photo at a time" behaves exactly like before.
- [ ] Mode = "Photo table": white-bordered, tilted photos land one by one at random spots,
      pile up, oldest leaves when over the limit.
- [ ] Each entry animation can be chosen in settings and looks different; Random mixes them.
- [ ] Drag moves a photo, flick throws it off screen, long-press exits.
- [ ] Preview screen and the real screensaver both show the chosen mode.
- [ ] No new dependencies, no Kotlin, no Compose.
