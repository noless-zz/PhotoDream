# PhotoDream

A photo slideshow **screensaver** for Android (Java). It runs only while the
phone is charging, because Android starts screensavers ("Dreams") by itself
when the phone is charging and idle.

## Try it
1. Run the app from Android Studio and tap **Choose photo folder** (for example DCIM/Camera).
2. Tap **Preview now** to see the slideshow straight away.
3. Tap **Open system screen saver settings**, pick **PhotoDream slideshow**, and set
   *When to start* to *While charging*. On Samsung this is under Settings > Display > Screen saver.
   If nothing appears while charging, turn off Always On Display.

Gestures (one photo): swipe = next/previous · tap = pause · long-press (or Back) = exit.
Gestures (photo table): drag a photo · flick it away · long-press the empty table (or Back) = exit.

## Code map
| File | Role |
|---|---|
| `PhotoDreamService` | The screensaver (DreamService) |
| `PreviewActivity` | Same slideshow as a normal screen, for testing |
| `SlideshowController` | Reads settings, loads the photo list, starts the view |
| `PhotoDisplay` | Interface for a display mode (start/stop/release) |
| `SlideshowView` | Mode 1 – one photo at a time: two stacked ImageViews, transitions, gestures |
| `PhotoTableView` | Mode 2 – photo table: bordered, tilted photos pile up; drag, flick, drift |
| `BitmapLoader` | Decodes photos at screen size (ImageDecoder, EXIF-aware) |
| `PhotoRepository` | Merges all enabled `PhotoSource`s, shuffles |
| `source/LocalFolderSource` | Folder picked with the system picker (SAF) |
| `source/CacheFolderSource` | `files/photo_cache/` – where cloud sync will download to |
| `SettingsActivity` / `Prefs` | Settings screen and SharedPreferences |

## Roadmap
- [x] Screensaver + preview, slide / fade / Ken Burns, local folder
- [x] Photo table mode (drop / fly in / pop / fade / random, drag & flick, slow drift)
- [ ] Sync job (WorkManager, only when charging + Wi-Fi) → `photo_cache/`
- [ ] OneDrive source (MSAL + Microsoft Graph, `Files.Read`)
- [ ] Google Drive source (Drive API, `drive.readonly`)
- [ ] Optional: live wallpaper using the same `SlideshowView` logic
