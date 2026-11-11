package com.lumen.player

import com.lumen.player.core.util.AudioCandidate
import com.lumen.player.core.util.activeLineIndex
import com.lumen.player.core.util.bestCandidate
import com.lumen.player.core.util.coreTitle
import com.lumen.player.core.util.normalize
import com.lumen.player.core.util.parseLyrics
import com.lumen.player.core.util.scoreCandidate
import com.lumen.player.core.util.seededTone
import com.lumen.player.core.util.sortKey
import com.lumen.player.core.util.splitCredits
import com.lumen.player.download.LinkKind
import com.lumen.player.download.classifyLink
import com.lumen.player.download.firstUrl
import com.lumen.player.download.youtubeVideoId
import com.lumen.player.download.sources.spotifyArtistFromPage
import com.lumen.player.download.sources.spotifyTrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {

    private val lrc = """
        [00:05.20]first test line
        [00:09.75][00:40.10]repeated chorus line
        [00:15.00]
        [00:21.333]third line with three-digit fraction
    """.trimIndent()

    @Test
    fun lrcParsesTimesAndRepeats() {
        val parsed = parseLyrics(lrc)
        assertTrue(parsed.synced)
        assertEquals(LyricLine(0, ""), parsed.lines.first())
        assertEquals(listOf(0L, 5_200L, 9_750L, 15_000L, 21_333L, 40_100L), parsed.lines.map { it.timeMs })
        assertEquals("repeated chorus line", parsed.lines.last().text)
    }

    @Test
    fun lrcActiveLineUsesLookahead() {
        val lines = parseLyrics(lrc).lines
        assertEquals(0, activeLineIndex(lines, 1_000))
        assertEquals(1, activeLineIndex(lines, 5_100))
        assertEquals(2, activeLineIndex(lines, 9_700))
        assertEquals(5, activeLineIndex(lines, 60_000))
        assertEquals(-1, activeLineIndex(emptyList(), 1_000))
    }

    @Test
    fun plainLyricsFallBack() {
        val parsed = parseLyrics("just a line\nanother line\n[ar:someone]")
        assertFalse(parsed.synced)
        assertEquals(listOf("just a line", "another line"), parsed.lines.map { it.text })
    }

    @Test
    fun spotifyLinks() {
        val id = "51Lt2RN3W0u2xI0LiARCRm"
        assertEquals(id, spotifyTrackId("https://open.spotify.com/track/$id"))
        assertEquals(id, spotifyTrackId("https://open.spotify.com/track/$id?si=abc&nd=1"))
        assertEquals(id, spotifyTrackId("https://open.spotify.com/intl-de/track/$id"))
        assertNull(spotifyTrackId("https://open.spotify.com/album/$id"))
        assertNull(spotifyTrackId("https://open.spotify.com/track/abc123"))
        assertEquals(LinkKind.SPOTIFY_ALBUM, classifyLink("https://open.spotify.com/album/$id"))
        assertEquals(LinkKind.YOUTUBE, classifyLink("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(LinkKind.YOUTUBE, classifyLink("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(classifyLink("https://example.com/song"))
        assertEquals("https://youtu.be/x", firstUrl("listen to this https://youtu.be/x."))
    }

    @Test
    fun youtubeVideoIds() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, youtubeVideoId("https://youtu.be/$id?si=abc123"))
        assertEquals(id, youtubeVideoId("https://www.youtube.com/watch?v=$id&t=42s"))
        assertEquals(id, youtubeVideoId("https://m.youtube.com/watch?feature=share&v=$id"))
        assertEquals(id, youtubeVideoId("https://music.youtube.com/watch?v=$id&list=RD"))
        assertEquals(id, youtubeVideoId("https://youtube.com/shorts/$id?feature=share"))
        assertEquals(id, youtubeVideoId("  check https://www.youtube.com/embed/$id  "))
        assertEquals(id, youtubeVideoId("https://www.youtube.com/live/$id"))
        assertNull(youtubeVideoId("https://www.youtube.com/playlist?list=PL123"))
        assertNull(youtubeVideoId("https://open.spotify.com/track/$id"))
        assertNull(youtubeVideoId("not a link"))
    }

    @Test
    fun spotifyArtistFromOgDescription() {
        val html = """<meta property="og:description" content="Simon &amp; Garfunkel · Bookends · Song · 1968">"""
        assertEquals("Simon & Garfunkel", spotifyArtistFromPage(html))
        assertNull(spotifyArtistFromPage("<html></html>"))
    }

    @Test
    fun normaliseAndCoreTitle() {
        assertEquals("beyonce halo", normalize("Beyoncé – Halo!"))
        assertEquals("Song", coreTitle("Song (feat. X) - Remastered 2011"))
        assertEquals("beatles", sortKey("The Beatles"))
    }

    private fun candidate(title: String, channel: String, duration: Int?) = AudioCandidate(title.hashCode().toString(), title, channel, duration)

    @Test
    fun topicUploadBeatsMusicVideo() {
        val topic = candidate("New Grass", "Talk Talk - Topic", 581)
        val video = candidate("Talk Talk - New Grass (Official Video)", "TalkTalkVEVO", 585)
        assertEquals(topic, bestCandidate(listOf(video, topic), "New Grass", "Talk Talk", 581))
    }

    @Test
    fun liveVersionsLoseUnlessWanted() {
        val live = candidate("Bohemian Rhapsody (Live at Wembley)", "Queen", 355)
        val studio = candidate("Bohemian Rhapsody", "Queen - Topic", 355)
        assertTrue(scoreCandidate(studio, "Bohemian Rhapsody", "Queen", 355)!! > scoreCandidate(live, "Bohemian Rhapsody", "Queen", 355)!!)
        val wanted = scoreCandidate(live, "Bohemian Rhapsody (Live at Wembley)", "Queen", 355)!!
        assertTrue(wanted >= 60)
    }

    @Test
    fun liveForeverIsNotPenalised() {
        val c = candidate("Oasis - Live Forever (Official Audio)", "Oasis", 276)
        assertTrue(scoreCandidate(c, "Live Forever", "Oasis", 276)!! >= 100)
    }

    @Test
    fun tooFarOffIsRejected() {
        assertNull(scoreCandidate(candidate("Song", "Artist - Topic", 216), "Song", "Artist", 200))
        assertTrue(scoreCandidate(candidate("Song", "Artist - Topic", 215), "Song", "Artist", 200) != null)
    }

    @Test
    fun accentsMatch() {
        val c = candidate("Beyonce - Halo", "Beyonce", 261)
        assertTrue(scoreCandidate(c, "Halo", "Beyoncé", 261)!! >= 100)
    }

    @Test
    fun artistSplittingNeverSplitsOnAmpersand() {
        assertEquals(listOf("Simon & Garfunkel"), splitCredits("The Boxer", "Simon & Garfunkel").artists)
        val split = splitCredits("Get Lucky (feat. Pharrell Williams)", "Daft Punk")
        assertEquals("Get Lucky", split.title)
        assertEquals(listOf("Daft Punk", "Pharrell Williams"), split.artists)
        assertEquals(listOf("A", "B", "C"), splitCredits("x", "A, B feat. C").artists)
    }

    @Test
    fun seededToneIsStable() {
        assertEquals(seededTone("Talk Talk"), seededTone("Talk Talk"))
        assertEquals(0.55f, seededTone("x").saturation)
        assertTrue(seededTone("abc").hue in 0f..359f)
    }
}

private typealias LyricLine = com.lumen.player.core.util.LyricLine
