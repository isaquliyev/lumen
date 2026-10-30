package com.lumen.player.data

import com.lumen.player.core.db.AlbumDao
import com.lumen.player.core.db.ArtistDao
import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.db.StatsDao
import com.lumen.player.core.db.TrackDao
import com.lumen.player.core.db.TrackRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// less listening than this in a period: Pulse shows a progress message instead of the stats
const val MIN_PULSE_MS = 15 * 60_000L

data class RankedTrack(val row: TrackRow, val plays: Int, val listenedMs: Long)
data class RankedArtist(val id: String, val name: String, val hasImage: Boolean, val listenedMs: Long, val plays: Int)
data class RankedAlbum(val id: String, val title: String, val artist: String?, val coverTrackId: String?, val listenedMs: Long)
data class Share(val name: String, val fraction: Float)
data class ActiveDay(val day: LocalDate, val listenedMs: Long)
data class FocusCard(val names: String, val listenedMs: Long, val plays: Int, val topSong: TrackRow?, val firstListen: Long?)

data class PulseData(
    val period: Period,
    val totalMs: Long = 0,
    val plays: Int = 0,
    val changePct: Int? = null,
    val topSongs: List<RankedTrack> = emptyList(),
    val topArtists: List<RankedArtist> = emptyList(),
    val topAlbums: List<RankedAlbum> = emptyList(),
    val genres: List<Share> = emptyList(),
    val hours: LongArray = LongArray(24),
    val weekdays: LongArray = LongArray(7),
    val days: Map<LocalDate, Long> = emptyMap(),
    val streaks: Streaks = Streaks(0, 0),
    val songsAdded: Int = 0,
    val firstListens: Int = 0,
    val newShare: Float = 0f,
    val skipRate: Float = 0f,
    val completionRate: Float = 0f,
    val sessions: SessionStats = SessionStats(0, 0, 0),
    val mostActiveDay: ActiveDay? = null,
    val repeatMoment: Pair<TrackRow, StatsCalculator.RepeatMoment>? = null,
    val personality: Personality? = null,
    val librarySongs: Int = 0,
    val libraryMs: Long = 0,
    val libraryBytes: Long = 0,
    val growth: List<Pair<String, Int>> = emptyList(),
    val focusCard: FocusCard? = null,
) {
    val enough get() = totalMs >= MIN_PULSE_MS
}

