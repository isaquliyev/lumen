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

## P6 · Playlists (2026-10-05)

**Done**
- Playlists tab: Create playlist, Liked Songs, On Repeat / Recently Added / Forgotten Gems (queries,
  hidden when empty), user playlists pinned first then by last update; mosaic or custom covers.
- Create sheet (default `My playlist #N`), detail page (glow from the first cover, 200 dp cover, meta,
  Play/Shuffle/Add songs, ⋯ menu: edit name & description, change/remove cover, pin, delete),
  sort chip, Focus banner "Showing X of Y · Focus: … · Show all".
- Edit mode: drag handles (`reorderable`, positions rewritten in one transaction on drop), swipe left
  to remove with an Undo snackbar (snackbars with an action now use the long duration).
- Add Songs picker (search + checkboxes over the whole library, "Only {focus}" chip),
  Add-to-playlist sheet (checkmarks, "Already in …", New playlist), Photo Picker custom cover →
  1000 px square JPEG in `Lumen/playlists/`.

**Verified on the phone**
- Create, rename ("Evening Mix"), add from the picker, the track menu and multi-select, duplicate →
  "Already in My playlist #1", drag reorder, swipe remove + Undo, custom cover, pin, delete.
- Play starts in custom order; after force-stop the playlist is intact, and the snapshot holds it
  (pinned, custom cover, same song order).

**Note**
- During testing, a stray tap on "Download album" downloaded Kanye West – *Graduation* (13 songs) and
  Talk Talk – *Life's What You Make It*. They can be deleted from the Library.

## P7 · Artist Focus (2026-10-05)

**Done**
- Focus set in DataStore; songs match on main **or featured** credits. Songs, Albums, search, smart
  playlists and the Add Songs picker are filtered; playlists show "Showing X of Y · Focus: … · Show all".
- Entry points: artist page Focus pill, long-press in Library ▸ Artists, track menu "Focus on artist",
  Home's target icon and the Focus pill → Focus sheet (search, multi-select chips with photos, Clear).
- Visible state: Focus pill under Home/Library/Pulse titles, app accent switches to the first focused
  artist's photo tone, 6 % accent vignette around the screen edges.
- "Play only X now?" snackbar when Focus turns on while a non-matching song plays; its action shuffles
  the focused songs. (A second "Focus: X" confirmation was removed; it hid that snackbar.)

**Verified on the phone**
- Focus on Chris Martin (only a featured artist) → 1 song (Homecoming), only the Graduation album.
- Focus on Kanye West while New Grass played → snackbar → Play → 10 skips, all Kanye West.
- Kanye West + Daft Punk → 15 songs; Evening Mix shows "Showing 2 of 5"; focus survives force-stop;
  clearing restores the full library.

## P8 · Now Playing complete (2026-10-05)

**Done**
- Player sheet over the app: drag down (or back) to collapse, spring animation.
- Now Playing: OLED black + cover-tone glow that breathes while playing, PLAYING FROM + context, ⋯ track
  menu, cover pager (swipe = previous/next), title (shrinks for long titles), each artist tappable
  (Open / Focus), like with a pop, 96-bar waveform scrubber (tap or drag, haptic tick every 10 %),
  time codes, controls (repeat-one icon), lyrics peek (crossfading current line), bottom row
  Lyrics · Queue · Sleep (shows time left) · Add.
- Full lyrics screen (§13.1): active line at ~35 %, neighbours 45 %, others 20 % + blur (API 31+),
  spring scale, auto-scroll paused 3 s after manual scrolling, tap to seek, ♪ ♪ ♪ for breaks,
  per-track offset, plain and "no lyrics" states.
- Queue sheet: Now playing / Next in queue (Clear) / Next from: context, drag reorder, swipe remove.
- Sleep timer (15–90 min, end of song; debug builds also have 1 min): fades over the last 10 s,
  pauses, restores the volume. Mini player progress line uses the song's accent.

**Verified on the phone**
- Waveform tap at 50 % of Get Lucky → 184.0 s (expected 183.5 s; within ±1 s).
- Tapping lyric line 55 → exactly 184,050 ms, the line's LRC time.
- Shuffle on + "Play next" Bohemian Rhapsody then Smells Like Teen Spirit → queue shows them in that order.
- 1-minute sleep timer started 17:44:48, paused at 17:45:47.
- Screenshots `shots/p8-*.png` (player, lyrics, queue).

## P9 · Listening tracker, Pulse, Recap (2026-10-05)

**Done**
- `ListeningTracker` as in §9.2 (real playing time only, 30 s checkpoints, 15-min sessions, open rows
  saved on pause), attached in `PlaybackService`. `first_listen` is only set on a real play (≥ 30 s).
- `StatsCalculator` (periods with local days and locale week start, streaks, sessions, skip /
  completion rates, on-repeat moment, new-to-you share, % change, 9 archetypes + Balanced Listener)
  with 7 JVM unit tests (26 unit tests in total, all pass).
- `PulseRepository`: one build per period from the §9.4 SQL; re-runs on every Room change, so Pulse is
  live. The current period is compared with the same stretch of the previous one.
