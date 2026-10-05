package com.lumen.player

import com.lumen.player.core.db.LyricsKind
import com.lumen.player.download.sources.DeezerParse
import com.lumen.player.download.sources.ItunesParse
import com.lumen.player.download.sources.LrcLibPick
import com.lumen.player.download.sources.LrcRecord
import com.lumen.player.download.sources.YtDlpParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// invented fixtures in the shape of the real API responses
class ParsingTest {

    @Test
    fun deezerTrackWithContributors() {
        val json = """
            {"id":3135556,"title":"Song One (feat. Guest)","title_short":"Song One","duration":248,"explicit_lyrics":false,
             "isrc":"XX0000000001","bpm":0,"release_date":"2013-05-17","track_position":8,"disk_number":1,
             "artist":{"id":27,"name":"Main Act","picture_xl":"https://e/a.jpg"},
             "album":{"id":6575789,"title":"Album Name","cover_xl":"https://e/c.jpg"},
             "contributors":[{"id":27,"name":"Main Act","picture_xl":"https://e/a.jpg","role":"Main"},
                             {"id":1281,"name":"Guest","picture_xl":"https://e/g.jpg","role":"Featured"}]}
        """.trimIndent()
        val track = DeezerParse.track(json)
        assertEquals(3135556L, track.id)
        assertEquals(2, track.contributors.size)
        assertEquals("Guest", track.contributors[1].name)
        assertEquals("https://e/c.jpg", track.album?.coverXl)
        assertEquals(0f, track.bpm)
    }

    @Test
    fun deezerQuotaErrorCode() {
        assertEquals(4, DeezerParse.errorCode("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""))
        assertNull(DeezerParse.errorCode("""{"data":[]}"""))
    }

    @Test
    fun deezerAlbumGenres() {
        val album = DeezerParse.album("""{"id":1,"title":"A","genres":{"data":[{"id":1,"name":"Jazz"},{"id":2,"name":"Pop"}]},"tracks":{"data":[]}}""")
        assertEquals(listOf("Jazz", "Pop"), album.genres?.data?.map { it.name })
    }

    @Test
    fun itunesGenrePicksMatchingArtistAndLength() {
        val results = ItunesParse.results(
            """{"resultCount":2,"results":[
                {"trackName":"Song","artistName":"Someone Else","trackTimeMillis":200000,"primaryGenreName":"Pop"},
                {"trackName":"Song","artistName":"Talk Talk","trackTimeMillis":582000,"primaryGenreName":"Rock"}]}"""
        )
        assertEquals("Rock", ItunesParse.pickGenre(results, "Talk Talk", 580_000))
        assertNull(ItunesParse.pickGenre(results, "Talk Talk", 500_000))
    }

    @Test
    fun lrclibPickOrder() {
        val exactPlain = LrcRecord(plainLyrics = "plain text", duration = 200.0)
        val searchSynced = LrcRecord(syncedLyrics = "[00:01.00]x", duration = 202.0)
        val searchFar = LrcRecord(syncedLyrics = "[00:01.00]y", duration = 230.0)
        assertEquals(LyricsKind.SYNCED, LrcLibPick.choose(exactPlain, listOf(searchFar, searchSynced), 200).kind)
        assertEquals("[00:01.00]x", LrcLibPick.choose(exactPlain, listOf(searchFar, searchSynced), 200).text)
        assertEquals(LyricsKind.PLAIN, LrcLibPick.choose(exactPlain, listOf(searchFar), 200).kind)
        assertEquals(LyricsKind.INSTRUMENTAL, LrcLibPick.choose(LrcRecord(instrumental = true), emptyList(), 200).kind)
        assertEquals(LyricsKind.NONE, LrcLibPick.choose(null, emptyList(), 200).kind)
    }

    @Test
    fun ytDlpFlatSearch() {
        val json = """{"_type":"playlist","entries":[
            {"id":"abc123","title":"Song","channel":"Artist - Topic","duration":201.0},
            {"id":"def456","title":"Song live","uploader":"Fan","duration":null}]}"""
        val entries = YtDlpParse.searchEntries(json)
        assertEquals(2, entries.size)
        assertEquals(201, entries[0].durationSec)
        assertEquals("Fan", entries[1].channel)
        assertNull(entries[1].durationSec)
    }
}
