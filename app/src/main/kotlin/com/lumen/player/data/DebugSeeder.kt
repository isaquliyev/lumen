package com.lumen.player.data

import androidx.room.withTransaction
import com.lumen.player.core.db.AlbumEntity
import com.lumen.player.core.db.ArtistEntity
import com.lumen.player.core.db.ArtistRole
import com.lumen.player.core.db.EndReason
import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.PlayEventEntity
import com.lumen.player.core.db.TrackArtistEntity
import com.lumen.player.core.db.TrackEntity
import com.lumen.player.core.util.sortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

// debug-only fake data: track/artist/album ids start with "seed", plays are tagged DEBUG_SEED
@Singleton
class DebugSeeder @Inject constructor(private val db: LumenDatabase) {

    private val syllables = listOf(
        "lu", "mi", "ra", "so", "ne", "va", "ko", "ta", "ri", "el", "an", "or", "is", "mo", "da", "fe", "ga", "ly",
        "ze", "po", "qu", "ba", "si", "um", "ar", "en", "ho", "wi", "xa", "ty",
    )
    private val genres = listOf("Rock", "Pop", "Electronic", "Jazz", "Hip-Hop", "Indie", "Folk", "Soul", "Classical", "Ambient")

    private fun word(random: Random, parts: Int) =
        (1..parts).joinToString("") { syllables[random.nextInt(syllables.size)] }.replaceFirstChar { it.uppercase() }

    private fun phrase(random: Random, words: Int) = (1..words).joinToString(" ") { word(random, 1 + random.nextInt(3)) }

    private fun seedId(prefix: String, n: Int) = "seed$prefix${n.toString().padStart(7, '0')}".take(12)

    suspend fun seedSongs(count: Int = 1500) = withContext(Dispatchers.IO) {
        val random = Random(42)
        val now = System.currentTimeMillis()
        val artists = (0 until 120).map { i ->
            val name = phrase(random, 1 + random.nextInt(2))
            ArtistEntity(seedId("a", i), name, sortKey(name), null, false, random.nextFloat() * 360, 0.4f + random.nextFloat() * 0.4f)
        }
        val albums = (0 until 300).map { i ->
            val year = 1970 + random.nextInt(56)
            AlbumEntity(seedId("l", i), phrase(random, 1 + random.nextInt(3)), artists[i % artists.size].id, "$year-01-01", genres[i % genres.size], seedId("t", i))
        }
        val tracks = mutableListOf<TrackEntity>()
        val links = mutableListOf<TrackArtistEntity>()
        repeat(count) { i ->
            val album = albums[i % albums.size]
            val id = seedId("t", i)
            val title = phrase(random, 1 + random.nextInt(4))
            val duration = 120_000L + random.nextLong(300_000)
            tracks += TrackEntity(
                id = id, title = title, sortTitle = sortKey(title), albumId = album.id, trackPosition = i / albums.size + 1,
                durationMs = duration, audioFile = "audio/$id.m4a", hasCover = false, lyricsKind = LyricsKind.NONE,
                genre = album.genre, releaseDate = album.releaseDate, isrc = null, deezerId = null, spotifyId = null,
                youtubeId = null, bpm = null, explicit = false, toneHue = random.nextFloat() * 360,
                toneSat = 0.4f + random.nextFloat() * 0.4f, waveform = ByteArray(96) { (60 + random.nextInt(196)).toByte() },
                gainDb = -random.nextFloat() * 6, fileSize = duration / 1000 * 16_000, addedAt = now - random.nextLong(400L * 86_400_000),
                likedAt = if (random.nextInt(10) == 0) now - random.nextLong(100L * 86_400_000) else null, deletedAt = null,
            )
            links += TrackArtistEntity(id, album.artistId, 0, ArtistRole.MAIN)
            if (random.nextInt(10) == 0) {
                val featured = artists[random.nextInt(artists.size)].id
                if (featured != album.artistId) links += TrackArtistEntity(id, featured, 1, ArtistRole.FEATURED)
            }
        }
        db.withTransaction {
            db.artistDao().upsertAll(artists)
            db.albumDao().upsertAll(albums)
            db.trackDao().upsertAll(tracks)
            db.trackArtistDao().upsertAll(links)
        }
        count
    }

    suspend fun seedPlays(days: Int = 365) = withContext(Dispatchers.IO) {
        val random = Random(7)
        val pool = db.trackDao().allActive().filter { it.id.startsWith("seed") }.ifEmpty { db.trackDao().allActive() }
        if (pool.isEmpty()) return@withContext 0
        val favourites = pool.shuffled(random).take(40)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val heard = mutableSetOf<String>()
        val events = mutableListOf<PlayEventEntity>()
        for (dayOffset in days downTo 1) {
            val day = today.minusDays(dayOffset.toLong())
            val weekend = day.dayOfWeek.value >= 6
            val plays = if (random.nextInt(8) == 0) 0 else random.nextInt(if (weekend) 30 else 18)
            if (plays == 0) continue
            val startHour = if (random.nextBoolean()) 19 + random.nextInt(4) else 7 + random.nextInt(10)
            var clock = day.atTime(startHour, random.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
            var session = UUID.randomUUID().toString()
            repeat(plays) {
                val track = if (random.nextInt(3) == 0) favourites[random.nextInt(favourites.size)] else pool[random.nextInt(pool.size)]
                val roll = random.nextInt(10)
                val listened = when {
                    roll < 7 -> track.durationMs
                    roll < 9 -> 5_000L + random.nextLong(24_000)
                    else -> track.durationMs / 2
                }
                val reason = when {
                    roll < 7 -> EndReason.COMPLETED
                    roll < 9 -> EndReason.SKIPPED
                    else -> EndReason.STOPPED
                }
                val first = listened >= 30_000 && heard.add(track.id)
                events += PlayEventEntity(
                    UUID.randomUUID().toString(), track.id, clock, clock + listened, listened, track.durationMs, reason,
                    PlayContext.DEBUG_SEED, null, false, session, first,
                )
                clock += listened + 2_000
                if (random.nextInt(12) == 0) {
                    clock += (20 + random.nextInt(120)) * 60_000L
                    session = UUID.randomUUID().toString()
                }
            }
        }
        db.withTransaction { events.chunked(500).forEach { db.playEventDao().insertAll(it) } }
        events.size
    }

    suspend fun removeSeeded() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.playEventDao().deleteSeeded()
            db.playlistDao().deleteSeededEntries()
            db.trackArtistDao().deleteSeeded()
            db.trackDao().deleteSeeded()
            db.albumDao().deleteSeeded()
            db.artistDao().deleteSeeded()
        }
    }
}