- Pulse screen: range tabs + ‹ › history, Everything | Focus (+ Focus card), hero count-up with ▲/▼,
  top songs (5 / see all 50), artists, albums, genres, radial clock, week rhythm, calendar heatmap
  (month grid / year grid), trend, streaks, discovery, habits, on-repeat moment, personality,
  library + growth, "How it's counted" sheet, "Not enough listening yet" progress state.
- Canvas charts (`RadialClock`, `BarRow`, `HBarList`, `CalendarHeatmap`, `Sparkline`), animated in
  unless Reduce motion is on.
- Recap stories (month or year): 6 s slides, tap left/right, hold to pause, progress segments; summary
  card → PNG in `Lumen/exports/` → share sheet via FileProvider. `RecapWorker` (daily) notifies once in
  the first week of a month if last month had ≥ 60 min; the notification opens that recap.

**Verified on the phone**
- Good Morning skipped at ~10 s → 12.7 s SKIPPED; I Wonder seeked near the end → 24.6 s COMPLETED;
  Stronger 40 s + pause 10 s + 20 s → 76.4 s STOPPED; the paused current song stays open (NULL).
- Pulse Today minutes went 54 → 55 → 56 while playing (no screen change needed).
- Year view with a seeded year: every card renders, no empty or NaN values.
- October recap → summary → share: `Lumen/exports/recap-2026-10.png` written, MIUI share sheet opened.

**Not tested on the device**
- The recap notification itself (it only fires in the first week of a month).

## P10 · Home (2026-10-05)

**Done**
- Header (date overline, Fraunces greeting by time of day, Focus / Search / Settings icons, Focus pill).
- Sections, each hidden when empty: Pick up where you left off (resume card), Pulse strip
  ("Today 56 min · 17-day streak · Top: …" → Pulse), recap card on days 1–7 (→ last month's recap),
  On repeat, For this hour (new `aroundHours` SQL, ±1 h over all history), Your playlists, Artists you
  love, Recently added, Rediscover. Empty library → "Your library is quiet." + Add.
- Every section is scoped by Artist Focus.

**Verified on the phone**
- With the seeded + real library: all 9 sections render (screenshots `shots/p10-home-*.png`).
- Focus on Kanye West: strip shows "Top: Kanye West", For this hour shows only Kanye songs, On repeat
  and Rediscover disappear (no Kanye plays there), accent switches; clearing Focus restores everything.
- The empty-library state is the same component as in P1 (not re-tested, so the user's library is untouched).

## P11 · Settings, backup, maintenance (2026-10-05)

**Done**
- Storage: folder path, space used per folder + free space, private-storage warning, "Hide songs from
  other apps" (`.nomedia` + MediaScanner rescan), Back up now, Restore from backup (latest or previous;
  copies over the live DB and restarts the app), Check library (missing audio + orphan files → Fix).
- Playback: volume normalisation, resume on launch. Downloads: Wi-Fi only, parallel 1–3, prefer m4a,
  yt-dlp version + Update now. Listening data: How it's counted, Reset statistics (two confirmations).
- Appearance: Reduce motion. About: version, licences, personal use. Debug: seeders, remove seeded data,
  Export logs (own-process logcat → `Lumen/exports/lumen-log-*.txt`).

**Verified on the phone**
- Moved Dopesmoker's audio out via adb + added an orphan file → Check library: "1 missing · 1 orphan".
  With the file moved back, Fix deleted only the orphan.
- Update now → "ALREADY_UP_TO_DATE 2026.08.19" (works; nothing newer today).
- Restore from `lumen-prev.db` → app restarted (new pid), database identical (1,525 songs, 1 playlist,
  3,137 plays). A safety copy of the live DB was taken first (in the scratchpad, not in the repo).

## P12 · Polish and release (2026-10-05)

**Done**
- Release keystore (`keytool`, RSA 4096) in `keystore/` + `keystore.properties`, both gitignored; the
  release build type uses it (falls back to the debug key if the file is missing).
- R8: found and fixed a real release-only crash. On a **fresh** install yt-dlp's first init unzips
  Python with Apache Commons Compress, which R8 broke ("class … is not a concrete class"). Added keep
  rules for commons-compress and commons-io. (The P5 release check passed only because it was an
  upgrade over a debug install that had already unpacked Python.)
- Accessibility: font scale 1.3 checked on Home, Library, Now Playing and Add; the sort chip now stays
  on one line and shrinks with an ellipsis. Icon buttons have content descriptions and 48 dp targets.
- Add tab: album results now use a compact download icon + track count instead of a big pill.
- Removed the redundant `android:extractNativeLibs` manifest attribute (`useLegacyPackaging` sets it
  in the merged manifest; the AGP warning is gone).
- Seed data removed (library: 27 real songs).
- Baseline Profile: skipped. Scrolling is already at 0.07 % janky frames in release.

**Verified on the phone (release APK, release key)**
- Uninstall → install release → onboarding offered "27 songs, 1 playlist" → restore → 27 songs,
  Evening Mix (5 songs) and today's plays all back.
- Download on a fresh release install works (One More Time), no JavaScript-runtime warning; the test
  song was deleted again afterwards.
- "Wrong audio? Choose another" on New Grass → candidates sheet → picked one → same song id, new
  `youtube_id`, waveform recomputed.
