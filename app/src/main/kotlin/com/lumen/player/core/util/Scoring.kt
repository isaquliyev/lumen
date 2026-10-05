package com.lumen.player.core.util

import kotlin.math.abs

data class AudioCandidate(val videoId: String, val title: String, val channel: String, val durationSec: Int?)

private val VARIANT_WORDS = listOf(
    "live", "cover", "remix", "karaoke", "instrumental", "sped up", "slowed", "reverb", "8d",
    "nightcore", "acoustic", "demo", "extended", "mashup", "tutorial", "reaction", "edit",
)

private val VARIANT_REGEX = VARIANT_WORDS.associateWith { Regex("\\b${Regex.escape(it)}\\b") }

fun scoreCandidate(candidate: AudioCandidate, title: String, artist: String, durationSec: Int?): Int? {
    var score = 100
    if (durationSec != null && candidate.durationSec != null) {
        val diff = abs(candidate.durationSec - durationSec)
        if (diff > 15) return null
        score -= diff * 4
    }
    val candidateTitle = normalize(candidate.title)
    val wantTitle = normalize(coreTitle(title))
    val wantFull = normalize(title)
    score += if (candidateTitle.contains(wantTitle)) 20 else -30
    if ("$candidateTitle ${normalize(candidate.channel)}".contains(normalize(artist))) score += 10
    if (candidate.channel.endsWith(" - Topic")) score += 25
    if ("official audio" in candidateTitle) score += 10
    if ("official video" in candidateTitle || "music video" in candidateTitle) score -= 8
    for ((word, regex) in VARIANT_REGEX) {
        if (regex.containsMatchIn(candidateTitle) && !wantFull.contains(word)) score -= 40
    }
    return score
}

fun bestCandidate(candidates: List<AudioCandidate>, title: String, artist: String, durationSec: Int?, minScore: Int = 60): AudioCandidate? =
    candidates.mapNotNull { c -> scoreCandidate(c, title, artist, durationSec)?.let { c to it } }
        .filter { it.second >= minScore }
        .maxByOrNull { it.second }?.first

fun rankedCandidates(candidates: List<AudioCandidate>, title: String, artist: String, durationSec: Int?): List<AudioCandidate> =
    candidates.sortedByDescending { scoreCandidate(it, title, artist, durationSec) ?: -1000 }
