package com.lumen.player.data

import androidx.room.withTransaction
import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.util.sortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrackRepository @Inject constructor(
    private val db: LumenDatabase,
    private val paths: LumenPaths,
    private val focusRepository: FocusRepository,
) {
    private val tracks = db.trackDao()

    // library songs without the ones kept out of Library; Artist Focus shows only the focused artists,
    // hidden songs included, because choosing an artist is asking for their songs
    val songs: Flow<List<TrackRow>> = focusRepository.focus.flatMapLatest { tracks.observeSongs(it.off, it.idList, it.active) }

    // like songs, but with the hidden playlist songs too (search, playlists)
    val songsWithHidden: Flow<List<TrackRow>> = focusRepository.focus.flatMapLatest { tracks.observeSongs(it.off, it.idList, true) }

    val allSongs: Flow<List<TrackRow>> = tracks.observeSongs(true, emptyList(), true)

    fun observe(id: String) = tracks.observeRow(id)

    suspend fun rows(ids: List<String>): List<TrackRow> {
        val byId = ids.chunked(500).flatMap { tracks.rows(it) }.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun setLiked(id: String, liked: Boolean) = tracks.setLiked(id, if (liked) System.currentTimeMillis() else null)

    suspend fun editInfo(id: String, title: String, genre: String?) =
        tracks.editInfo(id, title.trim(), sortKey(title), genre?.trim()?.ifEmpty { null })

    suspend fun setLyricsOffset(id: String, offsetMs: Long) = tracks.setLyricsOffset(id, offsetMs)

    suspend fun setLyricsKind(id: String, kind: LyricsKind) = tracks.setLyricsKind(id, kind)

    // soft delete: files and playlist entries go, play events stay so Pulse history is kept
    suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            val track = tracks.get(id) ?: return@forEach
            paths.resolve(track.audioFile).delete()
            paths.cover(id).delete()
            paths.coverThumb(id).delete()
            paths.syncedLyrics(id).delete()
            paths.plainLyrics(id).delete()
            db.withTransaction {
                db.playlistDao().removeTrackEverywhere(id)
                tracks.softDelete(id, now)
            }
        }
    }
}
