package com.lumen.player.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT $TRACK_ROW $TRACK_FROM WHERE t.deleted_at IS NULL AND $FOCUS_FILTER")
    fun observeSongs(focusOff: Boolean, focusIds: List<String>): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_ROW $TRACK_FROM WHERE t.deleted_at IS NULL AND t.liked_at IS NOT NULL ORDER BY t.liked_at DESC")
    fun observeLiked(): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_ROW $TRACK_FROM WHERE t.id IN (:ids)")
    suspend fun rows(ids: List<String>): List<TrackRow>

    @Query("SELECT $TRACK_ROW $TRACK_FROM WHERE t.id = :id")
    fun observeRow(id: String): Flow<TrackRow?>

    @Query(
        "SELECT $TRACK_ROW $TRACK_FROM WHERE t.deleted_at IS NULL AND " +
            "EXISTS (SELECT 1 FROM track_artists x WHERE x.track_id = t.id AND x.artist_id = :artistId)"
    )
    fun observeByArtist(artistId: String): Flow<List<TrackRow>>

    @Query("SELECT $TRACK_ROW $TRACK_FROM WHERE t.deleted_at IS NULL AND t.album_id = :albumId ORDER BY t.track_position, t.title")
    fun observeByAlbum(albumId: String): Flow<List<TrackRow>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: String): TrackEntity?

    @Query(
        "SELECT * FROM tracks WHERE (:deezerId IS NOT NULL AND deezer_id = :deezerId) " +
            "OR (:isrc IS NOT NULL AND isrc = :isrc) ORDER BY deleted_at IS NOT NULL LIMIT 1"
    )
    suspend fun findSame(deezerId: Long?, isrc: String?): TrackEntity?

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET liked_at = :likedAt WHERE id = :id")
    suspend fun setLiked(id: String, likedAt: Long?)

    @Query("UPDATE tracks SET deleted_at = :now, liked_at = NULL WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE tracks SET title = :title, sort_title = :sortTitle, genre = :genre WHERE id = :id")
    suspend fun editInfo(id: String, title: String, sortTitle: String, genre: String?)

    @Query("UPDATE tracks SET lyrics_offset_ms = :offsetMs WHERE id = :id")
    suspend fun setLyricsOffset(id: String, offsetMs: Long)

    @Query("UPDATE tracks SET lyrics_kind = :kind WHERE id = :id")
    suspend fun setLyricsKind(id: String, kind: LyricsKind)

    @Query(
        "UPDATE tracks SET audio_file = :audioFile, youtube_id = :youtubeId, waveform = :waveform, " +
            "gain_db = :gainDb, file_size = :fileSize WHERE id = :id"
    )
    suspend fun replaceAudio(id: String, audioFile: String, youtubeId: String?, waveform: ByteArray?, gainDb: Float?, fileSize: Long)

    @Query("SELECT waveform FROM tracks WHERE id = :id")
    suspend fun waveform(id: String): ByteArray?

    @Query(
        "SELECT COUNT(*) AS count, COALESCE(SUM(duration_ms), 0) AS durationMs, COALESCE(SUM(file_size), 0) AS bytes " +
            "FROM tracks WHERE deleted_at IS NULL"
    )
    fun observeTotals(): Flow<LibraryTotals>

    @Query("SELECT * FROM tracks WHERE deleted_at IS NULL")
    suspend fun allActive(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE deleted_at IS NULL")
    suspend fun count(): Int

    @Query("SELECT added_at FROM tracks WHERE deleted_at IS NULL ORDER BY added_at")
    fun observeAddedTimes(): Flow<List<Long>>

    @Query("DELETE FROM tracks WHERE id LIKE 'seed%'")
    suspend fun deleteSeeded()
}

@Dao
interface TrackArtistDao {
    @Upsert
    suspend fun upsertAll(links: List<TrackArtistEntity>)

    @Query("DELETE FROM track_artists WHERE track_id = :trackId")
    suspend fun deleteForTrack(trackId: String)

    @Query(
        "SELECT ta.track_id AS trackId, ta.artist_id AS artistId, a.name AS name, ta.position AS position " +
            "FROM track_artists ta JOIN artists a ON a.id = ta.artist_id WHERE ta.track_id = :trackId ORDER BY ta.position"
    )
    suspend fun artistsOf(trackId: String): List<TrackArtistName>

