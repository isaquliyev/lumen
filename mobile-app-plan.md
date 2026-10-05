# Lumen — Android Music Player: Complete Build Plan

> **Hand-off document for Claude Code.** You are building this app from an empty folder. This file
> is the only context you have, so read all of it before writing any code. Every decision here is
> already made. Where a step says **verify**, check on the phone or on Maven and adapt if needed.

---

## 0. How to work through this plan

1. Work **phase by phase** (§15). Don't start a phase until the previous one meets its *Definition
   of Done*.
2. At the end of each phase: build → install on the phone → run that phase's device checks (§14)
   → commit to git → add an entry to `PROGRESS.md` (what was done, what was verified, any open issues).
3. The user's phone is connected over USB. Use it for every real check: install builds, tap
   through screens, take screenshots and **look at them**, and read logcat.
4. Ask the user only for things that need hands on the phone (accepting the USB debugging prompt,
   MIUI install confirmations, unlocking the screen), or for real product decisions not covered here.
5. This is a **personal, sideloaded app**. It will never go on Google Play. Some choices
   (all-files storage access, downloading audio with yt-dlp) are only acceptable because of that.
6. Keep the code clean and small. Don't add features this plan doesn't ask for.

**Working name:** `Lumen`. The app name, package and folder name are each defined in one place (§3.3),
so a rename later is cheap.

---

## 1. Product summary

A **minimal, modern, editorial-looking music player** for one person's own downloaded library.
It covers Spotify's core features but drops the parts the user doesn't need. Its look is its own:
dark, typography-led, with one glowing accent colour taken from each song's cover.

### 1.1 The five must-haves (non-negotiable)

| # | Requirement | Where it's specified |
|---|---|---|
| 1 | **All Songs** section | §12.5 Library ▸ Songs |
| 2 | **Playlists**, created and managed like Spotify | §12.7 |
| 3 | **Pulse**, a live "Wrapped": statistics that update with every listen, not once a year | §9, §12.9 |
| 4 | **Artist Focus**: pick one or more artists, and the whole app shows and plays only their songs | §10 |
| 5 | **Local storage only**, in one dedicated app folder for audio, covers, lyrics and data | §4 |

### 1.2 Core features included

- Background playback with notification, lock-screen and Bluetooth/headset controls
- Floating mini player that expands into a full Now Playing screen
- Queue: *Play next*, *Add to queue*, reorder, remove; shuffle; repeat off/all/one
- Liked Songs; artist pages; album pages
- Time-synced lyrics (tap a line to seek), with plain-lyrics fallback
- Local library search, plus an online search to **add** songs (the download pipeline)
- **Share to Lumen**: share a Spotify/YouTube link from another app and it downloads
- Sleep timer, volume normalisation, resume where you left off
- Monthly recap stories (Wrapped-style) that can be shared as images

### 1.3 Out of scope (don't build)

Accounts, cloud sync, social features, podcasts, video, Chromecast, equaliser, light theme, tablet
layouts, Play Store compliance, translations (UI is English only).

---

## 2. Environment on this PC (checked 2026-10-04)

| Item | State | What to do |
|---|---|---|
| OS / shell | Windows 11, x64. Git Bash and PowerShell 5.1 are available | Prefer Git Bash for `adb` piping |
| Android Studio | Installed: `C:\Program Files\Android\Android Studio` (2026.2.1) | Don't need to open it |
| JDK for Gradle | Studio's bundled JBR **Java 25**: `C:\Program Files\Android\Android Studio\jbr`. System `java` on PATH is **11 (too old)** | Set `JAVA_HOME` to the JBR for every Gradle call. If the chosen Gradle can't run on Java 25, install JDK 21: `winget install EclipseAdoptium.Temurin.21.JDK` |
| Android SDK | `C:\Users\ASUS\AppData\Local\Android\Sdk`. Platforms 34, 36, 37.0; build-tools 36.0.0; cmdline-tools; NDK 28.2 | `ANDROID_HOME` is **not set**, so write `local.properties` (§15, P0) |
| adb | `…\Sdk\platform-tools\adb.exe` (1.0.41). **Not on PATH** | Call it by full path or add it to PATH for the session |
| Gradle | **Not installed** | Bootstrap the wrapper (§15, P0) |
| git | 2.55 | `git init` in P0 |
| Phone | Not connected at the time of writing | Run `adb devices`. If empty or `unauthorized`, ask the user (§14.1) |

### Target device: POCO F3

- Snapdragon 870 (arm64-v8a) → build **arm64-v8a only**.
- 6.67" AMOLED, 1080×2400, up to 120 Hz. Pure blacks are free (pixels off), and smooth scrolling matters.
- MIUI / HyperOS on Android 11–13 (**verify** with `adb shell getprop ro.build.version.release`).
  MIUI kills background apps aggressively and needs extra developer toggles for adb input (§14.1).

---

## 3. Tech stack and architecture

### 3.1 Stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 used only as a base under a **custom design system** (§11) |
| Architecture | Single `:app` module, MVVM + unidirectional data flow (`ViewModel` → `StateFlow<UiState>`) |
| DI | Hilt (KSP) |
| Navigation | Navigation Compose with type-safe `@Serializable` routes |
| Database | Room (KSP) + Flow queries, so the UI and Pulse update live |
| Preferences | DataStore (Preferences) |
| Playback | Media3: ExoPlayer, `MediaSessionService`, `MediaController` |
| Background work | WorkManager (download queue, yt-dlp updates, recap notifications) |
| Networking | OkHttp + kotlinx.serialization JSON (a thin helper, no Retrofit) |
| Images | Coil 3 (local files only, after download) |
| Audio download | **youtubedl-android 0.18.1** (`io.github.junkfood02.youtubedl-android:library:0.18.1`). It embeds Python + yt-dlp and bundles **QuickJS**, which YouTube extraction now needs. No ffmpeg module (§7.6) |
| Drag-to-reorder lists | `sh.calvin.reorderable:reorderable` |
| Icons | `androidx.compose.material:material-icons-extended`, **Rounded** set (R8 strips the unused ones) |
| Tests | JUnit for pure logic; instrumented tests on the connected phone for Room |

**Versions:** in P0, look up the **latest stable** of each library (Google Maven / Maven Central),
choose an AGP version that the installed Android Studio supports, and pin everything in
`gradle/libs.versions.toml`. Use the Kotlin/KSP/Compose-compiler setup that the current Android
Studio *Empty Activity (Compose)* template uses for that AGP version; don't invent a plugin setup.
Exception: youtubedl-android is pinned to **0.18.1**, the first version with QuickJS.

### 3.2 Package layout

```
com.lumen.player
├── LumenApp.kt                 Application: Hilt, yt-dlp init, WorkManager config
├── MainActivity.kt             single activity, edge-to-edge, share-intent entry
├── core/
│   ├── design/                 theme, colors, type, shapes, motion, shared components
│   ├── storage/                LumenPaths, folder setup, snapshot/restore, file ops
│   ├── db/                     Room database, entities, DAOs, converters
│   ├── net/                    OkHttp client, JSON helper, rate limiter
│   └── util/                   text normalisation, time formatting, colour tone, LRC parser
├── data/                       repositories: Tracks, Artists, Albums, Playlists, Stats, Focus, Settings
├── download/
│   ├── sources/                DeezerApi, ItunesApi, LrcLibApi, SpotifyResolver, YtDlpAudio
│   ├── DownloadPipeline.kt     one job, stage by stage
│   ├── DownloadQueue.kt        enqueue/cancel/retry; observed by the UI
│   ├── DownloadWorker.kt       drains the queue as a foreground worker
│   └── AudioAnalyzer.kt        waveform + loudness
├── playback/
│   ├── PlaybackService.kt      MediaSessionService + ExoPlayer
│   ├── PlayerConnection.kt     MediaController wrapper exposing StateFlows to the UI
│   ├── QueueManager.kt         play lists, shuffle, play next, persistence
│   ├── ListeningTracker.kt     writes play events (stats source of truth)
│   └── SleepTimer.kt
└── feature/
    ├── onboarding/  home/  library/  playlist/  artist/  album/  search/
    ├── player/      (mini player, Now Playing, queue sheet)
    ├── lyrics/      pulse/  recap/  add/  settings/  focus/
```

### 3.3 Single sources of truth for identity

- `applicationId` / namespace: `com.lumen.player`
- App label: `strings.xml` → `app_name = "Lumen"`
- Root folder name: `LumenPaths.ROOT_NAME = "Lumen"`

---

## 4. Storage: the Lumen folder (must-have #5)

### 4.1 Decision

All user data lives in **one visible folder on internal shared storage**:
`/storage/emulated/0/Lumen/`. This needs the **All files access** permission
(`MANAGE_EXTERNAL_STORAGE`), which is fine for a sideloaded app. Why this approach:

- It's a real, visible folder that the user can back up or copy to a PC.
- It **survives uninstalling or reinstalling** the app, so the music library is never lost.
- Nothing goes to any cloud.

If the permission is refused, fall back to `context.getExternalFilesDir(null)/Lumen`. Show a
persistent warning in Settings: *"Your library will be deleted if you uninstall Lumen."*

### 4.2 Layout

```
/storage/emulated/0/Lumen/
├── .nomedia                  hides files from Gallery / other music apps (toggle in Settings)
├── README.txt                short note: what this folder is, don't rename files
├── audio/                    {trackId}.m4a   (or .webm/.opus if m4a was unavailable)
├── covers/                   {trackId}.jpg   1000 px   +  {trackId}_s.jpg 256 px thumbnail
├── artists/                  {artistId}.jpg  1000 px   +  {artistId}_s.jpg 256 px
├── lyrics/                   {trackId}.lrc   (synced)  or  {trackId}.txt (plain)
├── playlists/                {playlistId}.jpg  custom playlist covers (1000 px square)
├── data/
│   ├── lumen.db              automatic snapshot of the database (see 4.3)
│   └── lumen-prev.db         previous snapshot
└── exports/                  recap images the user shared
```

- **IDs used in file names:** track and playlist IDs are 12-character random lowercase
  alphanumerics. Artist and album IDs are `dz<deezerId>` (e.g. `dz1234`), or `nm<8-char hash of the
  normalised name>` when there's no Deezer ID. Never put `:` or `/` in IDs.
