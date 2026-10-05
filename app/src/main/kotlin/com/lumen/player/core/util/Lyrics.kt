package com.lumen.player.core.util

import kotlin.math.pow

data class LyricLine(val timeMs: Long, val text: String)

data class ParsedLyrics(val synced: Boolean, val lines: List<LyricLine>)

private val LRC_LINE = Regex("""((?:\[\d+:\d+(?:[.:]\d+)?\])+)(.*)""")
private val LRC_TAG = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\]""")

fun parseLyrics(raw: String): ParsedLyrics {
    val synced = mutableListOf<LyricLine>()
    val plain = mutableListOf<String>()
    for (line in raw.lineSequence().map { it.trim() }) {
        val match = LRC_LINE.matchEntire(line)
        if (match != null) {
            val text = match.groupValues[2].trim()
            for (tag in LRC_TAG.findAll(match.groupValues[1])) {
                val (minutes, seconds, fraction) = tag.destructured
                var ms = (minutes.toLong() * 60 + seconds.toLong()) * 1000
                if (fraction.isNotEmpty()) ms += (fraction.toDouble() / 10.0.pow(fraction.length) * 1000).toLong()
                synced += LyricLine(ms, text)
            }
        } else if (line.isNotEmpty() && !line.startsWith("[")) {
            plain += line
        }
    }
    if (synced.isNotEmpty()) {
        synced.sortBy { it.timeMs }
        if (synced.first().timeMs > 4_000) synced.add(0, LyricLine(0, ""))
        return ParsedLyrics(true, synced)
    }
    return ParsedLyrics(false, plain.map { LyricLine(-1, it) })
}

fun activeLineIndex(lines: List<LyricLine>, positionMs: Long, lookaheadMs: Long = 180): Int {
    var lo = 0
    var hi = lines.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs + lookaheadMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}
