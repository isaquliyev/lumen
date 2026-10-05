package com.lumen.player.core.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

const val EVENT_RANGE = " pe.started_at >= :from AND pe.started_at < :to "

const val EVENT_FOCUS = """
    (:focusOff OR pe.track_id IN (SELECT track_id FROM track_artists WHERE artist_id IN (:focusIds)))
"""

@Dao
interface StatsDao {
    @Query("SELECT COALESCE(SUM(pe.listened_ms), 0) FROM play_events pe WHERE $EVENT_RANGE AND $EVENT_FOCUS")
    fun totalListened(from: Long, to: Long, focusOff: Boolean, focusIds: List<String>): Flow<Long>

    @Query(
        "SELECT pe.track_id AS trackId, SUM(pe.listened_ms >= 30000) AS plays, SUM(pe.listened_ms) AS listenedMs " +
            "FROM play_events pe WHERE $EVENT_RANGE AND $EVENT_FOCUS GROUP BY pe.track_id HAVING plays > 0 " +
            "ORDER BY plays DESC, listenedMs DESC LIMIT :limit"
    )
    fun topTracks(from: Long, to: Long, limit: Int, focusOff: Boolean, focusIds: List<String>): Flow<List<TrackPlays>>

    @Query(
        "SELECT ta.artist_id AS artistId, SUM(pe.listened_ms) AS listenedMs, SUM(pe.listened_ms >= 30000) AS plays " +
            "FROM play_events pe JOIN track_artists ta ON ta.track_id = pe.track_id " +
            "WHERE $EVENT_RANGE AND $EVENT_FOCUS GROUP BY ta.artist_id ORDER BY listenedMs DESC LIMIT :limit"
    )
    fun topArtists(from: Long, to: Long, limit: Int, focusOff: Boolean, focusIds: List<String>): Flow<List<ArtistListening>>

    @Query(
        "SELECT t.album_id AS albumId, SUM(pe.listened_ms) AS listenedMs, SUM(pe.listened_ms >= 30000) AS plays " +
            "FROM play_events pe JOIN tracks t ON t.id = pe.track_id " +
            "WHERE t.album_id IS NOT NULL AND $EVENT_RANGE AND $EVENT_FOCUS GROUP BY t.album_id ORDER BY listenedMs DESC LIMIT :limit"
    )
    fun topAlbums(from: Long, to: Long, limit: Int, focusOff: Boolean, focusIds: List<String>): Flow<List<AlbumListening>>

    @Query(
        "SELECT t.genre AS genre, SUM(pe.listened_ms) AS listenedMs FROM play_events pe JOIN tracks t ON t.id = pe.track_id " +
            "WHERE t.genre IS NOT NULL AND $EVENT_RANGE AND $EVENT_FOCUS GROUP BY t.genre ORDER BY listenedMs DESC LIMIT :limit"
    )
    fun topGenres(from: Long, to: Long, limit: Int, focusOff: Boolean, focusIds: List<String>): Flow<List<GenreListening>>

    @Query(
        "SELECT strftime('%H', pe.started_at / 1000, 'unixepoch', 'localtime') AS bucket, SUM(pe.listened_ms) AS listenedMs " +
            "FROM play_events pe WHERE $EVENT_RANGE AND $EVENT_FOCUS GROUP BY bucket"
    )
    fun hours(from: Long, to: Long, focusOff: Boolean, focusIds: List<String>): Flow<List<Bucket>>

    @Query(
        "SELECT strftime('%w', pe.started_at / 1000, 'unixepoch', 'localtime') AS bucket, SUM(pe.listened_ms) AS listenedMs " +
            "FROM play_events pe WHERE $EVENT_RANGE AND $EVENT_FOCUS GROUP BY bucket"
    )
    fun weekdays(from: Long, to: Long, focusOff: Boolean, focusIds: List<String>): Flow<List<Bucket>>

    @Query(
        "SELECT strftime('%Y-%m-%d', pe.started_at / 1000, 'unixepoch', 'localtime') AS bucket, SUM(pe.listened_ms) AS listenedMs " +
            "FROM play_events pe WHERE $EVENT_RANGE AND $EVENT_FOCUS GROUP BY bucket ORDER BY bucket"
    )
    fun days(from: Long, to: Long, focusOff: Boolean, focusIds: List<String>): Flow<List<Bucket>>

    @Query(
        "SELECT pe.track_id AS trackId, pe.started_at AS startedAt, pe.ended_at AS endedAt, pe.listened_ms AS listenedMs, " +
            "pe.track_duration_ms AS trackDurationMs, pe.end_reason AS endReason, pe.first_listen AS firstListen, " +
            "pe.session_id AS sessionId FROM play_events pe WHERE $EVENT_RANGE AND $EVENT_FOCUS ORDER BY pe.started_at"
    )
    fun events(from: Long, to: Long, focusOff: Boolean, focusIds: List<String>): Flow<List<EventLite>>

    @Query(
        "SELECT track_id AS trackId, SUM(listened_ms >= 30000) AS plays, SUM(listened_ms) AS listenedMs " +
            "FROM play_events GROUP BY track_id"
    )
    fun lifetimePlays(): Flow<List<TrackPlays>>

    @Query(
        "SELECT ta.artist_id AS artistId, SUM(pe.listened_ms) AS listenedMs, SUM(pe.listened_ms >= 30000) AS plays " +
            "FROM play_events pe JOIN track_artists ta ON ta.track_id = pe.track_id GROUP BY ta.artist_id"
    )
    fun lifetimeArtists(): Flow<List<ArtistListening>>

    @Query("SELECT MIN(pe.started_at) FROM play_events pe JOIN track_artists ta ON ta.track_id = pe.track_id WHERE ta.artist_id = :artistId")
    fun firstListenOfArtist(artistId: String): Flow<Long?>

    @Query("SELECT MIN(started_at) FROM play_events")
    fun firstEventTime(): Flow<Long?>
}