    @Query(
        "SELECT ta.track_id AS trackId, ta.artist_id AS artistId, a.name AS name, ta.position AS position " +
            "FROM track_artists ta JOIN artists a ON a.id = ta.artist_id"
    )
    fun observeAll(): Flow<List<TrackArtistName>>

    @Query("DELETE FROM track_artists WHERE track_id LIKE 'seed%'")
    suspend fun deleteSeeded()
}

@Dao
interface ArtistDao {
    @Query(
        "SELECT a.id AS id, a.name AS name, a.sort_name AS sortName, a.has_image AS hasImage, a.tone_hue AS toneHue, " +
            "a.tone_sat AS toneSat, COUNT(t.id) AS trackCount FROM artists a " +
            "JOIN track_artists ta ON ta.artist_id = a.id JOIN tracks t ON t.id = ta.track_id AND t.deleted_at IS NULL " +
            "GROUP BY a.id ORDER BY a.sort_name"
    )
    fun observeAll(): Flow<List<ArtistRow>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observe(id: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun get(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<ArtistEntity>>

    @Upsert
    suspend fun upsert(artist: ArtistEntity)

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id LIKE 'seed%'")
    suspend fun deleteSeeded()
}

@Dao
interface AlbumDao {
    @Query(
        "SELECT al.id AS id, al.title AS title, al.artist_id AS artistId, a.name AS artistName, " +
            "al.release_date AS releaseDate, al.genre AS genre, " +
            "COALESCE(MIN(CASE WHEN t.id = al.cover_track_id THEN t.id END), MIN(CASE WHEN t.has_cover THEN t.id END)) AS coverTrackId, " +
            "COUNT(t.id) AS trackCount, COALESCE(SUM(t.duration_ms), 0) AS durationMs " +
            "FROM albums al LEFT JOIN artists a ON a.id = al.artist_id " +
            "JOIN tracks t ON t.album_id = al.id AND t.deleted_at IS NULL AND $FOCUS_FILTER " +
            "GROUP BY al.id ORDER BY al.title"
    )
    fun observeAll(focusOff: Boolean, focusIds: List<String>): Flow<List<AlbumRow>>

    @Query(
        "SELECT al.id AS id, al.title AS title, al.artist_id AS artistId, a.name AS artistName, " +
            "al.release_date AS releaseDate, al.genre AS genre, " +
            "COALESCE(MIN(CASE WHEN t.id = al.cover_track_id THEN t.id END), MIN(CASE WHEN t.has_cover THEN t.id END)) AS coverTrackId, " +
            "COUNT(t.id) AS trackCount, COALESCE(SUM(t.duration_ms), 0) AS durationMs " +
            "FROM albums al LEFT JOIN artists a ON a.id = al.artist_id " +
            "JOIN tracks t ON t.album_id = al.id AND t.deleted_at IS NULL WHERE al.id = :id GROUP BY al.id"
    )
    fun observe(id: String): Flow<AlbumRow?>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun get(id: String): AlbumEntity?

    @Upsert
    suspend fun upsert(album: AlbumEntity)

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id LIKE 'seed%'")
    suspend fun deleteSeeded()
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, p.description AS description, p.has_custom_cover AS hasCustomCover, " +
            "p.pinned AS pinned, p.created_at AS createdAt, p.updated_at AS updatedAt, " +
            "COUNT(t.id) AS trackCount, COALESCE(SUM(t.duration_ms), 0) AS durationMs FROM playlists p " +
            "LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id " +
            "LEFT JOIN tracks t ON t.id = pt.track_id AND t.deleted_at IS NULL " +
            "GROUP BY p.id ORDER BY p.pinned DESC, p.updated_at DESC"
    )
    fun observeAll(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT $TRACK_ROW $TRACK_FROM JOIN playlist_tracks pt ON pt.track_id = t.id WHERE pt.playlist_id = :playlistId AND t.deleted_at IS NULL ORDER BY pt.position")
    fun observeTracks(playlistId: String): Flow<List<TrackRow>>

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    fun observeEntries(playlistId: String): Flow<List<PlaylistTrackEntity>>

    @Query(
        "SELECT pt.playlist_id AS playlistId, pt.track_id AS trackId FROM playlist_tracks pt " +
            "JOIN tracks t ON t.id = pt.track_id WHERE t.deleted_at IS NULL AND t.has_cover ORDER BY pt.playlist_id, pt.position"
    )
    fun observeCoverTracks(): Flow<List<PlaylistCoverTrack>>

    @Query("SELECT playlist_id FROM playlist_tracks WHERE track_id = :trackId")
    fun observePlaylistsContaining(trackId: String): Flow<List<String>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, description = :description, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, description: String, now: Long)

    @Query("UPDATE playlists SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE playlists SET has_custom_cover = :value, updated_at = :now WHERE id = :id")
    suspend fun setCustomCover(id: String, value: Boolean, now: Long)

    @Query("UPDATE playlists SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :id")
    suspend fun deleteEntries(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addEntries(entries: List<PlaylistTrackEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEntries(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeEntry(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE track_id = :trackId")
    suspend fun removeTrackEverywhere(trackId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun setPosition(playlistId: String, trackId: String, position: Int)

    @Query("DELETE FROM playlist_tracks WHERE track_id LIKE 'seed%'")
    suspend fun deleteSeededEntries()

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Transaction
    suspend fun reorder(playlistId: String, orderedTrackIds: List<String>, now: Long) {
        orderedTrackIds.forEachIndexed { index, trackId -> setPosition(playlistId, trackId, index) }
        touch(playlistId, now)
    }

    @Transaction
    suspend fun deleteWithEntries(id: String) {
        deleteEntries(id)
        delete(id)
    }
}

@Dao
interface PlayEventDao {
    @Upsert
    suspend fun upsert(event: PlayEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PlayEventEntity>)

    @Query("DELETE FROM play_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM play_events WHERE track_id = :trackId AND id != :excludeId AND listened_ms >= 30000)")
    suspend fun hasQualifyingPlay(trackId: String, excludeId: String): Boolean

    @Query("SELECT * FROM play_events ORDER BY started_at")
    suspend fun all(): List<PlayEventEntity>

    @Query("SELECT COUNT(*) FROM play_events")
    suspend fun count(): Int

    @Query("DELETE FROM play_events")
    suspend fun deleteAll()

    @Query("DELETE FROM play_events WHERE context_type = 'DEBUG_SEED' OR track_id LIKE 'seed%'")
    suspend fun deleteSeeded()
}

@Dao
interface DownloadJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: DownloadJobEntity)

    @Upsert
    suspend fun upsert(job: DownloadJobEntity)

    @Query("SELECT * FROM download_jobs WHERE id = :id")
    suspend fun get(id: String): DownloadJobEntity?

    @Query("SELECT * FROM download_jobs ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_jobs WHERE status = 'QUEUED' ORDER BY created_at LIMIT 1")
    suspend fun nextQueued(): DownloadJobEntity?

    @Query("SELECT COUNT(*) FROM download_jobs WHERE status IN ('QUEUED', 'RUNNING')")
    suspend fun activeCount(): Int

    @Query("UPDATE download_jobs SET status = 'QUEUED', stage = 'queued', progress = 0 WHERE status = 'RUNNING'")
    suspend fun resetRunning()

    @Query("UPDATE download_jobs SET status = :status, stage = :stage, progress = :progress, error = :error, updated_at = :now WHERE id = :id")
    suspend fun setState(id: String, status: JobStatus, stage: String, progress: Int, error: String?, now: Long)

    @Query("UPDATE download_jobs SET stage = :stage, progress = :progress, updated_at = :now WHERE id = :id AND status = 'RUNNING'")
    suspend fun setProgress(id: String, stage: String, progress: Int, now: Long)

    @Query(
        "UPDATE download_jobs SET status = :status, stage = :stage, progress = :progress, error = :error, track_id = :trackId, " +
            "candidates = :candidates, updated_at = :now WHERE id = :id"
    )
    suspend fun finish(id: String, status: JobStatus, stage: String, progress: Int, error: String?, trackId: String?, candidates: String?, now: Long)

    @Query("UPDATE download_jobs SET title = :title, artist = :artist, cover_url = COALESCE(:coverUrl, cover_url) WHERE id = :id")
    suspend fun setInfo(id: String, title: String, artist: String, coverUrl: String?)

    @Query("SELECT deezer_id FROM tracks WHERE deleted_at IS NULL AND deezer_id IS NOT NULL")
    fun observeLibraryDeezerIds(): Flow<List<Long>>

    @Query("UPDATE download_jobs SET status = 'RUNNING', updated_at = :now WHERE id = :id AND status = 'QUEUED'")
    suspend fun claim(id: String, now: Long): Int

    @Query("DELETE FROM download_jobs WHERE status IN ('DONE', 'FAILED', 'CANCELLED')")
    suspend fun deleteFinished()
}
