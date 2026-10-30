package com.lumen.player.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LyricsKind { NONE, PLAIN, SYNCED, INSTRUMENTAL }
enum class ArtistRole { MAIN, FEATURED }
enum class EndReason { COMPLETED, SKIPPED, STOPPED }
enum class PlayContext { ALL_SONGS, PLAYLIST, LIKED, ARTIST, ALBUM, SEARCH, HOME, QUEUE, RECAP, DEBUG_SEED }
enum class JobStatus { QUEUED, RUNNING, NEEDS_CHOICE, DONE, FAILED, CANCELLED }

@Entity(tableName = "tracks", indices = [Index("deezer_id"), Index("isrc"), Index("added_at")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "album_id") val albumId: String?,
    @ColumnInfo(name = "track_position") val trackPosition: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "audio_file") val audioFile: String,
    @ColumnInfo(name = "has_cover") val hasCover: Boolean,
    @ColumnInfo(name = "lyrics_kind") val lyricsKind: LyricsKind,
    @ColumnInfo(name = "lyrics_offset_ms") val lyricsOffsetMs: Long = 0,
    val genre: String?,
    @ColumnInfo(name = "release_date") val releaseDate: String?,
    val isrc: String?,
    @ColumnInfo(name = "deezer_id") val deezerId: Long?,
    @ColumnInfo(name = "spotify_id") val spotifyId: String?,
    @ColumnInfo(name = "youtube_id") val youtubeId: String?,
    val bpm: Float?,
    val explicit: Boolean,
    @ColumnInfo(name = "tone_hue") val toneHue: Float?,
    @ColumnInfo(name = "tone_sat") val toneSat: Float?,
    val waveform: ByteArray?,
    @ColumnInfo(name = "gain_db") val gainDb: Float?,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "liked_at") val likedAt: Long?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
) {
    override fun equals(other: Any?) = other is TrackEntity && other.id == id && other.title == title &&
        other.deletedAt == deletedAt && other.likedAt == likedAt && other.waveform.contentEquals(waveform)

    override fun hashCode() = id.hashCode()
}

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "deezer_id") val deezerId: Long?,
    @ColumnInfo(name = "has_image") val hasImage: Boolean,
    @ColumnInfo(name = "tone_hue") val toneHue: Float?,
    @ColumnInfo(name = "tone_sat") val toneSat: Float?,
    @ColumnInfo(name = "hide_songs", defaultValue = "0") val hideSongs: Boolean = false,
)

@Entity(tableName = "track_artists", primaryKeys = ["track_id", "artist_id"], indices = [Index("artist_id")])
data class TrackArtistEntity(
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "artist_id") val artistId: String,
    val position: Int,
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
    @ColumnInfo(name = "hide_songs", defaultValue = "0") val hideSongs: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    @ColumnInfo(name = "has_custom_cover") val hasCustomCover: Boolean = false,
    val pinned: Boolean = false,
    @ColumnInfo(name = "hide_songs", defaultValue = "0") val hideSongs: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlist_id", "track_id"], indices = [Index("track_id")])
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "play_events", indices = [Index("started_at"), Index("track_id")])
data class PlayEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
    @ColumnInfo(name = "track_duration_ms") val trackDurationMs: Long,
    @ColumnInfo(name = "end_reason") val endReason: EndReason?,
    @ColumnInfo(name = "context_type") val contextType: PlayContext,
    @ColumnInfo(name = "context_id") val contextId: String?,
    @ColumnInfo(name = "focus_active") val focusActive: Boolean,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "first_listen") val firstListen: Boolean,
)

@Entity(tableName = "download_jobs", indices = [Index("status")])
data class DownloadJobEntity(
    @PrimaryKey val id: String,
    val request: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    val status: JobStatus,
    val stage: String,
    val progress: Int,
    val error: String?,
    val candidates: String?,
    @ColumnInfo(name = "track_id") val trackId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
