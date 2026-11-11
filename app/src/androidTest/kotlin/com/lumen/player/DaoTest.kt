package com.lumen.player

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumen.player.core.db.AlbumEntity
import com.lumen.player.core.db.ArtistEntity
import com.lumen.player.core.db.ArtistRole
import com.lumen.player.core.db.EndReason
import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.PlayEventEntity
import com.lumen.player.core.db.PlaylistEntity
import com.lumen.player.core.db.PlaylistTrackEntity
import com.lumen.player.core.db.TrackArtistEntity
import com.lumen.player.core.db.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: LumenDatabase

    private fun track(id: String, title: String, albumId: String? = "al1", deezerId: Long? = null) = TrackEntity(
        id = id, title = title, sortTitle = title.lowercase(), albumId = albumId, trackPosition = 1, durationMs = 200_000,
        audioFile = "audio/$id.m4a", hasCover = false, lyricsKind = LyricsKind.NONE, genre = "Rock", releaseDate = null,
        isrc = null, deezerId = deezerId, spotifyId = null, youtubeId = null, bpm = null, explicit = false, toneHue = null,
        toneSat = null, waveform = null, gainDb = null, fileSize = 1000, addedAt = 1, likedAt = null, deletedAt = null,
    )

    private fun event(id: String, trackId: String, startedAt: Long, listenedMs: Long) = PlayEventEntity(
        id, trackId, startedAt, startedAt + listenedMs, listenedMs, 200_000, EndReason.COMPLETED, PlayContext.ALL_SONGS,
        null, false, "s1", false,
    )

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LumenDatabase::class.java).allowMainThreadQueries().build()
        db.artistDao().upsertAll(
            listOf(
                ArtistEntity("artA", "Artist A", "artist a", null, false, null, null),
                ArtistEntity("artB", "Artist B", "artist b", null, false, null, null),
            )
        )
        db.albumDao().upsert(AlbumEntity("al1", "Album", "artA", null, null, null))
        db.trackDao().upsertAll(listOf(track("t1", "One", deezerId = 11), track("t2", "Two"), track("t3", "Three")))
        db.trackArtistDao().upsertAll(
            listOf(
                TrackArtistEntity("t1", "artA", 0, ArtistRole.MAIN),
                TrackArtistEntity("t2", "artB", 0, ArtistRole.MAIN),
                TrackArtistEntity("t2", "artA", 1, ArtistRole.FEATURED),
                TrackArtistEntity("t3", "artB", 0, ArtistRole.MAIN),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun focusFilterIncludesFeaturedCredits() = runTest {
        val all = db.trackDao().observeSongs(true, emptyList(), true).first()
        assertEquals(3, all.size)
        val focused = db.trackDao().observeSongs(false, listOf("artA"), true).first().map { it.id }.toSet()
        assertEquals(setOf("t1", "t2"), focused)
        val two = db.trackDao().observeSongs(false, listOf("artA", "artB"), true).first()
        assertEquals(3, two.size)
        assertEquals("Artist B, Artist A", all.first { it.id == "t2" }.artistLine)
    }

    @Test
    fun playlistReorderAndDuplicates() = runTest {
        val dao = db.playlistDao()
        dao.insert(PlaylistEntity("p1", "Mix", createdAt = 1, updatedAt = 1))
        val added = dao.addEntries(listOf("t1", "t2", "t3").mapIndexed { i, id -> PlaylistTrackEntity("p1", id, i, 1) })
        assertEquals(3, added.count { it != -1L })
        val duplicate = dao.addEntries(listOf(PlaylistTrackEntity("p1", "t1", 3, 2)))
        assertEquals(-1L, duplicate.single())
        dao.reorder("p1", listOf("t3", "t1", "t2"), 5)
        assertEquals(listOf("t3", "t1", "t2"), dao.observeTracks("p1").first().map { it.id })
        assertEquals(5L, dao.get("p1")!!.updatedAt)
    }

    @Test
    fun statsQueriesWithFixedTimestamps() = runTest {
        val base = 1_700_000_000_000L
        db.playEventDao().insertAll(
            listOf(
                event("e1", "t1", base, 40_000),
                event("e2", "t1", base + 100_000, 10_000),
                event("e3", "t2", base + 200_000, 60_000),
                event("e4", "t3", base + 10_000_000_000, 90_000),
            )
        )
        val stats = db.statsDao()
        val to = base + 1_000_000
        assertEquals(110_000L, stats.totalListened(base, to, true, emptyList()).first())
        val top = stats.topTracks(base, to, 5, true, emptyList()).first()
        assertEquals(listOf("t2", "t1"), top.map { it.trackId })
        assertEquals(1, top.first { it.trackId == "t1" }.plays)
        val artists = stats.topArtists(base, to, 5, true, emptyList()).first()
        assertEquals("artA", artists.first().artistId)
        assertEquals(110_000L, artists.first().listenedMs)
        val focused = stats.totalListened(base, to, false, listOf("artB")).first()
        assertEquals(60_000L, focused)
        assertEquals(110_000L, stats.hours(base, to, true, emptyList()).first().sumOf { it.listenedMs })
    }

    @Test
    fun softDeleteKeepsPlayEventsAndAllowsReuse() = runTest {
        db.playEventDao().upsert(event("e1", "t1", 1000, 50_000))
        db.trackDao().softDelete("t1", 2000)
        val ids = db.trackDao().observeSongs(true, emptyList(), true).first().map { it.id }
        assertTrue("t1" !in ids)
        assertEquals(1, db.playEventDao().count())
        val same = db.trackDao().findSame(11, null)
        assertNotNull(same)
        assertEquals("t1", same!!.id)
    }
}
