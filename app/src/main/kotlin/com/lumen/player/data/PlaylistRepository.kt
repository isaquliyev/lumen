package com.lumen.player.data

import com.lumen.player.core.db.PlaylistDao
import com.lumen.player.core.db.PlaylistEntity
import com.lumen.player.core.db.PlaylistTrackEntity
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.util.newId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlists: PlaylistDao,
    private val paths: LumenPaths,
) {
    val all = playlists.observeAll()

    fun observe(id: String) = playlists.observe(id)
    fun observeTracks(id: String) = playlists.observeTracks(id)
    fun observeEntries(id: String) = playlists.observeEntries(id)
    fun observeContaining(trackId: String) = playlists.observePlaylistsContaining(trackId)
    val coverTracks = playlists.observeCoverTracks()

    suspend fun nextDefaultName(): String {
        val names = all.first().map { it.name }.toSet()
        var n = names.size + 1
        while ("My playlist #$n" in names) n++
        return "My playlist #$n"
    }

    suspend fun create(name: String, description: String = ""): String {
        val now = System.currentTimeMillis()
        val id = newId()
        playlists.insert(PlaylistEntity(id, name.trim().ifEmpty { "My playlist" }, description, createdAt = now, updatedAt = now))
        return id
    }

    suspend fun rename(id: String, name: String, description: String) =
        playlists.rename(id, name.trim().ifEmpty { "My playlist" }, description.trim(), System.currentTimeMillis())

    suspend fun setPinned(id: String, pinned: Boolean) = playlists.setPinned(id, pinned)

    suspend fun delete(id: String) {
        paths.playlistCover(id).delete()
        playlists.deleteWithEntries(id)
    }

    // returns how many songs were really added (songs already in the playlist are skipped)
    suspend fun add(playlistId: String, trackIds: List<String>): Int {
        val now = System.currentTimeMillis()
        var position = playlists.maxPosition(playlistId) + 1
        val entries = trackIds.distinct().map { PlaylistTrackEntity(playlistId, it, position++, now) }
        val added = playlists.addEntries(entries).count { it != -1L }
        if (added > 0) playlists.touch(playlistId, now)
        return added
    }

    suspend fun remove(playlistId: String, trackId: String) {
        playlists.removeEntry(playlistId, trackId)
        playlists.touch(playlistId, System.currentTimeMillis())
    }

    suspend fun restoreEntry(entry: PlaylistTrackEntity) = playlists.putEntries(listOf(entry))

    suspend fun reorder(playlistId: String, orderedTrackIds: List<String>) =
        playlists.reorder(playlistId, orderedTrackIds, System.currentTimeMillis())

    suspend fun setCustomCover(id: String, value: Boolean) = playlists.setCustomCover(id, value, System.currentTimeMillis())
}