- The database stores **relative** paths (`audio/abc123def456.m4a`). `LumenPaths.root` resolves them.
- Writes are atomic: download into `cacheDir/work/{jobId}/`, then move into place (copy + delete if
  `renameTo` fails across filesystems). The library folder must never contain half-written files.

### 4.3 Database location and snapshot

The live Room database stays in the app's private `databases/` directory (fast and safe for SQLite).
To keep "all data in the Lumen folder" true and survive reinstalls:

- **Snapshot:** run `VACUUM INTO '<cache>/lumen-snap.db'` (SQLite ≥ 3.27, available on Android 11+),
  then move it to `Lumen/data/lumen.db`, rotating the old one to `lumen-prev.db`. Triggers:
  30 s after the last database write (debounced, using Room's `InvalidationTracker`), when the app
  goes to the background (`ProcessLifecycleOwner` `ON_STOP`), and from the "Back up now" button in Settings.
- **Restore:** during onboarding (after storage permission), if the private database is empty and
  `Lumen/data/lumen.db` exists, offer *"Restore your library (N songs, M playlists)"*. Copy the file
  into `getDatabasePath("lumen.db")` **before** Room is first opened. Room must therefore be created
  lazily, and nothing may touch the database before onboarding finishes.

### 4.4 First run

After permission: create the folders, write `.nomedia` and `README.txt`, then check free space
(warn if < 1 GB).

---

## 5. Data model (Room)

### 5.1 Entities

```kotlin
enum class LyricsKind { NONE, PLAIN, SYNCED, INSTRUMENTAL }
enum class ArtistRole { MAIN, FEATURED }
enum class EndReason { COMPLETED, SKIPPED, STOPPED }
enum class PlayContext { ALL_SONGS, PLAYLIST, LIKED, ARTIST, ALBUM, SEARCH, HOME, QUEUE, RECAP, DEBUG_SEED }
enum class JobStatus { QUEUED, RUNNING, NEEDS_CHOICE, DONE, FAILED, CANCELLED }

@Entity(tableName = "tracks", indices = [Index("deezer_id"), Index("isrc"), Index("added_at")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,       // normalised, leading "the "/"a " removed
    @ColumnInfo(name = "album_id") val albumId: String?,
    @ColumnInfo(name = "track_position") val trackPosition: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "audio_file") val audioFile: String,       // relative path
    @ColumnInfo(name = "has_cover") val hasCover: Boolean,
    @ColumnInfo(name = "lyrics_kind") val lyricsKind: LyricsKind,
    @ColumnInfo(name = "lyrics_offset_ms") val lyricsOffsetMs: Long = 0,
    val genre: String?,
    @ColumnInfo(name = "release_date") val releaseDate: String?,  // ISO yyyy-MM-dd
    val isrc: String?,
    @ColumnInfo(name = "deezer_id") val deezerId: Long?,
    @ColumnInfo(name = "spotify_id") val spotifyId: String?,
    @ColumnInfo(name = "youtube_id") val youtubeId: String?,
    val bpm: Float?,
    val explicit: Boolean,
    @ColumnInfo(name = "tone_hue") val toneHue: Float?,           // cover tone (§13.3)
    @ColumnInfo(name = "tone_sat") val toneSat: Float?,
    val waveform: ByteArray?,                                     // 96 bars, 0..255
    @ColumnInfo(name = "gain_db") val gainDb: Float?,             // ≤ 0, for normalisation
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "liked_at") val likedAt: Long?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,        // soft delete keeps stats intact
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "deezer_id") val deezerId: Long?,
    @ColumnInfo(name = "has_image") val hasImage: Boolean,
    @ColumnInfo(name = "tone_hue") val toneHue: Float?,
    @ColumnInfo(name = "tone_sat") val toneSat: Float?,
)

@Entity(
    tableName = "track_artists",
    primaryKeys = ["track_id", "artist_id"],
    indices = [Index("artist_id")],
)
data class TrackArtistEntity(
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "artist_id") val artistId: String,
    val position: Int,                                            // 0 = primary artist
    val role: ArtistRole,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "artist_id") val artistId: String,
    @ColumnInfo(name = "release_date") val releaseDate: String?,
    val genre: String?,
    @ColumnInfo(name = "cover_track_id") val coverTrackId: String?,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    @ColumnInfo(name = "has_custom_cover") val hasCustomCover: Boolean = false,
    val pinned: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_id"],                     // a song appears once per playlist
    indices = [Index("track_id")],
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "play_events", indices = [Index("started_at"), Index("track_id")])
data class PlayEventEntity(
    @PrimaryKey val id: String,                                   // UUID, created client-side
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,         // wall clock, epoch ms
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,       // real playing time only
    @ColumnInfo(name = "track_duration_ms") val trackDurationMs: Long,
    @ColumnInfo(name = "end_reason") val endReason: EndReason?,   // null = still open / app was killed
    @ColumnInfo(name = "context_type") val contextType: PlayContext,
    @ColumnInfo(name = "context_id") val contextId: String?,
    @ColumnInfo(name = "focus_active") val focusActive: Boolean,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "first_listen") val firstListen: Boolean,
)

@Entity(tableName = "download_jobs", indices = [Index("status")])
data class DownloadJobEntity(
    @PrimaryKey val id: String,
    val request: String,                                          // JSON of DownloadRequest (§7.1)
    val title: String,
    val artist: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    val status: JobStatus,
    val stage: String,
    val progress: Int,                                            // 0..100
    val error: String?,
    val candidates: String?,                                      // JSON list when NEEDS_CHOICE
    @ColumnInfo(name = "track_id") val trackId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

Notes:
- `ByteArray` in a data class: override `equals`/`hashCode`, or keep the waveform out of the list
  models (list queries should use a lighter projection without `waveform`).
- **Soft delete:** deleting a song removes its files and playlist entries and sets `deleted_at`.
  Play events stay, so Pulse history stays correct (deleted songs show greyed, "removed").
  If the same song (same `deezer_id` or `isrc`) is downloaded again, **reuse the old ID** and clear
  `deleted_at`, so its stats continue.
- **Liked Songs** = tracks with `liked_at != null`, ordered by `liked_at DESC`. There's no playlist row for it.
- Smart playlists (On Repeat, Recently Added, Forgotten Gems) are **queries**, not rows (§12.7).

### 5.2 Key queries

```kotlin
// All songs, respecting Artist Focus. SQLite accepts an empty IN () list.
@Query("""
    SELECT t.id, t.title, t.sort_title, t.album_id, t.duration_ms, t.has_cover, t.added_at,
           t.liked_at, t.tone_hue, t.tone_sat
    FROM tracks t
    WHERE t.deleted_at IS NULL
      AND (:focusOff OR EXISTS (
            SELECT 1 FROM track_artists ta
            WHERE ta.track_id = t.id AND ta.artist_id IN (:focusIds)))
""")
fun observeSongs(focusOff: Boolean, focusIds: List<String>): Flow<List<TrackRow>>
```

Sort in the ViewModel (fast for ≤ 10k rows): Recently added · Title · Artist · Most played · Duration.
"Most played" uses lifetime play counts from a separate flow (`trackId → plays`).

Stats queries are in §9.4.

---

## 6. External services (all free, no API keys)

All verified on 2026-10-04. Send a clear User-Agent on every call: `Lumen/1.0 (personal music player)`.
Put a shared `RateLimiter` per host in front of the calls.

### 6.1 Deezer API (main metadata source)

| Call | Use |
|---|---|
| `GET https://api.deezer.com/search?q={q}&limit=25` | Search results in the Add tab. `data[]`: `id, title, title_short, duration` (seconds), `explicit_lyrics, artist{id,name,picture_xl}, album{id,title,cover_xl}` |
| `GET https://api.deezer.com/search?q=artist:"{a}" track:"{t}"` | Precise lookup when resolving a Spotify/YouTube link |
| `GET https://api.deezer.com/track/{id}` | `isrc, bpm, release_date, track_position, disk_number, contributors[{id,name,picture_xl,role}]` |
| `GET https://api.deezer.com/album/{id}` | `genres.data[].name, release_date, label, tracks.data[]`. **Send `Accept-Language: en`**, otherwise genre names come back in the IP's local language |
| `GET https://api.deezer.com/artist/{id}` | `picture_xl` |

- `cover_xl` and `picture_xl` are 1000×1000 JPEGs.
- Errors come back as HTTP 200 with `{"error":{"type","message","code"}}`. **Code 4 = quota**
  (about 50 requests / 5 s): back off 1 s → 2 s → 4 s and retry.
- **Use `contributors` for artist credits** (it gives separate IDs and photos for featured artists).

### 6.2 iTunes Search API (genre)

`GET https://itunes.apple.com/search?term={artist title}&entity=song&limit=5` →
`trackName, artistName, trackTimeMillis, primaryGenreName`. Deezer album genres are often wrong
(the test album came back as "Jazz, Pop"; iTunes correctly said "Rock"). So the genre comes from the
first iTunes result whose artist matches (normalised) and whose duration is within ±5 s; otherwise
the first Deezer album genre; otherwise `null`. Failure here is never fatal.

### 6.3 LRCLIB (lyrics)

- `GET https://lrclib.net/api/get?track_name=&artist_name=&album_name=&duration=` (duration in whole
  seconds). Returns **404** if there's no match. Fields: `id, trackName, artistName, albumName,
  duration, instrumental, plainLyrics, syncedLyrics` (LRC text or null).
- `GET https://lrclib.net/api/search?track_name=&artist_name=` returns up to 20 records with the same shape.
- Pick: (1) `/api/get` with `syncedLyrics` → SYNCED. (2) Else from `/api/search`, the record with
  `syncedLyrics` and `|duration − target| ≤ 3` → SYNCED. (3) Else any `plainLyrics` (prefer
  `/api/get`) → PLAIN. (4) Else `instrumental == true` → INSTRUMENTAL. (5) Else NONE.
- Save SYNCED as `lyrics/{id}.lrc` and PLAIN as `lyrics/{id}.txt`. Ignore the newer `lyricsfile` field.

### 6.4 Spotify links (input only, no API)

- A track URL may have locale prefixes and tracking parameters. Extract the ID with the regex in §13.2.
  `https://spotify.link/…` short links: follow redirects with OkHttp and use `response.request.url`.
- Title: `GET https://open.spotify.com/oembed?url=https://open.spotify.com/track/{id}` → `title`.
- Artist: fetch the track page (desktop User-Agent). The
  `<meta property="og:description" content="Artist · Album · Song · 1991">` tag: take the part before
  the first `" · "` and HTML-unescape it.
- Then resolve to Deezer with the precise search (§6.1) and continue as a normal download.

### 6.5 YouTube via yt-dlp (audio)

See §7.6.

---

## 7. Download pipeline

### 7.1 Inputs

```kotlin
@Serializable sealed interface DownloadRequest {
    @Serializable data class DeezerPick(val deezerId: Long) : DownloadRequest   // chosen in the Add tab
    @Serializable data class SpotifyLink(val url: String) : DownloadRequest     // pasted or shared
    @Serializable data class YouTubeLink(val url: String) : DownloadRequest     // pasted or shared
    @Serializable data class FreeText(val query: String) : DownloadRequest      // "artist - title", top Deezer hit
    @Serializable data class ReplaceAudio(val trackId: String, val videoId: String) : DownloadRequest
}
```

Sources: Add tab results, a pasted link, the share sheet (`ACTION_SEND text/plain`), and
"Download album" (one `DeezerPick` per album track).

### 7.2 Stages and progress (shown in the UI)

| Stage | Progress | Work |
|---|---|---|
| `queued` | 0 | waiting |
| `resolving` | 5 | link → Spotify/YouTube metadata → Deezer track |
| `metadata` | 12 | Deezer `/track` + `/album` (+ contributors), iTunes genre, **duplicate check** |
| `cover` | 20 | cover_xl → `covers/{id}.jpg` + 256 px thumb; compute cover tone |
| `artists` | 26 | for each contributor without an image: picture_xl → `artists/` (+ tone) |
| `lyrics` | 32 | LRCLIB (§6.3) |
| `audio` | 35 → 90 | yt-dlp search + score + download (progress mapped 0–100 → 35–90) |
| `analysing` | 93 | waveform + loudness (§13.5) |
| `saving` | 98 | move files into place, insert all rows in **one transaction** |
| `done` | 100 | |

Rules (same behaviour as the user's web app):
- **Duplicate:** if a non-deleted track with the same `deezer_id` or `isrc` exists, finish as DONE
  with the message *"Already in your library"*. Don't download anything.
- **Cancel:** QUEUED becomes CANCELLED immediately. RUNNING sets a flag that is checked between stages.
  During `audio`, call `YoutubeDL.getInstance().destroyProcessById(jobId)`. Always delete the job's
  work directory.
- **Failures** show a short, human message in the UI (never just an exception class name):
  - `"Search is busy. Try again in a minute."` (Deezer quota)
  - `"No match found for that search."`
  - `"No confident audio match. Choose one."` → status NEEDS_CHOICE (§7.6)
  - `"YouTube refused the download. Updating the downloader…"` → auto-update yt-dlp once, then retry once
  - `"No internet connection."`
  - otherwise `"Download failed: <first line of the error, max 160 chars>"`
- Optional stages (cover, artists, lyrics, genre, analysing) **never fail the job**. Only resolving
  and audio can fail it.

### 7.3 Queue execution

- Jobs are rows in `download_jobs`. The UI observes this table (not WorkManager's progress API).
- `DownloadQueue.enqueue()` inserts a row, then
  `WorkManager.enqueueUniqueWork("download-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, request)`.
  The worker requires `NetworkType.CONNECTED` (`UNMETERED` if "Wi-Fi only" is on).
- `DownloadWorker` is a foreground `CoroutineWorker` (`setForeground` with a progress notification,
  service type `dataSync`). It drains QUEUED jobs with **2 running in parallel** (setting: 1–3), and
  exits when none are left.
- On app start, reset jobs left as RUNNING (killed process) back to QUEUED.

### 7.4 Track identity and artists

- New track ID = 12-char random. Artist rows come from Deezer `contributors`: position 0 is MAIN,
  the others are FEATURED (Deezer `role`). Without Deezer (rare fallback), split the artist string
  only on `,`, ` feat. `, ` ft. `, ` featuring `. Also pull `(feat. X)` out of the title. **Never split
  on `&`** ("Simon & Garfunkel").
- Album row: `dz<albumId>`, title, primary artist, release date, genre; `cover_track_id` = first track
  downloaded from it.

### 7.5 youtubedl-android setup

- Gradle: `implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")`. No ffmpeg, no aria2c.
- Manifest `<application android:extractNativeLibs="true">` **and**
  `packaging { jniLibs { useLegacyPackaging = true } }`.
- `ndk { abiFilters += "arm64-v8a" }` (it bundles a Python runtime per ABI, so one ABI keeps the APK small).
- Init once on a background thread in `LumenApp` (`YoutubeDL.getInstance().init(appContext)`). The
  first run extracts Python and takes seconds. Expose a `ready: StateFlow<Boolean>`; the Add tab shows
  *"Preparing downloader…"* until it's ready.
- 0.18.1 adds `--js-runtimes quickjs:<path>` to requests automatically. **Verify** the bundled QuickJS
  is picked up: logcat must **not** show *"No supported JavaScript runtime could be found"*.
- Updates: a `PeriodicWorkRequest` every 24 h on unmetered network calls
  `YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)`.
  Settings shows `YoutubeDL.getInstance().version(appContext)` and an **Update now** button.
- If the Kotlin API in 0.18.1 differs (e.g. `YoutubeDL` is an `object`), adapt the calls. The
  behaviour stays the same.
- R8 (release builds): keep the library and its JSON parsing. Start with this and **verify a
  download works in the release build**:
  ```
  -keep class com.yausername.** { *; }
  -keep class com.fasterxml.jackson.databind.** { *; }
  -dontwarn com.fasterxml.jackson.databind.**
  ```

### 7.6 Finding and downloading the audio

1. **Candidates:** run yt-dlp in flat-search mode (JSON only, no download):
   ```kotlin
   val request = YoutubeDLRequest("ytsearch10:${primaryArtist} - ${title}").apply {
       addOption("--flat-playlist")
       addOption("--dump-single-json")
       addOption("--no-warnings")
   }
   val json = JSONObject(YoutubeDL.getInstance().execute(request).out)
   // entries[]: id, title, channel (fallback: uploader), duration (seconds, may be null)
   ```
2. **Score** every candidate with `scoreCandidate` (§13.4). Take the best with score ≥ 60.
   If none qualify, run a second search `"{artist} {title} audio"`, merge the results and score again.
3. Still nothing: save the top 5 candidates (title, channel, duration, thumbnail
   `https://i.ytimg.com/vi/{id}/mqdefault.jpg`) into `candidates` and set **NEEDS_CHOICE**. The Add tab
   shows *"Choose the right audio"*. When the user picks one, the job continues with `ReplaceAudio`-style
   logic for that video ID.
4. **Download** (no ffmpeg needed. Media3 plays AAC/m4a and Opus/webm natively):
   ```kotlin
   val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
       addOption("-f", "bestaudio[ext=m4a]/bestaudio")
       addOption("--no-playlist")
       addOption("--no-mtime")
       addOption("-o", File(workDir, "$trackId.%(ext)s").absolutePath)
   }
   YoutubeDL.getInstance().execute(request, jobId) { progress, _, _ ->
       if (progress >= 0) reportProgress(35 + (progress * 0.55f).toInt())
   }
   val audio = workDir.listFiles { f -> f.name.startsWith(trackId) }!!.single()
   ```
5. **Replace audio** (track menu → "Wrong audio? Choose another"): run steps 1–2, show the candidates,
   download the chosen one, swap the file, recompute waveform/gain, and update `youtube_id`. The track ID and stats stay the same.
6. YouTube sometimes answers *"Sign in to confirm you're not a bot"*. Show that as a clear error.
   **Don't** build cookie or login support.

### 7.7 Share target and links

- `MainActivity` intent filter: `ACTION_SEND`, `text/plain`. Extract the first URL. Spotify `track` →
  SpotifyLink; Spotify `album` → resolve the album on Deezer → enqueue all tracks; `youtube.com` /
  `youtu.be` / `music.youtube.com` → YouTubeLink; anything else → snackbar *"Not a song link"*.
- After enqueueing: open the Add tab and show a snackbar *"Added to downloads"*.
- The Add tab has a **Paste link** button that reads the clipboard only when tapped.
- For a YouTube link: yt-dlp `--dump-single-json --no-playlist` gives the title/uploader. Search Deezer
  with that for metadata (if it matches within ±5 s of the video length). Otherwise use the YouTube
  title/uploader as the metadata and the video thumbnail as the cover. The audio comes from **that exact
  video**.

---

## 8. Playback

### 8.1 Service

`PlaybackService : MediaSessionService`
- ExoPlayer: `setAudioAttributes(AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC), handleAudioFocus = true)`,
  `setHandleAudioBecomingNoisy(true)` (pause when headphones are unplugged), `setWakeMode(C.WAKE_MODE_LOCAL)`.
  Gapless playback is the default.
- A `MediaSession` with a session activity `PendingIntent` that opens the app on Now Playing.
- Manifest: `android:foregroundServiceType="mediaPlayback"`, exported, with the
  `androidx.media3.session.MediaSessionService` intent filter.
- `onTaskRemoved`: stop the service if it's not playing.
- `MediaSession.Callback.onPlaybackResumption` restores the saved queue, so "play" from the system
  media controls / Bluetooth works after the app was killed.
- Notification: Media3's default media notification (prev / play-pause / next). Artwork comes from the
  `artworkUri` (a `file://` URI for `covers/{id}.jpg`).

### 8.2 MediaItems

```kotlin
MediaItem.Builder()
    .setMediaId(track.id)
    .setUri(Uri.fromFile(paths.resolve(track.audioFile)))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistLine)        // "Main, Featured"
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(Uri.fromFile(paths.cover(track.id)))
            .setDurationMs(track.durationMs)    // ListeningTracker reads this
            .build()
    ).build()
```

### 8.3 QueueManager (shuffle is ours, not ExoPlayer's)

ExoPlayer's built-in shuffle inserts "Play next" items at random positions. So **shuffle is done in
`QueueManager`**, and ExoPlayer always plays the list in order (`shuffleModeEnabled = false`).

- `play(tracks, startIndex, context, shuffle)`: builds the list. With shuffle on, the started song goes
  first and the rest is shuffled. Keep `originalOrder` (IDs) so shuffle can be turned off again.
- `toggleShuffle()`: ON → shuffle the items **after** the current one using
  `replaceMediaItems(current + 1, end, shuffled)`. OFF → rebuild the original order around the current item.
- `playNext(track)`: insert at `current + 1 + userQueuedCount` (first-in, first-out among "play next" items).
  `addToQueue(track)`: insert after the last user-queued item. User-queued items are shown in their own
  "Next in queue" section (§12.4) and removed from the count as they play.
- Repeat uses ExoPlayer's `repeatMode` (OFF → ALL → ONE).
- Previous: ExoPlayer's `seekToPrevious()` already restarts the song if more than 3 s have played.
- **Persistence** (DataStore, every 5 s while playing and on pause): queue IDs in the current order,
  original order, current index, position, shuffle, repeat, user-queued count, and the play context.
  Restore on app start **paused**.
- `PlaybackContextHolder` (singleton): the current `PlayContext` + ID, read by `ListeningTracker`.

### 8.4 PlayerConnection

Wraps `MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()`.
Exposes `StateFlow`s: `nowPlaying: TrackUi?`, `isPlaying`, `positionMs` (polled every 250 ms only while
a player UI is visible; lyrics poll at 100 ms), `durationMs`, `queue`, `shuffle`, `repeat`.

### 8.5 Extras

- **Volume normalisation** (Settings, default on): on every media-item transition, set
  `player.volume = 10f.pow(gainDb / 20f)` (`gainDb ≤ 0`, so only quieter, never clipping).
- **Sleep timer:** 15 / 30 / 45 / 60 / 90 min or *end of this song*. Fade the volume to 0 over the last
  10 s, pause, then restore the volume. Show the time left in the player.
- Swiping the mini player left/right skips next/previous.

---

## 9. Listening tracker and Pulse (must-have #3)

### 9.1 Definitions (show these in Pulse's "How it's counted" sheet)

- **Listening time** = real time the audio was playing (pauses and seeking don't count).
- A **play** = a listen of ≥ 30 s (Spotify's rule), or ≥ 50 % of the song for songs shorter than 60 s.
- **Skip** = the user moved away with < 30 s and < 50 % listened.
- **Completed** = the song reached its end (or repeated).
- **Session** = listening with gaps shorter than 15 minutes.
- **First listen** = the first play of a song ever.
- Periods use the phone's local time zone. Weeks start on the locale's first day of the week.

### 9.2 ListeningTracker (runs inside PlaybackService)

```kotlin
class ListeningTracker @Inject constructor(
    private val events: PlayEventDao,
    private val tracks: TrackDao,
    private val context: PlaybackContextHolder,
    private val focus: FocusRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : Player.Listener {

    private class Open(val id: String, val trackId: String, val durationMs: Long, val startedAt: Long) {
        var listenedMs = 0L
        var playingSince: Long? = null
        fun total(now: Long) = listenedMs + (playingSince?.let { now - it } ?: 0L)
    }

    private val writer = Dispatchers.IO.limitedParallelism(1)   // keeps writes in order
    private var player: Player? = null
    private var open: Open? = null
    private var sessionId = UUID.randomUUID().toString()
    private var lastEndedAt = 0L
    private var ticker: Job? = null

    fun attach(player: Player) {
        this.player = player
        player.addListener(this)
        player.currentMediaItem?.let { start(it) }
    }

    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
        val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        close(if (ended) EndReason.COMPLETED else null)
        item?.let { start(it) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val o = open ?: return
        val now = SystemClock.elapsedRealtime()
        if (isPlaying && o.playingSince == null) o.playingSince = now
        if (!isPlaying && o.playingSince != null) {
            o.listenedMs += now - o.playingSince!!
            o.playingSince = null
            save(o, null)
        }
        if (isPlaying) startTicker() else ticker?.cancel()
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) close(EndReason.COMPLETED)
    }

    fun release() { close(EndReason.STOPPED); player?.removeListener(this) }

    private fun start(item: MediaItem) {
        val now = System.currentTimeMillis()
        if (now - lastEndedAt > 15 * 60_000) sessionId = UUID.randomUUID().toString()
        open = Open(UUID.randomUUID().toString(), item.mediaId, item.mediaMetadata.durationMs ?: 0, now)
            .also { if (player?.isPlaying == true) it.playingSince = SystemClock.elapsedRealtime() }
    }

    private fun close(reason: EndReason?) {
        val o = open ?: return
        open = null
        ticker?.cancel()
        val listened = o.total(SystemClock.elapsedRealtime())
        val short = listened < 30_000 && (o.durationMs == 0L || listened < o.durationMs / 2)
        val final = reason ?: if (short) EndReason.SKIPPED else EndReason.STOPPED
        lastEndedAt = System.currentTimeMillis()
        if (listened < 1_000) { scope.launch(writer) { events.delete(o.id) }; return }
        save(o, final, listened)
    }

    private fun save(o: Open, reason: EndReason?, listened: Long = o.total(SystemClock.elapsedRealtime())) {
        val snapshot = context.current
        val focusActive = focus.isActive
        scope.launch(writer) {
            val first = !events.hasQualifyingPlay(o.trackId, excludeId = o.id)
            events.upsert(
                PlayEventEntity(
                    o.id, o.trackId, o.startedAt, System.currentTimeMillis(), listened, o.durationMs,
                    reason, snapshot.type, snapshot.id, focusActive, sessionId, first,
                )
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main) {   // same thread as the Player callbacks
            while (isActive) { delay(30_000); open?.let { save(it, null) } }   // checkpoint: survives kills
        }
    }
}
```

`hasQualifyingPlay` = `EXISTS(SELECT 1 FROM play_events WHERE track_id = :trackId AND id != :excludeId AND listened_ms >= 30000)`.
Treat rows with `end_reason = NULL` that aren't currently open as STOPPED.

### 9.3 What Pulse shows

Range selector: **Today · Week · Month · Year · All time**, with ‹ › arrows to go back through earlier
weeks/months/years. Everything updates live, because Room re-emits whenever a play event is
written (every 30 s while playing).

| Card | Content |
|---|---|
| Hero | Total listening time (big number counting up), plays, and the change vs the previous period (▲ 12 %) |
| Top songs | Top 5 by plays (listening time breaks ties), with cover and count. "See all" → top 50 |
| Top artists | Top 5 by listening time, with circular photos |
| Top albums | Top 5 |
| Top genres | Horizontal bars, share of listening time |
| Listening clock | 24-hour radial chart of listening time per hour, with a marker on the current hour |
| Week rhythm | 7 bars, Mon–Sun |
| Calendar | Heatmap: a month grid (Month range) or 53×7 GitHub-style grid (Year/All), 5 intensity levels |
| Trend | Minutes per day (line/sparkline) across the period |
| Streaks | Current and longest streak of days with ≥ 1 play |
| Discovery | Songs added in the period, first listens, % of time on songs new to you |
| Habits | Skip rate, completion rate, average and longest session, most active day ever |
| On repeat moment | Most plays of one song in a single day ("You played *X* 14 times on 3 March") |
| Listening personality | Archetype (§9.5), plus up to 2 secondary traits |
| Library | Number of songs, total hours, size on disk, growth over time |

- **Empty states:** if the period has < 20 minutes, show *"Not enough listening yet"* with a progress
  bar towards 20 minutes. Never show "NaN" or a 0 % chart.
- Focus interaction: see §10.3.
- "How it's counted" info sheet (§9.1).

### 9.4 Stats SQL (local time buckets)

```sql
-- total listening time
SELECT COALESCE(SUM(listened_ms), 0) FROM play_events
WHERE started_at >= :from AND started_at < :to;

-- top tracks (plays = listens of 30 s or more)
SELECT track_id AS trackId, SUM(listened_ms >= 30000) AS plays, SUM(listened_ms) AS listenedMs
FROM play_events WHERE started_at >= :from AND started_at < :to
GROUP BY track_id HAVING plays > 0
ORDER BY plays DESC, listenedMs DESC LIMIT :limit;

-- top artists (main and featured artists both get credit)
SELECT ta.artist_id AS artistId, SUM(pe.listened_ms) AS listenedMs, SUM(pe.listened_ms >= 30000) AS plays
FROM play_events pe JOIN track_artists ta ON ta.track_id = pe.track_id
WHERE pe.started_at >= :from AND pe.started_at < :to
GROUP BY ta.artist_id ORDER BY listenedMs DESC LIMIT :limit;

-- hour of day / weekday / calendar day
SELECT CAST(strftime('%H', started_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS bucket,
       SUM(listened_ms) AS listenedMs
FROM play_events WHERE started_at >= :from AND started_at < :to GROUP BY bucket;
-- weekday: strftime('%w', …) (0 = Sunday); day: strftime('%Y-%m-%d', …)
```

For the Focus-only scope, add
`AND pe.track_id IN (SELECT track_id FROM track_artists WHERE artist_id IN (:focusIds))`.
Compute streaks, sessions, archetypes and period-over-period changes in Kotlin (`StatsCalculator`,
pure functions with unit tests).

### 9.5 Listening personality (rule-based)

| Archetype | Rule (share of the period's listening time unless noted) |
|---|---|
| Night Owl | ≥ 35 % between 22:00 and 04:00 |
| Early Bird | ≥ 30 % between 05:00 and 09:00 |
| Loyalist | Top artist ≥ 40 % |
| Explorer | ≥ 35 % of plays are first listens, or ≥ 15 artists with none above 20 % |
| On Repeat | Average plays per distinct song ≥ 6 |
| Marathoner | Average session ≥ 75 min |
| Weekend Warrior | ≥ 45 % on Saturday and Sunday |
| Completionist | Completion rate ≥ 85 % and skip rate ≤ 10 % |
| Skipper | Skip rate ≥ 35 % |

Strength = how far past its threshold a rule is (value ÷ threshold). The strongest is the main
archetype, and the next two are traits. If nothing matches: **"Balanced Listener"**. Each archetype has
one line of friendly copy and a small custom icon.

### 9.6 Recap stories (the Wrapped experience)

- Available for any finished month, finished year, or "This month so far". Open from Pulse ("Your
  September →") or a Home card on the 1st–7th of each month.
- Full-screen story slides, auto-advancing every 6 s. Tap right/left = next/previous, hold = pause.
  Progress segments at the top.
- Slides: intro → total minutes (count-up) → top song (huge cover, play button that plays it) → top
  artist (photo) → top 5 songs → listening clock peak ("You're a 23:00 listener") → personality →
  streak → discoveries → summary card.
- **Share:** the summary card is rendered to a bitmap (`rememberGraphicsLayer()` →
  `toImageBitmap()`), saved to `Lumen/exports/recap-YYYY-MM.png`, and shared through the share sheet
  via a `FileProvider`.
- A WorkManager daily check posts a notification on the 1st of the month: *"Your September recap is
  ready"* (only if that month had ≥ 60 minutes of listening).

---

## 10. Artist Focus (must-have #4)

### 10.1 Behaviour

- Focus = a set of one or more artists, stored in DataStore (`focus_artist_ids`). It persists across
  app restarts until cleared.
- **A song matches** if any of its credited artists (main **or featured**) is in the set.
- While Focus is on, the whole app is scoped to matching songs:
  - Home sections, Library ▸ Songs / Albums, local search results.
  - Playlists: show only matching songs, with a banner *"Showing 4 of 25 · Focus: Talk Talk · Show all"*.
    "Show all" lifts the filter for that screen only.
  - Library ▸ Artists still lists **all** artists (you choose focus artists there), with focused ones
    pinned first and highlighted.
  - Every Play/Shuffle builds the queue from the filtered list, so **only focused songs play**.
- **Not** affected: the Add tab (downloading) and Settings.
- If Focus is turned on while a non-matching song is playing, keep playing (don't interrupt), but show
  a snackbar: *"Play only Talk Talk now?"* → shuffles the focused songs.

### 10.2 Entry points

- Artist page → **Focus** toggle button.
- Long-press an artist in Library ▸ Artists → *Focus on this artist* / *Add to focus*.
- Now Playing → tap the artist name → *Focus on {artist}*.
- The Focus pill in the top bar (when active) or the target icon in Home's header → **Focus sheet**:
  search artists, multi-select chips with photos, *Clear focus*.

### 10.3 Visual state (it must be impossible to miss)

- A **Focus pill** under every main screen title: artist photo(s) stacked + "Talk Talk" + ✕, ember-tinted.
- The app's accent switches to the focused artist's photo tone.
- A very soft vignette (accent at 6 % alpha) around the screen edges.
- Pulse: segmented control **Everything | Focus** (default *Everything*). It also shows a "Focus card"
  at the top with the focused artists' stats (minutes, plays, top song, first listened).

---

## 11. Design system

### 11.1 Concept: "Ink & Glow"

Editorial and quiet. Near-black ink, warm paper-white type, a serif display face for titles and big
numbers, and **one glowing accent per context** taken from the cover art. No cards-in-cards, no
gradients-everywhere, no Spotify green. Lots of air. The cover art and the type carry the design.

It must **not** look like the web player (which uses a 3D cassette and gold tones). The signature
elements here are the **waveform scrubber**, **cover glow**, **serif typography**, the **floating pill
mini player** and the **lyrics peek**.

### 11.2 Colour tokens (dark only)

| Token | Hex | Use |
|---|---|---|
| `ink` | `#0B0B0C` | app background |
| `ink0` | `#000000` | Now Playing, lyrics, recap (OLED black) |
| `ink2` | `#141416` | sheets, mini player, inputs |
| `ink3` | `#1C1C1F` | pressed / selected rows, chips |
| `line` | `#27272B` | 1 px hairlines (use rarely) |
| `paper` | `#F3EFE8` | primary text |
| `paper2` | `#A8A39B` | secondary text |
| `paper3` | `#6B675F` | tertiary, disabled, unplayed waveform bars |
| `ember` | `#FF5A36` | brand accent (fallback when there's no cover tone): play button, active states, Focus |
| `emberSoft` | `#FF5A36` @ 14 % | chip/pill backgrounds |
| `positive` | `#7BD88F` | success, "in library" |
| `danger` | `#FF6B6B` | delete, errors |

**Dynamic accent from the cover tone** `(hue, sat)` (§13.3):
- `accent = hsl(hue, clamp(sat, 0.50, 0.85), 0.64)`: icons, played waveform, active lyric glow
- `glow = hsl(hue, clamp(sat × 0.9, 0.35, 0.70), 0.32)`: radial background glow
- `deep = hsl(hue, min(0.60, sat + 0.06), 0.07)`: bottom of gradients
- No tone → `seededTone(title)` (hash → hue, fixed sat 0.55).

Check `accent` against `ink0` for ≥ 4.5:1 contrast. If it's below that, raise the lightness in 0.04 steps.

### 11.3 Typography

Bundle the variable fonts (OFL licence) from `github.com/google/fonts`:
`ofl/fraunces/Fraunces[SOFT,WONK,opsz,wght].ttf` → `res/font/fraunces.ttf`, and
`ofl/manrope/Manrope[wght].ttf` → `res/font/manrope.ttf`. Use `FontVariation.Settings` for
weight / `opsz` / `SOFT`.

| Style | Font | Size / line | Notes |
|---|---|---|---|
| `displayHero` | Fraunces 400, opsz 144, SOFT 50 | 72 / 72 sp | Pulse hero numbers |
| `displayLarge` | Fraunces 400, opsz 96 | 44 / 48 | screen titles ("Library"), artist names |
| `display` | Fraunces 450, opsz 48 | 30 / 36 | Now Playing title (max 2 lines; shrink to 24 for long titles) |
| `lyric` | Fraunces 500, opsz 36 | 28 / 36 | synced lyrics |
| `title` | Manrope 650 | 20 / 26 | section titles |
| `body` | Manrope 550 | 16 / 22 | track titles in lists |
| `label` | Manrope 500 | 13 / 18 | artist lines, chips, metadata |
| `overline` | Manrope 700, +0.08 em, UPPERCASE | 11 / 14 | "ON REPEAT", "PLAYING FROM" |
| `numeric` | Manrope 600, `fontFeatureSettings = "tnum"` | 12 / 16 | time codes, counts |

### 11.4 Space, shape, motion, haptics

- 4 dp grid: 4, 8, 12, 16, 20, 24, 32, 48. **Screen side margin 20 dp.**
- Radii: covers in lists 6 dp · cards/covers 14 dp · sheets 28 dp · mini player 20 dp · artists = circle.
- Track row: 64 dp tall, 48 dp cover, title `body`, artist `label`/`paper2`, trailing duration or ⋯.
  **No row backgrounds or dividers**; spacing separates rows.
- Motion: standard easing `CubicBezier(0.2, 0, 0, 1)`, 200 / 300 / 450 ms. Sheets use a spring
  (`dampingRatio 0.85`, `StiffnessMediumLow`). First-load lists fade in and rise 8 dp, staggered
  20 ms (first 12 items only). The like heart pops 1 → 1.25 → 1.
- Ambient animation: the Now Playing glow "breathes" (scale 1.00 ↔ 1.06, 8 s loop) only while playing.
  Settings → **Reduce motion** turns off all ambient and staggered animations (also needed for adb UI
  dumps, §14.3).
- Haptics: play/pause (light click), a tick at every 10 % while dragging the waveform, long-press
  menus, drag-reorder pick-up/drop.
- 120 Hz: keep compositions cheap. Use stable/immutable UI models, `key`s in every Lazy list, and no
  allocations inside draw lambdas.

### 11.5 Shared components (`core/design`)

`LumenTheme`, `TrackRow`, `CoverImage` (thumbnail/large, rounded, placeholder = seeded tone gradient
+ first letter in Fraunces), `ArtistAvatar`, `PlaylistCover` (custom image or 2×2 mosaic of the first 4
distinct covers), `GlowBackground(tone)`, `SectionHeader(overline, title, action)`, `PillButton`
(filled paper / outlined / ember), `PlayFab` (56 dp paper circle with an ink icon), `FocusPill`,
`SegmentedTabs`, `SortChip`, `EmptyState(serifLine, body, action)`, `LumenSheet` (modal bottom sheet
style), `Snackbar` style, `WaveformScrubber`, `CountUpText`, the chart primitives (§12.9).

---

## 12. Screens

### 12.1 App shell

- `MainActivity` → `enableEdgeToEdge()`; root `Box` with a `NavHost`, the **floating mini player** and
  a **bottom nav**, plus the **player sheet** overlay.
- Bottom nav (4 tabs): **Home** · **Library** · **Pulse** · **Add**. Background `ink` with a top
  hairline. Icon + small label. The active tab shows paper colour + a 4 dp ember dot under the icon;
  inactive is `paper3`.
- Mini player: a floating pill 64 dp tall, 12 dp side margins, 8 dp above the nav, `ink2`, radius 20.
  44 dp cover, title + artist, play/pause; a 2 dp progress line in the accent along its bottom inside
  edge. Tap or swipe up → expand; swipe left/right → next/previous.
- **Player sheet** = an overlay controlled by `AnchoredDraggableState` (collapsed ↔ expanded). The
  expand fraction animates the cover from its mini-player position/size to the full-width cover, and
  fades the controls in. Back press collapses it.
- Root: `Modifier.semantics { testTagsAsResourceId = true }`, and give every interactive element a
  `testTag` (for adb testing, §14.3).

### 12.2 Onboarding (first launch only)

1. **Welcome:** "Lumen" in Fraunces 72, one line: *"Your music. On your phone. Nowhere else."* → Continue.
2. **Storage:** explain the `Lumen/` folder (show its tree) → *Allow access* opens
   `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for the package. Re-check
   `Environment.isExternalStorageManager()` on resume. Secondary option: *"Use private storage
   instead"* (§4.1 fallback).
3. **Restore** (only if a snapshot exists, §4.3).
4. **Notifications** (`POST_NOTIFICATIONS` on Android 13+): for playback controls and download progress.
5. **Background:** request `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Then MIUI help: a button that
   tries to open `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`
   (in a try/catch), plus text: *"Turn on Autostart for Lumen, and set Battery saver to No restrictions."*
6. Done → folders created → Home (empty state).

### 12.3 Home

- Header: time-based greeting in Fraunces ("Good evening"), the date as an overline. Search and settings
  icons on the right; the Focus pill under the greeting when active.
- Sections (hide any that are empty):
  1. **Pick up where you left off:** a large card for the last queue, with a resume button.
  2. **Pulse strip:** `Today 42 min · 6-day streak · Top: Talk Talk` → opens Pulse.
  3. **Recap card** on the 1st–7th of the month.
  4. **On repeat:** most plays in the last 30 days (horizontal, 140 dp covers).
  5. **For this hour:** songs you play most around this hour (±1 h) across all your history. Tap plays them all.
  6. **Your playlists:** pinned first.
  7. **Artists you love:** circles, by listening time.
  8. **Recently added.**
  9. **Rediscover:** ≥ 3 lifetime plays but not played in 60+ days.
- Empty library: *"Your library is quiet."* (Fraunces) + *"Add your first song"* → Add tab.

### 12.4 Now Playing (player sheet expanded) and queue

- Background `ink0` + `GlowBackground(glow)`: a radial glow from behind the cover.
- Top bar: collapse chevron · overline `PLAYING FROM` + context name ("Liked Songs") · ⋯ (track menu).
- Cover: width = screen − 40 dp, radius 14, soft accent shadow. A **horizontal pager** swipes to the
  previous/next song.
- Title (`display`), artist line (`label`, each artist tappable → menu: open artist / Focus), heart on the right.
- **Waveform scrubber** (56 dp tall): 96 bars from `track.waveform`. Played bars in `accent`, unplayed in
  `paper3` at 50 %, with a small playhead dot. Drag to seek (haptic ticks). Missing waveform → flat bars.
  Time codes underneath (`numeric`, elapsed left, remaining right).
- Controls: shuffle · previous · **play/pause (72 dp paper circle, ink icon)** · next · repeat
  (repeat-one shows a small "1").
- **Lyrics peek:** a rounded `ink2` card under the controls showing the current synced line (crossfading).
  Tap or swipe up → full lyrics. Hidden when there are no lyrics.
- Bottom row: Lyrics · Queue · Sleep timer (shows time left when active) · Add to playlist.
- **Queue sheet:** "Now playing" row; **Next in queue** (user-added, with a "Clear" action); **Next from:
  {context}**. Drag handles to reorder, swipe to remove.

### 12.5 Library (must-have #1)

- Title "Library" (`displayLarge`), Focus pill, `SegmentedTabs`: **Songs · Playlists · Artists · Albums**.
- **Songs = All Songs:** a header line `1,240 songs · 82 h`, a **Shuffle** button (filled) and a
  **Play** button (outlined); a sort chip (Recently added / Title / Artist / Most played / Duration);
  an alphabet fast-scroll rail on the right for the Title/Artist sorts. Tap = play this list from that
  song. ⋯ = track menu. Long-press → **multi-select** (Add to playlist, Play next, Like, Delete).
- **Artists:** 3-column grid of circular photos + names; sort by Name / Listening time. Focused artists
  are pinned first. Long-press = focus actions.
- **Albums:** 2-column grid of covers (title, artist · year).
- **Playlists:** see §12.7.
- **Track menu** (bottom sheet): Play next · Add to queue · Add to playlist… · Like/Unlike · Go to artist ·
  Go to album · Focus on artist · Edit info (title, genre) · Lyrics: re-fetch / adjust offset · Wrong
  audio? Choose another · Delete from device (confirm).

### 12.6 Artist and album pages

- **Artist:** full-bleed photo header (360 dp) fading into ink, name in `displayLarge`. Buttons:
  **Play** · **Shuffle** · **Focus** (toggle pill, ember when on). A stats line: `6 h 12 min · 84 plays ·
  since Mar 2026`. Then: Your top songs (5), All songs, Albums (horizontal), Appears on (featured credits).
- **Album:** cover 240 dp centred with the glow behind it, title, artist, `1991 · Rock · 5 songs ·
  42 min`, Play/Shuffle, songs in `track_position` order.

### 12.7 Playlists (must-have #2)

- List: **Create playlist** row at the top. System playlists: **Liked Songs** (ember gradient cover with
  a heart), and the smart, read-only playlists **On Repeat** (top 30 by plays, last 30 days), **Recently
  Added** (last 50), **Forgotten Gems** (≥ 3 plays, none in 60 days). Then user playlists (pinned first,
  then most recently updated). Each row: cover, name, `Playlist · 24 songs`.
- **Create:** bottom sheet with a name field (default `My playlist #N`) → create → open it. The empty state
  shows an *Add songs* button.
- **Detail:** glow header from the cover tone; 200 dp cover; name (`display`); description;
  `24 songs · 1 h 32 min`. Buttons: Play · Shuffle · Add songs · ⋯ (Edit name & description, Change cover,
  Remove custom cover, Pin, Delete). Sort chip: Custom order (default) / Title / Artist / Recently added.
- **Edit mode:** drag handles (reorderable) to reorder and swipe left to remove (with an *Undo* snackbar).
  Reordering rewrites `position` values in one transaction.
- **Add songs picker:** full screen, search field and checkboxes over the whole library (with a pre-selected
  chip *"Only {focus artist}"* when Focus is active), and an *Add N songs* button.
- **Add to playlist sheet** (from the track menu / multi-select): a list of playlists with checkmarks on the
  ones that already contain the song, plus *New playlist*. A duplicate → *"Already in {name}"*.
- **Custom cover:** Photo Picker (`PickVisualMedia`) → centre-crop square → 1000 px JPEG →
  `playlists/{id}.jpg`.

### 12.8 Search (local library)

Opened from Home/Library headers. One field, case- and accent-insensitive (`normalize()` in §13.4).
It matches title, artists and album, and respects Focus. Results are grouped: Songs, Artists, Albums,
Playlists. Recent searches (last 10) appear when the field is empty.

### 12.9 Pulse

- Title "Pulse" (`displayLarge`), range selector as `SegmentedTabs` + ‹ period label ›, the Focus segmented
  control when Focus is active, and an ⓘ for "How it's counted".
- Magazine layout: a big hero number, thin section rules, every chart in **one accent colour** on ink.
  No legends unless needed, labels in `numeric`.
- **Charts are custom `Canvas` composables** (no chart library): `RadialClock`, `BarRow`, `HBarList`,
  `CalendarHeatmap`, `Sparkline`. Animate values in on first show (respect Reduce motion).
- Tapping a top song/artist/album opens that page.
- "Your {Month} recap →" opens §9.6.

### 12.10 Add (downloads)

- Title "Add music". A big search field (Deezer search, debounce 350 ms), a **Paste link** button, and the
  "Preparing downloader…" state (§7.5).
- Results: cover, title, artist · album, duration, and a trailing **+** (or a green check "In library").
  Albums in the results: *Download album (12)*.
- **Downloads section:** in-progress cards (cover, title, stage label like "Finding audio…", progress bar,
  cancel ✕); a *Recent* list (done → tap plays; failed → message + *Retry*; NEEDS_CHOICE → *Choose audio*
  sheet with candidate thumbnails, durations and channel names); *Clear finished*.

### 12.11 Settings

- **Storage:** folder path, space used per folder, free space; "Hide songs from other apps"
  (`.nomedia` toggle, rescan with `MediaScannerConnection`); **Back up now**; **Restore from backup**;
  **Check library** (finds missing files and orphan files, offers to fix).
- **Playback:** volume normalisation, resume on launch.
- **Downloads:** Wi-Fi only, parallel downloads (1–3), preferred format (m4a / best available),
  yt-dlp version + **Update now**.
- **Listening data:** "How it's counted"; **Reset statistics** (double confirmation).
- **Appearance:** Reduce motion.
- **About:** app version, licences (fonts OFL, libraries), "Personal use only."
- **Debug** (debug builds only): *Seed 1,500 fake songs*, *Seed 1 year of fake plays* (rows tagged
  `DEBUG_SEED` / track IDs starting `seed`), *Remove seeded data*, *Export logs*.

---

## 13. Reference code (ported from the user's web app + new logic)

### 13.1 LRC lyrics: parse, find the active line

Ported from the web app's server parser and its lyrics view (multiple timestamps per line, 2- or
3-digit fractions, a blank intro line if the first line starts after 4 s, 180 ms look-ahead).

```kotlin
data class LyricLine(val timeMs: Long, val text: String)
data class ParsedLyrics(val synced: Boolean, val lines: List<LyricLine>)

private val LRC_LINE = Regex("""((?:\[\d+:\d+(?:[.:]\d+)?\])+)(.*)""")
private val LRC_TAG = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\]""")

fun parseLyrics(raw: String): ParsedLyrics {
    val synced = mutableListOf<LyricLine>()
    val plain = mutableListOf<String>()
    for (line in raw.lineSequence().map { it.trim() }) {
        val match = LRC_LINE.matchEntire(line)
        if (match != null) {
            val text = match.groupValues[2].trim()
            for (tag in LRC_TAG.findAll(match.groupValues[1])) {
                val (minutes, seconds, fraction) = tag.destructured
                var ms = (minutes.toLong() * 60 + seconds.toLong()) * 1000
                if (fraction.isNotEmpty()) ms += (fraction.toDouble() / 10.0.pow(fraction.length) * 1000).toLong()
                synced += LyricLine(ms, text)
            }
        } else if (line.isNotEmpty() && !line.startsWith("[")) {
            plain += line
        }
    }
    if (synced.isNotEmpty()) {
        synced.sortBy { it.timeMs }
        if (synced.first().timeMs > 4_000) synced.add(0, LyricLine(0, ""))
        return ParsedLyrics(true, synced)
    }
    return ParsedLyrics(false, plain.map { LyricLine(-1, it) })
}

fun activeLineIndex(lines: List<LyricLine>, positionMs: Long, lookaheadMs: Long = 180): Int {
    var lo = 0
    var hi = lines.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs + lookaheadMs) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
}
```

**Lyrics screen:** `LazyColumn` of `lyric`-style lines, left-aligned with 20 dp margins. The active line
is `paper` at scale 1.0; ±1 lines `paper` at 45 %; the rest at 20 % with `Modifier.blur(2.dp)` (API 31+;
skip the blur below 31). Scale animates 0.96 → 1.0 with a spring. The active line auto-scrolls so it
sits about 35 % from the top. If the user scrolls manually, pause auto-scroll for 3 s. Tap a line → seek.
Empty lines in synced lyrics = instrumental break → three pulsing dots ♪ ♪ ♪. The per-track
`lyrics_offset_ms` is added to the position (adjust ±100 ms from the ⋯ menu). Plain lyrics: Manrope 20 sp
static scroll with a small "Not synced" overline. None: *"No lyrics for this one. Just listen."* in Fraunces.

**Test with invented LRC only** (never real lyrics in the repo):
```
[00:05.20]first test line
[00:09.75][00:40.10]repeated chorus line
[00:15.00]
[00:21.333]third line with three-digit fraction
```

### 13.2 Spotify link → track ID

Ported from the web app's fix: tracking parameters (`?si=…`) broke file names there.

```kotlin
private val SPOTIFY_TRACK = Regex(
    """https?://open\.spotify\.com/(?:intl-[a-z-]+/)?track/([A-Za-z0-9]{22})""",
    RegexOption.IGNORE_CASE,
)
private val SPOTIFY_ALBUM = Regex(
    """https?://open\.spotify\.com/(?:intl-[a-z-]+/)?album/([A-Za-z0-9]{22})""",
    RegexOption.IGNORE_CASE,
)
private val OG_DESCRIPTION = Regex("""<meta property="og:description" content="([^"]+)"""")

fun spotifyTrackId(text: String): String? = SPOTIFY_TRACK.find(text)?.groupValues?.get(1)

// artist from the track page: "Talk Talk · Laughing Stock · Song · 1991" -> "Talk Talk"
fun spotifyArtistFromPage(html: String): String? =
    OG_DESCRIPTION.find(html)?.groupValues?.get(1)
        ?.substringBefore(" · ")
        ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
        ?.takeIf { it.isNotEmpty() }
```

Unit tests: plain URL, `?si=abc&nd=1`, `/intl-de/` prefix, album URL (not a track), short ID → null.

### 13.3 Cover tone (ported from the web app's colour analysis)

Scale the cover to 48×48 and bucket the pixels into 24 hue buckets (15° each). Skip very dark, very light
and grey pixels. The winner is the bucket with the largest sum of saturation²; its average saturation
gives the tone's saturation.

```kotlin
data class CoverTone(val hue: Float, val saturation: Float)

fun coverTone(bitmap: Bitmap): CoverTone? {
    val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
    val pixels = IntArray(48 * 48).also { small.getPixels(it, 0, 48, 0, 0, 48, 48) }
    val counts = IntArray(24)
    val weights = FloatArray(24)
    val sums = FloatArray(24)
    val hsl = FloatArray(3)
    for (pixel in pixels) {
        if (Color.alpha(pixel) < 128) continue
        ColorUtils.colorToHSL(pixel, hsl)                // h 0..360, s 0..1, l 0..1
        val (hue, sat, light) = Triple(hsl[0], hsl[1], hsl[2])
        if (light < 0.10f || light > 0.95f || sat < 0.14f) continue
        val bucket = (hue / 15f).toInt() % 24
        counts[bucket]++
        weights[bucket] += sat * sat
        sums[bucket] += sat
    }
    val top = (0 until 24).filter { counts[it] > 0 }.maxByOrNull { weights[it] } ?: return null
    return CoverTone(top * 15f + 7.5f, (sums[top] / counts[top]).coerceIn(0.2f, 0.85f))
}

fun seededTone(text: String): CoverTone {
    var hash = 0
    for (c in text) hash = (hash * 31 + c.code) % 100_000
    return CoverTone((hash % 360).toFloat(), 0.55f)
}

fun hsl(hue: Float, sat: Float, light: Float): Int = ColorUtils.HSLToColor(floatArrayOf(hue, sat, light))
```

Compute the tone at download time and store it on the track (and on the artist, from the artist photo).
Never decode cover bitmaps on the UI thread.

### 13.4 YouTube candidate scoring

```kotlin
data class AudioCandidate(val videoId: String, val title: String, val channel: String, val durationSec: Int?)

private val VARIANT_WORDS = listOf(
    "live", "cover", "remix", "karaoke", "instrumental", "sped up", "slowed", "reverb", "8d",
    "nightcore", "acoustic", "demo", "extended", "mashup", "tutorial", "reaction", "edit",
)

fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

// "Song (feat. X) - Remastered 2011" -> "Song"
fun coreTitle(title: String): String =
    title.replace(Regex("""\s*[(\[](feat|ft|with)\.?\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+.*(remaster|version|mono|stereo|edit).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

fun scoreCandidate(candidate: AudioCandidate, title: String, artist: String, durationSec: Int?): Int? {
    var score = 100
    if (durationSec != null && candidate.durationSec != null) {
        val diff = abs(candidate.durationSec - durationSec)
        if (diff > 15) return null
        score -= diff * 4
    }
    val candidateTitle = normalize(candidate.title)
    val wantTitle = normalize(coreTitle(title))
    val wantFull = normalize(title)
    score += if (candidateTitle.contains(wantTitle)) 20 else -30
    if ("$candidateTitle ${normalize(candidate.channel)}".contains(normalize(artist))) score += 10
    if (candidate.channel.endsWith(" - Topic")) score += 25          // official "Art Track" uploads
    if ("official audio" in candidateTitle) score += 10
    if ("official video" in candidateTitle || "music video" in candidateTitle) score -= 8
    for (word in VARIANT_WORDS) {
        val inCandidate = Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(candidateTitle)
        if (inCandidate && !wantFull.contains(word)) score -= 40
    }
    return score
}
```

Unit tests: exact Topic upload wins over the music video; "Live at Wembley" loses unless the wanted title
is live; a title like *Live Forever* isn't penalised; 16 s off → rejected; accents ("Beyoncé") match.

### 13.5 Waveform + loudness (one decode pass)

```kotlin
data class AudioAnalysis(val waveform: ByteArray, val gainDb: Float)

fun analyseAudio(file: File, bars: Int = 96, targetDb: Float = -14f): AudioAnalysis {
    val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
    val index = (0 until extractor.trackCount).first {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    }
    val format = extractor.getTrackFormat(index)
    extractor.selectTrack(index)
    val durationUs = format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(format, null, null, 0)
    codec.start()
    val squares = DoubleArray(bars)
    val counts = LongArray(bars)
    var totalSquares = 0.0
    var totalCount = 0L
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var outputDone = false
    try {
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                if (info.size > 0) {
                    // 16-bit PCM is the default decoder output; check KEY_PCM_ENCODING and handle float if needed
                    val samples = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val bar = (info.presentationTimeUs * bars / durationUs).toInt().coerceIn(0, bars - 1)
                    while (samples.hasRemaining()) {
                        val v = samples.get() / 32768.0
                        squares[bar] += v * v
                        counts[bar]++
                        totalSquares += v * v
                        totalCount++
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
    } finally {
        codec.stop(); codec.release(); extractor.release()
    }
    val rms = DoubleArray(bars) { if (counts[it] == 0L) 0.0 else sqrt(squares[it] / counts[it]) }
    val peak = rms.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val waveform = ByteArray(bars) { ((rms[it] / peak).pow(0.7) * 255).toInt().toByte() }
    val overallDb = 20 * log10(sqrt(totalSquares / totalCount.coerceAtLeast(1)).coerceAtLeast(1e-9))
    val gain = (targetDb - overallDb).toFloat().coerceIn(-12f, 0f)
    return AudioAnalysis(waveform, gain)
}
```

Read bytes back with `toInt() and 0xFF`. Run on `Dispatchers.Default`. Expect about 1–2 s for a 5-minute
song on the Snapdragon 870. If it fails, store `null` (the waveform shows flat bars, gain is 0).

### 13.6 Required manifest entries

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<application android:name=".LumenApp" android:extractNativeLibs="true" ...>
    <activity android:name=".MainActivity" android:exported="true"
        android:launchMode="singleTask" android:windowSoftInputMode="adjustResize">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
        <intent-filter>
            <action android:name="android.intent.action.SEND" />
            <category android:name="android.intent.category.DEFAULT" />
            <data android:mimeType="text/plain" />
        </intent-filter>
    </activity>
    <service android:name=".playback.PlaybackService" android:exported="true"
        android:foregroundServiceType="mediaPlayback">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaSessionService" />
        </intent-filter>
    </service>
    <service android:name="androidx.work.impl.foreground.SystemForegroundService"
        android:foregroundServiceType="dataSync" tools:node="merge" />
    <provider android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.files" android:exported="false"
        android:grantUriPermissions="true">
        <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
    </provider>
</application>
```

### 13.7 Build config essentials (`app/build.gradle.kts`)

```kotlin
android {
    namespace = "com.lumen.player"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.lumen.player"
        minSdk = 30               // Android 11: VACUUM INTO, all-files access, the POCO F3's original OS
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")   // from keystore.properties (P12)
        }
    }
}
```

---

## 14. Testing on the phone (POCO F3, MIUI)

### 14.1 One-time phone setup (ask the user, once, in P0)

On the phone: **Settings → About phone → tap "MIUI version" / "OS version" 7 times** → Developer options:
1. **USB debugging:** ON
2. **Install via USB:** ON (MIUI requires a signed-in Mi account for this)
3. **USB debugging (Security settings):** ON. Without it, `adb shell input tap` is silently ignored.
4. Keep the phone unlocked during test sessions. Run `adb shell svc power stayon usb` to keep the screen on
   while it's plugged in.

Then `adb devices` must show the device as `device`. If it says `unauthorized`, ask the user to accept the
RSA prompt on the phone. MIUI may show an *Install via USB?* dialog for each install. If `adb install`
hangs or fails with `INSTALL_FAILED_USER_RESTRICTED`, ask the user to tap **Install**.

### 14.2 Commands (Git Bash; `ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`)

```bash
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell appops set --uid com.lumen.player MANAGE_EXTERNAL_STORAGE allow
"$ADB" shell pm grant com.lumen.player android.permission.POST_NOTIFICATIONS
"$ADB" shell dumpsys deviceidle whitelist +com.lumen.player
"$ADB" shell am start -n com.lumen.player/.MainActivity
"$ADB" shell am force-stop com.lumen.player

# screenshot: pull, never redirect stdout (PowerShell 5.1 corrupts binary redirects)
"$ADB" shell screencap -p /sdcard/shot.png && "$ADB" pull /sdcard/shot.png shots/p5-songs.png

# UI tree -> find a testTag (exposed as resource-id) -> tap the centre of its bounds
"$ADB" shell uiautomator dump /sdcard/ui.xml && "$ADB" pull /sdcard/ui.xml shots/ui.xml
"$ADB" shell input tap 540 1200
"$ADB" shell input swipe 540 1800 540 600 300           # scroll
"$ADB" shell input text "talk%stalk%snew%sgrass"         # %s = space
"$ADB" shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE     # also KEYCODE_MEDIA_NEXT / KEYCODE_HOME / KEYCODE_BACK

# share a Spotify link into the app
"$ADB" shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://open.spotify.com/track/51Lt2RN3W0u2xI0LiARCRm?si=test" \
  -n com.lumen.player/.MainActivity

# logs, files, media session, jank
"$ADB" logcat -c; "$ADB" logcat -d -v time Lumen:V AndroidRuntime:E *:S
"$ADB" shell ls -la /sdcard/Lumen /sdcard/Lumen/audio
"$ADB" shell dumpsys media_session | head -60
"$ADB" shell dumpsys gfxinfo com.lumen.player reset   # then interact, then:
"$ADB" shell dumpsys gfxinfo com.lumen.player | head -40
"$ADB" shell wm size; "$ADB" shell wm density
```

### 14.3 Rules for UI testing

- Use a single log tag `Lumen` for app logs.
- **Look at every screenshot** you take and compare it with §11–§12. Fix visual problems before moving on.
- `uiautomator dump` fails ("could not get idle state") while infinite animations run. Turn on
  **Reduce motion** in the app before dumping, or set `adb shell settings put global animator_duration_scale 0`
  (set it back to `1` afterwards).
- Prefer finding elements by `testTag` in the dump over hard-coded coordinates.
- Test **performance in a release build** (debug Compose builds are much slower). Target: no visible jank
  on Songs with 1,500 seeded songs, and < 5 % janky frames in `gfxinfo`.
- Lock-screen and Bluetooth/headset checks need the user: ask once in P4, with exact steps.

### 14.4 Automated tests

- JVM unit tests: LRC parser + active index, Spotify regex, `normalize`/`coreTitle`/`scoreCandidate`,
  artist splitting, `StatsCalculator` (streaks, sessions, archetypes, % changes), seeded tone.
- Instrumented on the phone (`gradlew connectedDebugAndroidTest`): DAO tests on an in-memory Room
  database (focus filter, playlist reorder, stats queries with fixed timestamps, soft delete).

---

## 15. Phases

Each phase ends with: build, install, device checks, screenshots in `shots/` (gitignored), a commit
(`P<n>: <summary>`) and a `PROGRESS.md` entry.

### P0 · Bootstrap
- `git init`. `.gitignore`: `build/`, `.gradle/`, `local.properties`, `keystore/`, `*.jks`,
  `keystore.properties`, `shots/`, `.idea/`, `captures/`, `.kotlin/`.
- `local.properties`: `sdk.dir=C\:\\Users\\ASUS\\AppData\\Local\\Android\\Sdk`
- Choose versions (§3.1). Gradle wrapper: download `https://services.gradle.org/distributions/gradle-<ver>-bin.zip`
  into a temp folder, unzip, run `<temp>/gradle-<ver>/bin/gradle wrapper --gradle-version <ver>` in the
  project, then use only `./gradlew`. Set `JAVA_HOME` to the Studio JBR (§2) for every Gradle call.
- A minimal Compose app ("Lumen" on ink) with Hilt wired up.
- Phone setup (§14.1).
- **DoD:** `./gradlew assembleDebug` succeeds; the app is installed and launched on the phone; the
  screenshot shows the text.

### P1 · Design system, shell, onboarding, storage
- Fonts, tokens, typography, shared components (§11), app shell with 4 placeholder tabs, mini player
  placeholder, onboarding (§12.2), `LumenPaths` + folder creation (§4.2/4.4), Settings skeleton with
  Reduce motion.
- **DoD:** the onboarding flow works on the phone; `/sdcard/Lumen/` has all subfolders + `.nomedia` +
  `README.txt`; a screenshot of each tab shows the correct fonts and colours.

### P2 · Data layer
- Room entities/DAOs (§5), repositories, DataStore settings, snapshot + restore (§4.3), debug seeders.
- **DoD:** instrumented DAO tests pass on the phone; `Lumen/data/lumen.db` appears 30 s after a seed;
  uninstall + reinstall → onboarding offers a restore and it works.

### P3 · Downloads
- Network clients (§6) with unit-tested parsing, youtubedl-android init/update (§7.5), pipeline (§7.2),
  worker + queue (§7.3), the Add screen (§12.10), share target + paste link (§7.7), NEEDS_CHOICE flow.
- **DoD:** download three songs from the Add tab (e.g. *Talk Talk – New Grass*, *Daft Punk – Get Lucky*
  (has a featured artist), *Queen – Bohemian Rhapsody*) and one via the share intent (§14.2). For each, check:
  the audio file plays (`adb pull` + size > 1 MB), cover + thumbnail, lyrics file (where LRCLIB has one),
  waveform/gain are not null, featured artists are separate rows, and the genre is set. Cancel works
  mid-audio (no leftover files). A duplicate shows "Already in your library". Logcat shows no
  "No supported JavaScript runtime" warning.

### P4 · Playback core
- `PlaybackService`, `PlayerConnection`, `QueueManager`, mini player, a basic expanded player,
  persistence + resumption, notification.
- **DoD:** playing from a temporary debug list works; it keeps playing after HOME and opening another app
  for 3 minutes; notification controls work; `KEYCODE_MEDIA_*` keyevents work; force-stop → relaunch
  restores the queue paused; `dumpsys media_session` shows the session. Ask the user to check the lock
  screen controls and a headset unplug (pauses).

### P5 · Library (All Songs, Artists, Albums, search, track menu)
- §12.5, §12.6, §12.8, likes, edit info, delete (soft), multi-select.
- **DoD:** with 1,500 seeded songs in a **release** build: Songs scrolls smoothly (gfxinfo < 5 % janky);
  every sort works; the fast-scroll rail works; deleting a song removes its files and keeps its play events;
  screenshots match the design.

### P6 · Playlists
- §12.7 complete, including the smart playlists, mosaic/custom covers, the picker and reordering.
- **DoD:** create, rename, add (from 3 entry points), reorder, remove + undo, custom cover, pin, delete;
  "Play" plays in custom order; the state survives an app restart and is in the snapshot.

### P7 · Artist Focus
- §10 complete.
- **DoD:** focus one artist → Songs/Albums/Home/search/playlists show only their songs (including songs where
  they're featured); Shuffle plays only them (check 10 skips); the "Play only X now?" snackbar works;
  focus with 2 artists works; the focus survives a restart; clearing it restores everything.

### P8 · Now Playing complete
- Waveform scrubber, glow + breathing, lyrics peek, the full lyrics screen (§13.1), the queue sheet with
  sections, sleep timer, normalisation, cover pager, haptics, cover-tone theming.
- **DoD:** synced lyrics stay in time (tap-to-seek lands on the right line); the waveform seek is
  accurate to ±1 s; the sleep timer fades and pauses; "Play next" ordering is correct with shuffle on;
  screenshots of player/lyrics/queue match the design.

### P9 · Listening tracker + Pulse + Recap
- `ListeningTracker` (§9.2), `StatsCalculator` with unit tests, the Pulse screen (§9.3, §12.9), charts,
  the Focus scope, recap stories + share image, the monthly notification worker.
- **DoD:** play 3 songs (one skipped at 10 s, one played fully, one paused then resumed) → events in the
  database match the definitions exactly; Pulse minutes increase live while playing (watch for 60 s);
  with 1 year of seeded plays every card renders without empty/NaN states; the recap image appears in
  `Lumen/exports/` and the share sheet opens.

### P10 · Home
- §12.3 complete (all sections, empty state, greeting, Pulse strip, recap card, "For this hour").
- **DoD:** sections appear/disappear correctly with an empty, small and seeded library; they respect Focus.

### P11 · Settings, backup, maintenance
- §12.11 complete: storage usage, `.nomedia` toggle, back up / restore, Check library, yt-dlp update, reset
  statistics, debug tools.
- **DoD:** manually deleting an audio file via adb → Check library finds it; update yt-dlp works;
  restore from `lumen-prev.db` works.

### P12 · Polish and release
- A release keystore (`keytool`, stored in `keystore/`, gitignored, passwords in `keystore.properties`),
  R8 rules verified (a download works in release!), a Baseline Profile if time allows, accessibility
  (content descriptions, 48 dp touch targets, font scale 1.3 doesn't break layouts), every empty/error
  state reviewed, final screenshots of all screens.
- **DoD:** the §16 checklist passes completely on the phone with the **release** APK installed.

---

## 16. Final acceptance checklist

- [ ] Fresh install → onboarding → `Lumen/` folder created with the right structure
- [ ] Search on Deezer → download → song appears in All Songs with cover, lyrics, waveform
- [ ] Share a Spotify track link from another app → downloads correctly
- [ ] A wrong/uncertain audio match can be replaced ("Choose another")
- [ ] All Songs: sort, fast-scroll, shuffle, multi-select, delete
- [ ] Playlists: create, add from 3 places, reorder, custom cover, pin, delete; Liked Songs; 3 smart playlists
- [ ] Artist Focus on 1 and on 2 artists: everything filtered, playback limited, clearly visible, persists
- [ ] Pulse: all cards for every range, live updates while playing, Focus scope, recap story + shared image
- [ ] Background playback 10+ min with the screen off; notification, lock screen and headset controls work
- [ ] Resume after force-stop; queue / shuffle / repeat restored
- [ ] Synced lyrics in time; lyrics peek; plain and "no lyrics" states
- [ ] Sleep timer; volume normalisation audible between a loud and a quiet song
- [ ] Uninstall → reinstall → restore brings back songs, playlists and stats
- [ ] Release build: smooth at 120 Hz with 1,500 seeded songs (seed data then removed)
- [ ] No crash in `adb logcat` across the whole checklist
