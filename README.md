# PhotoDream

A photo slideshow **screensaver** for Android 10+ (Java). It runs only while the
phone is charging, because Android starts screensavers ("Dreams") by itself
when the phone is charging and idle.

## Try it
1. Open the app. The first-run **welcome screen** explains the idea in three steps.
2. Pick where photos come from: a folder on the phone (for example DCIM/Camera), **OneDrive**,
   **Google Drive** – or all of them. Cloud photos are downloaded in the background
   (see `docs/ONEDRIVE_SETUP.md` and `docs/GOOGLE_DRIVE_SETUP.md` for the one-time setup).
3. Tap **Preview now** to see the slideshow straight away.
4. Tap **Open system screen saver settings**, pick **PhotoDream slideshow**, and set
   *When to start* to *While charging*. On Samsung this is under Settings > Display > Screen saver.
   If nothing appears while charging, turn off Always On Display.

Gestures (one photo): swipe = next/previous · tap = pause · long-press (or Back) = exit.
Gestures (photo table): drag a photo · flick it away · long-press the empty table (or Back) = exit.

## Code map
Everything is under `app/src/main/java/com/noam/photodream/`.

| File | Role |
|---|---|
| `PhotoDreamService` | The screensaver (DreamService) |
| `PreviewActivity` | Same slideshow as a normal screen, for testing |
| `SlideshowController` | Reads settings, loads the photo list, starts the view |
| `PhotoDisplay` | Interface for a display mode (start/stop/release) |
| `SlideshowView` | Mode 1 – one photo at a time: two stacked ImageViews, transitions, gestures |
| `PhotoTableView` | Mode 2 – photo table: bordered, tilted photos pile up; drag, flick, drift |
| `Photo` | One photo + its source id; `key()` is its stable identity |
| `BitmapLoader` | Decodes photos at screen size (ImageDecoder, EXIF-aware) |
| `PhotoRepository` | Merges all enabled `PhotoSource`s, shuffles |
| `source/LocalFolderSource` | Folder picked with the system picker (SAF) |
| `source/CacheFolderSource` | `files/photo_cache/<provider>/` – where one cloud's sync downloads to |
| `SettingsActivity` / `Prefs` | Settings screen and SharedPreferences |
| `cloud/CloudProvider` | Interface every cloud implements; `CloudProviders` lists them |
| `cloud/CloudSyncWorker` + `SyncPlanner` | Shared WorkManager sync: download, shrink, rotate, clean up |
| `cloud/CloudScheduler` | Every 6 h (Wi-Fi + charging by default), Sync now, Disconnect |
| `cloud/CloudFolderActivity`, `CloudSourceView` | Shared folder browser and settings section |
| `onedrive/OneDriveProvider`, `OneDriveAuth` | Microsoft sign-in (OAuth code + PKCE), Graph API |
| `gdrive/GoogleDriveProvider` | Google sign-in (Play services AuthorizationClient), Drive v3 API |
| `onedrive/SecureStore` | OneDrive tokens encrypted with an Android Keystore key |

## Docs
- `CLAUDE.md` – rules and architecture notes for Claude Code
- `docs/ROADMAP.md` – plan and issue map; `docs/CLAUDE_CODE.md` – how Claude Code is wired into this repo
- `docs/STUDENT_INSTALL.md` – install & use guide for students (Hebrew)
- `docs/ONEDRIVE_SETUP.md`, `docs/GOOGLE_DRIVE_SETUP.md` – one-time cloud registration

## Signing
Builds are signed with the release key in `../PhotoDream-signing/` (outside git;
see the README there). Without that folder, Gradle falls back to the debug key –
then Google sign-in fails, because Google knows only the release key's SHA-1.

## Releasing a new version
1. Commit and push to `main`.
2. GitHub → **Releases → Draft a new release** → new tag like `v0.3` → **Publish**.
3. The *Release APK* workflow builds, signs and attaches `PhotoDream.apk` (~5 min).

Students always download the newest version from
<https://github.com/noless-zz/PhotoDream/releases/latest/download/PhotoDream.apk>
and install it over the old one (same signing key, so settings are kept).

## Roadmap
- [x] Screensaver + preview, slide / fade / Ken Burns, local folder
- [x] Photo table mode (drop / fly in / pop / fade / random, drag & flick, slow drift)
- [x] Sync job (WorkManager, only when charging + Wi-Fi) → `photo_cache/onedrive/`
- [x] OneDrive source (OAuth PKCE + Microsoft Graph, `Files.Read`) – setup: `docs/ONEDRIVE_SETUP.md`
- [x] Google Drive source (Drive API, `drive.readonly`) – setup: `docs/GOOGLE_DRIVE_SETUP.md`
- [x] First-run screen, Hebrew, adaptive/themed icon, screen saver preview, Android 10+
- [ ] Optional: live wallpaper using the same `SlideshowView` logic
- [ ] Google Play (only if going public): privacy policy, user-initiated transfer job instead of the
      foreground service, remove the battery-exemption button, Drive verification + CASA
