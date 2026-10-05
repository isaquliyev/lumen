package com.lumen.player.core.db

import androidx.compose.runtime.Immutable

// columns shared by every track list query (no waveform, so list models stay light)
const val TRACK_ROW = """
    t.id AS id, t.title AS title, t.sort_title AS sortTitle, t.album_id AS albumId, al.title AS albumTitle,
    (SELECT a.name FROM track_artists ta JOIN artists a ON a.id = ta.artist_id
        WHERE ta.track_id = t.id AND ta.position = 0)
      || COALESCE(', ' || (SELECT GROUP_CONCAT(a.name, ', ') FROM track_artists ta JOIN artists a ON a.id = ta.artist_id
        WHERE ta.track_id = t.id AND ta.position > 0), '') AS artistLine,
    (SELECT a.sort_name FROM track_artists ta JOIN artists a ON a.id = ta.artist_id
        WHERE ta.track_id = t.id AND ta.position = 0) AS artistSort,
    (SELECT ta.artist_id FROM track_artists ta WHERE ta.track_id = t.id AND ta.position = 0) AS primaryArtistId,
    t.duration_ms AS durationMs, t.has_cover AS hasCover, t.added_at AS addedAt, t.liked_at AS likedAt,
    t.tone_hue AS toneHue, t.tone_sat AS toneSat, t.lyrics_kind AS lyricsKind, t.lyrics_offset_ms AS lyricsOffsetMs,
    t.track_position AS trackPosition, t.release_date AS releaseDate, t.genre AS genre, t.file_size AS fileSize,
    t.audio_file AS audioFile, t.gain_db AS gainDb, t.deleted_at AS deletedAt
"""

const val TRACK_FROM = " FROM tracks t LEFT JOIN albums al ON al.id = t.album_id "

const val FOCUS_FILTER = """
    (:focusOff OR EXISTS (SELECT 1 FROM track_artists fa WHERE fa.track_id = t.id AND fa.artist_id IN (:focusIds)))
"""

@Immutable
data class TrackRow(
    val id: String,
    val title: String,
    val sortTitle: String,
    val albumId: String?,
    val albumTitle: String?,
    val artistLine: String?,
    val artistSort: String?,
    val primaryArtistId: String?,
    val durationMs: Long,
    val hasCover: Boolean,
    val addedAt: Long,
    val likedAt: Long?,
    val toneHue: Float?,
    val toneSat: Float?,
    val lyricsKind: LyricsKind,
    val lyricsOffsetMs: Long,
    val trackPosition: Int?,
    val releaseDate: String?,
    val genre: String?,
    val fileSize: Long,
    val audioFile: String,
    val gainDb: Float?,
    val deletedAt: Long?,
)

data class LibraryTotals(val count: Int, val durationMs: Long, val bytes: Long)

@Immutable
data class ArtistRow(
    val id: String,
    val name: String,
    val sortName: String,
    val hasImage: Boolean,
    val toneHue: Float?,
    val toneSat: Float?,
    val trackCount: Int,
)

@Immutable
data class AlbumRow(
    val id: String,
    val title: String,
    val artistId: String,
    val artistName: String?,
    val releaseDate: String?,
    val genre: String?,
    val coverTrackId: String?,
    val trackCount: Int,
    val durationMs: Long,
)

@Immutable
data class PlaylistRow(
    val id: String,
    val name: String,
    val description: String,
    val hasCustomCover: Boolean,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
    val durationMs: Long,
)

data class PlaylistCoverTrack(val playlistId: String, val trackId: String)

data class TrackArtistName(val trackId: String, val artistId: String, val name: String, val position: Int)

data class TrackPlays(val trackId: String, val plays: Int, val listenedMs: Long)

data class ArtistListening(val artistId: String, val listenedMs: Long, val plays: Int)

data class AlbumListening(val albumId: String, val listenedMs: Long, val plays: Int)

data class GenreListening(val genre: String, val listenedMs: Long)

data class Bucket(val bucket: String, val listenedMs: Long)

data class EventLite(
    val trackId: String,
    val startedAt: Long,
    val endedAt: Long,
    val listenedMs: Long,
    val trackDurationMs: Long,
    val endReason: EndReason?,
    val firstListen: Boolean,
    val sessionId: String,
)