@Singleton
class PulseRepository @Inject constructor(
    private val db: LumenDatabase,
    private val stats: StatsDao,
    private val tracks: TrackDao,
    private val artists: ArtistDao,
    private val albums: AlbumDao,
    private val trackRepository: TrackRepository,
) {
    // re-emits whenever a play event or a track changes, so Pulse updates live
    fun changes(): Flow<Set<String>> = db.invalidationTracker.createFlow("play_events", "tracks", "track_artists", emitInitialState = true)

    suspend fun build(kind: RangeKind, offset: Int, focus: Focus, scoped: Boolean, zone: ZoneId = ZoneId.systemDefault()): PulseData =
        withContext(Dispatchers.IO) {
            val off = !scoped || focus.off
            val ids = if (off) emptyList() else focus.idList
            val today = LocalDate.now(zone)
            val firstEvent = stats.firstEventTime().first()?.let { StatsCalculator.day(it, zone) }
            val period = StatsCalculator.period(kind, offset, today, zone, firstEvent = firstEvent)
            val from = period.from
            val to = period.to
            val total = stats.totalListened(from, to, off, ids).first()
            // the current period is compared with the same stretch of the previous one ("so far")
            val previous = StatsCalculator.previous(kind, offset, today, zone)?.let { prev ->
                val end = if (offset == 0) minOf(prev.to, prev.from + (System.currentTimeMillis() - from)) else prev.to
                stats.totalListened(prev.from, end, off, ids).first()
            }
            val events = stats.events(from, to, off, ids).first()
            val topTracks = stats.topTracksByTime(from, to, 50, off, ids).first()
            val rows = trackRepository.rows(topTracks.map { it.trackId }).associateBy { it.id }
            val topArtistRows = stats.topArtists(from, to, 5, off, ids).first()
            val artistNames = artists.observeByIds(topArtistRows.map { it.artistId }).first().associateBy { it.id }
            val topAlbumRows = stats.topAlbums(from, to, 5, off, ids).first()
            val genres = stats.topGenres(from, to, 6, off, ids).first()
            val genreTotal = genres.sumOf { it.listenedMs }.coerceAtLeast(1)
            val hours = LongArray(24).also { array -> stats.hours(from, to, off, ids).first().forEach { array[it.bucket.toInt()] = it.listenedMs } }
            // SQLite %w: 0 = Sunday; stored Monday-first
            val weekdays = LongArray(7).also { array ->
                stats.weekdays(from, to, off, ids).first().forEach { array[(it.bucket.toInt() + 6) % 7] = it.listenedMs }
            }
            val days = stats.days(from, to, off, ids).first().associate { LocalDate.parse(it.bucket) to it.listenedMs }
            val allDays = stats.days(0, Long.MAX_VALUE, off, ids).first()
            val streakDays = allDays.filter { it.listenedMs > 0 }.map { LocalDate.parse(it.bucket) }.toSet()
            val mostActive = allDays.maxByOrNull { it.listenedMs }?.let { ActiveDay(LocalDate.parse(it.bucket), it.listenedMs) }
            val artistMs = topArtistRows.associate { it.artistId to it.listenedMs }
            val sessions = StatsCalculator.sessions(events)
            val moment = StatsCalculator.onRepeatMoment(events, zone)
            val momentRow = moment?.let { trackRepository.rows(listOf(it.trackId)).firstOrNull() }
            val totals = tracks.observeTotals().first()
            val added = tracks.observeAddedTimes().first()
            val monthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
            var running = 0
            val growth = added.groupBy { StatsCalculator.day(it, zone).withDayOfMonth(1) }.toSortedMap().map { (month, list) ->
                running += list.size
                month.format(monthFormat) to running
            }.takeLast(12)

            PulseData(
                period = period,
                totalMs = total,
                plays = events.count(StatsCalculator::isPlay),
                changePct = previous?.let { StatsCalculator.change(total, it) },
                topSongs = topTracks.mapNotNull { t -> rows[t.trackId]?.let { RankedTrack(it, t.plays, t.listenedMs) } },
                topArtists = topArtistRows.mapNotNull { a ->
                    artistNames[a.artistId]?.let { RankedArtist(it.id, it.name, it.hasImage, a.listenedMs, a.plays) }
                },
                topAlbums = topAlbumRows.mapNotNull { a ->
                    val album = albums.get(a.albumId) ?: return@mapNotNull null
                    RankedAlbum(album.id, album.title, artists.get(album.artistId)?.name, album.coverTrackId, a.listenedMs)
                },
                genres = genres.map { Share(it.genre, it.listenedMs.toFloat() / genreTotal) },
                hours = hours,
                weekdays = weekdays,
                days = days,
                streaks = StatsCalculator.streaks(streakDays, today),
                songsAdded = added.count { it in from until to },
                firstListens = events.count { it.firstListen },
                newShare = StatsCalculator.newShare(events),
                skipRate = StatsCalculator.skipRate(events),
                completionRate = StatsCalculator.completionRate(events),
                sessions = sessions,
                mostActiveDay = mostActive,
                repeatMoment = if (moment != null && momentRow != null) momentRow to moment else null,
                personality = if (total > 0) StatsCalculator.personality(
                    events, hours.withIndex().associate { it.index to it.value },
                    DayOfWeek.entries.associateWith { weekdays[it.value - 1] }, artistMs, sessions.averageMs,
                ) else null,
                librarySongs = totals.count,
                libraryMs = totals.durationMs,
                libraryBytes = totals.bytes,
                growth = growth,
                focusCard = if (focus.active) focusCard(focus, from, to) else null,
            )
        }

    private suspend fun focusCard(focus: Focus, from: Long, to: Long): FocusCard {
        val names = artists.observeByIds(focus.idList).first().joinToString(" & ") { it.name }
        val top = stats.topTracksByTime(from, to, 1, false, focus.idList).first().firstOrNull()
        return FocusCard(
            names = names,
            listenedMs = stats.totalListened(from, to, false, focus.idList).first(),
            plays = stats.events(from, to, false, focus.idList).first().count(StatsCalculator::isPlay),
            topSong = top?.let { trackRepository.rows(listOf(it.trackId)).firstOrNull() },
            firstListen = focus.idList.mapNotNull { stats.firstListenOfArtist(it).first() }.minOrNull(),
        )
    }
}
