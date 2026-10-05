# PhotoDream – notes for Claude Code

Android photo **screensaver** (DreamService) that shows photos from a phone folder,
OneDrive and Google Drive while the phone charges. Owner: Noam (teacher). Sideloaded
to students as a signed APK from GitHub Releases – not on Google Play.

## Hard rules
- **Java only.** No Kotlin, no Jetpack Compose. Classic Views + XML layouts.
- **minSdk 29, targetSdk/compileSdk 36.** Guard newer APIs with `Build.VERSION.SDK_INT`
  or use the androidx `*Compat` classes. `./gradlew lintRelease` must have **0 errors**.
- **No new dependencies** unless the issue says so. Ask in the issue/PR first otherwise.
- **Every user-visible string goes in `res/values/strings.xml` AND `res/values-iw/strings.xml`**
  (Hebrew; `iw` is Android's resource code for Hebrew). Lint fails on missing translations.
  Layouts use `start`/`end`, never `left`/`right` (RTL).
- **Never commit secrets**: `keystore.properties`, `*.jks`, API keys, tokens. The release key
  lives outside the repo in `../PhotoDream-signing/` (and as GitHub secrets for CI).
- Don't change `applicationId`, the signing setup, or `onedrive_config.xml` client ID.
- Keep the app working offline: the screensaver/alarm must never wait on the network.

## Build & test
```bash
./gradlew assembleDebug            # builds without the release key (debug key fallback)
./gradlew testDebugUnitTest        # JVM unit tests (JUnit 4) in app/src/test
./gradlew lintRelease              # must report 0 errors
```
Put pure logic (planners, pickers, game rules, schedulers' time math) in plain Java classes
without Android imports so it can be unit-tested; add tests for every such class.
There is no emulator in CI – say in the PR what you could not test on a device.

## Architecture (app/src/main/java/com/noam/photodream)
| Area | Files |
|---|---|
| Screensaver entry | `PhotoDreamService` (DreamService), `PreviewActivity` (same UI as an activity) |
| Display modes | `PhotoDisplay` interface; `SlideshowView` (one photo), `PhotoTableView` (photos pile up, drag/flick/drift) |
| Glue | `SlideshowController` reads `Prefs`, loads photos off the main thread, starts the chosen `PhotoDisplay` |
| Photos | `PhotoRepository` merges `source/*` (`LocalFolderSource` = SAF tree URI, `CacheFolderSource` = `files/photo_cache/<provider>/`) |
| Decoding | `BitmapLoader` (ImageDecoder, sampled to target size; background thread only) |
| Clouds | `cloud/` shared engine: `CloudProvider` interface, `CloudProviders` registry, `CloudSyncWorker` (WorkManager), `SyncPlanner` (pure, tested), `CloudScheduler`, `CloudFolderActivity`, `CloudSourceView` (settings section), `CloudPrefs`, `Http` |
| Providers | `onedrive/` (OAuth PKCE + Graph, tokens in `SecureStore`), `gdrive/` (Play services AuthorizationClient + Drive v3) |
| Settings | `SettingsActivity` + `activity_settings.xml`, `Prefs` (SharedPreferences wrapper), `WelcomeActivity` |

Conventions already in the code – follow them:
- Bitmaps decode on a single-thread executor, results posted to a main `Handler`; ignore late
  results with a `requestId` counter; `stop()` cancels callbacks/animators; `release()` shuts executors.
- Settings live in `Prefs` (`KEY_...` constant + getter with default + setter with `.apply()`).
- New cloud = implement `CloudProvider` + register in `CloudProviders`; nothing else changes.
- Comments explain *why*, in plain English, for a reader learning Android.

## Pull requests
- One issue per PR, branch `issue-<n>-short-name`, title `#<n>: …`, body starts with `Closes #<n>`.
- PR description: what changed, how you tested (unit tests / lint), what needs a device test,
  screenshots of new layouts if you can render them.
- Update `docs/STUDENT_INSTALL.md` (Hebrew) and the in-app welcome text when gestures or
  user-facing flows change.

## Docs
`docs/ROADMAP.md` (plan + issue map), `docs/STUDENT_INSTALL.md`, `docs/ONEDRIVE_SETUP.md`,
`docs/GOOGLE_DRIVE_SETUP.md`, `docs/CLAUDE_CODE.md` (how Claude Code is wired into this repo).
