# Progress

## P0 · Bootstrap (2026-10-05)

**Done**
- Removed the generated Flutter scaffold. The app is native Kotlin + Compose, as the spec says.
- Gradle wrapper 9.8.0, generated with the cached Gradle 9.3.1 running on the Studio JBR (Java 25).
- Version catalog `gradle/libs.versions.toml` with the latest stable versions (checked 2026-10-05):
  AGP 9.4.1 (Studio 2026.2.1 supports up to 9.5), Kotlin 2.4.20, KSP 2.3.12, Hilt 2.60.1,
  Compose BOM 2026.09.00, Room 2.8.5, Media3 1.11.1, youtubedl-android 0.18.1 (pinned).
- AGP 9 built-in Kotlin: the app module applies only `kotlin.plugin.compose`. `kotlin-android`
  is declared `apply false` at the root just to pin KGP 2.4.20.
- Minimal Hilt + Compose app: `com.lumen.player`, "Lumen" on ink, edge-to-edge, adaptive vector icon.

**Verified**
- `./gradlew assembleDebug` succeeds.
- Installed and launched on the POCO F3 (Android 13, HyperOS 1.0); screenshot `shots/p0-launch.png`
  shows "Lumen". `adb shell input tap` works without a SecurityException.

**Open issues / deviations**
- `compileSdk` is **37**, not 36: core-ktx 1.19.1 and other current AndroidX libraries require it.
  `targetSdk` stays 36.
- AGP warns that `android:extractNativeLibs` should not be in the manifest (`useLegacyPackaging`
  already sets it). Kept for now as the spec asks; check the merged manifest in P3 and remove it if
  it's redundant.
- The release build uses debug signing until the release keystore is made in P12.
- Use `MSYS_NO_PATHCONV=1` in Git Bash for adb commands with `/sdcard/...` paths.

## P1 · Design system, shell, onboarding, storage (2026-10-05)

**Done**
- Design tokens (`core/design`): colours, cover-tone accent (with the 4.5:1 contrast step), type scale,
  motion/space/radii, `LumenTheme`, first shared components (PillButton, SectionHeader, SegmentedTabs,
  EmptyState, ScreenTitle).
- App shell: type-safe routes, NavHost, bottom nav (Home · Library · Pulse · Add) with the ember dot.
- `LumenPaths` + `StorageSetup` (folders, `.nomedia`, `README.txt`, low-space check) + atomic `moveInto`.
- `SettingsRepository` (DataStore) with every setting key the app needs.
- Onboarding: Welcome → Storage (all-files access, re-checked on resume, private fallback) → Restore
  slot → Notifications → Background (battery + MIUI Autostart) → Done.
- Settings skeleton: folder path, private-storage warning, Reduce motion, About.

**Verified on the phone**
- Full onboarding flow; `/sdcard/Lumen/` has all 7 folders + `.nomedia` + `README.txt`.
- Screenshots `shots/p1-*.png`: Fraunces + Manrope render, ink/paper colours, ember dot on the active tab.

**Deviation**
- Fonts live in `assets/fonts/`, not `res/font/`. On HyperOS, Compose silently ignored
  `FontVariation.Settings` for resource fonts and fell back to the system font. The fonts are now built
  with `Typeface.Builder(...).setFontVariationSettings(...)`, which works.

## P2 · Data layer (2026-10-05)

**Done**
- Room: the 8 entities from §5.1, list projections without the waveform (`TrackRow` etc.), DAOs incl.
  `observeSongs` with the Focus filter and the §9.4 stats SQL (with a Focus-scope variant).
- The database is only opened by the first query. `MainViewModel` starts backups only after onboarding.
- Repositories: Tracks (like, edit, soft delete with file removal), Playlists, Focus (DataStore),
  Settings.
- `SnapshotManager`: `VACUUM INTO` → `Lumen/data/lumen.db` with rotation to `lumen-prev.db`;
  triggers: 30 s debounce on Room invalidation, app ON_STOP, "Back up now".
- Onboarding Restore step (shows "N songs, M playlists"), copies the snapshot in before Room opens.
- Debug seeders: 1,500 songs (`seed…` ids), 1 year of plays (`DEBUG_SEED`), remove seeded data.

**Verified on the phone**
- `connectedDebugAndroidTest`: 4/4 pass (focus filter incl. featured, playlist reorder + duplicates,
  stats with fixed timestamps, soft delete keeps events and allows ID reuse).
- Seed → `Lumen/data/lumen.db` (1.5 MB) written 30 s after the last write.
- Uninstall → reinstall → onboarding offered "1,500 songs, 0 playlists" → restore worked, and Room
  opened the restored file (a backup from it succeeded).

## P3 · Downloads (2026-10-05)

**Done**
- `core/net/Http` (OkHttp, Lumen User-Agent, per-host rate limiter, offline detection), JSON via
  kotlinx.serialization.
