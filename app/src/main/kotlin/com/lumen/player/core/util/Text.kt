package com.lumen.player.core.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer

private val random = SecureRandom()
private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

fun newId(length: Int = 12): String = buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

private val MARKS = Regex("\\p{Mn}+")
private val NON_ALNUM = Regex("[^a-z0-9 ]")
private val SPACES = Regex("\\s+")

fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(NON_ALNUM, " ")
        .replace(SPACES, " ")
        .trim()

// sort key: normalised, without a leading "the " or "a "
fun sortKey(text: String): String {
    val key = normalize(text)
    return when {
        key.startsWith("the ") -> key.removePrefix("the ")
        key.startsWith("a ") -> key.removePrefix("a ")
        else -> key
    }.ifEmpty { text.lowercase() }
}

// "Song (feat. X) - Remastered 2011" -> "Song"
fun coreTitle(title: String): String =
    title.replace(Regex("""\s*[(\[](feat|ft|with)\.?\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+.*(remaster|version|mono|stereo|edit).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

fun artistIdFor(deezerId: Long?, name: String): String =
    if (deezerId != null) "dz$deezerId" else "nm${hash8(normalize(name))}"

fun albumIdFor(deezerId: Long?, title: String, artist: String): String =
    if (deezerId != null) "dz$deezerId" else "nm${hash8(normalize("$artist $title"))}"

private fun hash8(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)

private val FEAT_IN_TITLE = Regex("""\s*[(\[](?:feat|ft|featuring)\.?\s+([^)\]]+)[)\]]""", RegexOption.IGNORE_CASE)
private val ARTIST_SPLIT = Regex(""",|\s+feat\.\s+|\s+ft\.\s+|\s+featuring\s+""", RegexOption.IGNORE_CASE)

data class SplitCredits(val title: String, val artists: List<String>)

// fallback artist splitting when there's no Deezer data; never splits on "&"
fun splitCredits(title: String, artist: String): SplitCredits {
    val names = artist.split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    var cleanTitle = title
    FEAT_IN_TITLE.find(title)?.let { match ->
        match.groupValues[1].split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }.forEach { featured ->
            if (names.none { normalize(it) == normalize(featured) }) names += featured
        }
        cleanTitle = title.removeRange(match.range).trim()
    }
    return SplitCredits(cleanTitle, names.ifEmpty { listOf(artist.trim()) })
}