- Sources: Deezer (quota back-off 1/2/4 s, `Accept-Language: en`), iTunes genre rule, LRCLIB pick order,
  Spotify (oEmbed + og:description, short links), yt-dlp (flat search, info, download, cancel).
- `YtDlpManager`: init in `LumenApp`, `ready` flow, daily update worker, version.
- `DownloadPipeline` with every §7.2 stage, duplicate check, ID reuse after delete, optional stages
  that never fail the job, NEEDS_CHOICE, ReplaceAudio, one-transaction save, atomic moves.
- `DownloadQueue` + `DownloadWorker` (foreground `dataSync`, 2 parallel, Wi-Fi-only constraint,
  RUNNING → QUEUED after a killed process).
- Add screen: Deezer search (350 ms debounce), albums with "Download album (N)", Paste link,
  "Preparing downloader…", downloads list, Choose-audio sheet, Clear finished.
- Share target (`ACTION_SEND text/plain`) → Add tab + snackbar; app-wide `AppEvents` for snackbars.
- 19 JVM unit tests (LRC, Spotify, scoring, splitting, seeded tone, API parsing) all pass.

**Verified on the phone**
- Downloaded New Grass, Get Lucky, Bohemian Rhapsody (Add tab) and Smells Like Teen Spirit (shared
  Spotify link with `/intl-de/` + `?si=`). Each has audio > 1 MB, cover + 256 px thumb, waveform (96)
  and gain, genre (iTunes); synced lyrics where LRCLIB has them; Get Lucky has Pharrell Williams and
  Nile Rodgers as separate FEATURED artists with photos.
- Sharing New Grass again → "Already in your library".
- Cancel during the audio stage (Sleep – Dopesmoker) → "Cancelled", empty work dir, no new audio file.
- yt-dlp stderr shows no "No supported JavaScript runtime" warning (QuickJS works). Only the expected
  "no ffmpeg / DASH m4a" warnings appear; those files analyse and play fine.

**Deviation**
- Spotify now serves an empty web-player shell to a desktop-browser User-Agent, so the track page is
  fetched with Lumen's own User-Agent (crawler agent as a fallback).

## P4 · Playback core (2026-10-05)

**Done**
- `PlaybackService` (MediaSessionService + ExoPlayer: audio focus, becoming-noisy pause, wake lock,
  session activity → Now Playing, `onTaskRemoved`, `onPlaybackResumption`), queue restored paused on start.
- `QueueManager`: our own shuffle (ExoPlayer stays in order), user-queued block ("#q" media ids, FIFO),
  shuffle off rebuilds the original order, repeat cycle, persistence to DataStore every 5 s while
  playing, on pause, and on skips/edits while paused.
- `PlayerConnection` (MediaController → StateFlows, position polled only while collected),
  `PlaybackContextHolder`, volume normalisation from `gain_db`.
- Floating mini player (swipe left/right to skip, swipe up/tap to expand, accent progress line) and a
  basic Now Playing overlay. Notification = Media3 default (artwork from `file://` cover).

**Verified on the phone**
- Playing from a temporary song list; `dumpsys media_session` shows the session.
- HOME + Settings app for 3 minutes → still playing (position 191 s); media notification present.
- `KEYCODE_MEDIA_PLAY_PAUSE` / `NEXT` work; notification pause/next buttons work (HyperOS media card
  shows cover, title, artist).
- force-stop → relaunch → queue restored paused at the saved position, plays on tap.

**Open**
- Lock-screen controls and headset-unplug pause need a manual check by the user (asked at the end).

## P5 · Library (2026-10-05)

**Done**
- Library: Songs (count · total time, Shuffle/Play, 5 sorts, A–Z rail for Title/Artist sorts, tap plays the
  list from that song, ⋯ track menu, long-press multi-select with Add to playlist / Play next / Like /
  Delete), Artists (3-column grid, sort by name or listening time, focused artists first, long-press
  focus actions), Albums (2-column grid).
- Artist page (photo header fading into ink, Play/Shuffle/Focus, stats line, top songs, all songs,
  albums, appears on), Album page (glow behind the cover, meta line, songs in track order).
- Local search (title/artists/album, accent-insensitive, grouped results, last 10 recent searches).
- Track menu sheet: play next, add to queue, add to playlist, like, go to artist/album, focus on
  artist, edit info, lyrics re-fetch / offset, "Wrong audio? Choose another" (yt-dlp candidates →
  ReplaceAudio job), delete with confirm (soft delete, also removes it from the queue).
- Artist Focus foundation (focus pill, focus sheet, shell-level FocusViewModel) used by the Library.

**Verified on the phone**
- Release build with 1,507 songs: `gfxinfo` 1,399 frames, **0.07 % janky**, p99 9 ms.
- All 5 sorts, A–Z rail (tap N → jumps to N), artists by listening time, albums grid, artist page,
  search ("daft" → song, artist, album), track menu, delete (audio + cover + thumb removed, row
  soft-deleted), multi-select delete of two seeded songs (soft-deleted).
- A download works in the **release** build (R8 rules for youtubedl-android are enough).
