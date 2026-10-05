package com.lumen.player.data

import com.lumen.player.core.db.EndReason
import com.lumen.player.core.db.EventLite
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class RangeKind(val label: String) { TODAY("Today"), WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All time") }

data class Period(val from: Long, val to: Long, val label: String, val days: List<LocalDate>)

data class Streaks(val current: Int, val longest: Int)

data class SessionStats(val count: Int, val averageMs: Long, val longestMs: Long)

data class Trait(val name: String, val line: String, val strength: Float)

data class Personality(val main: Trait, val traits: List<Trait>)

const val PLAY_MS = 30_000L

// pure statistics helpers; every function here is unit-tested
object StatsCalculator {

    fun period(kind: RangeKind, offset: Int, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault(), firstEvent: LocalDate? = null): Period {
        fun ms(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (kind) {
            RangeKind.TODAY -> {
                val day = today.minusDays(offset.toLong())
                val label = when (offset) { 0 -> "Today"; 1 -> "Yesterday"; else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }
                Period(ms(day), ms(day.plusDays(1)), label, listOf(day))
            }
            RangeKind.WEEK -> {
                val firstDay = WeekFields.of(locale).firstDayOfWeek
                val start = today.with(TemporalAdjusters.previousOrSame(firstDay)).minusWeeks(offset.toLong())
                val end = start.plusDays(7)
                val label = if (offset == 0) "This week" else {
                    val f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
                    "${start.format(f)} – ${end.minusDays(1).format(f)}"
                }
                Period(ms(start), ms(end), label, (0 until 7).map { start.plusDays(it.toLong()) })
            }
            RangeKind.MONTH -> {
                val start = today.withDayOfMonth(1).minusMonths(offset.toLong())
                val end = start.plusMonths(1)
                Period(ms(start), ms(end), start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    (0 until start.lengthOfMonth()).map { start.plusDays(it.toLong()) })
            }
            RangeKind.YEAR -> {
                val start = today.withDayOfYear(1).minusYears(offset.toLong())
                val end = start.plusYears(1)
                Period(ms(start), ms(end), start.year.toString(), (0 until start.lengthOfYear()).map { start.plusDays(it.toLong()) })
            }
            RangeKind.ALL -> {
                val start = (firstEvent ?: today).coerceAtMost(today)
                val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
                Period(0, ms(today.plusDays(1)), "All time", days)
            }
        }
    }

    // the period just before this one, used for "▲ 12 %"
    fun previous(kind: RangeKind, offset: Int, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): Period? =
        if (kind == RangeKind.ALL) null else period(kind, offset + 1, today, zone, locale)

    fun change(current: Long, previous: Long): Int? = if (previous <= 0) null else (((current - previous) * 100.0) / previous).toInt()

    fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun streaks(days: Set<LocalDate>, today: LocalDate): Streaks {
        if (days.isEmpty()) return Streaks(0, 0)
        val sorted = days.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i - 1].plusDays(1) == sorted[i]) run + 1 else 1
            longest = maxOf(longest, run)
        }
        // today's streak still counts if you haven't listened yet today
        var cursor = if (today in days) today else today.minusDays(1)
        var current = 0
        while (cursor in days) {
            current++
            cursor = cursor.minusDays(1)
        }
        return Streaks(current, longest)
    }

    fun sessions(events: List<EventLite>): SessionStats {
        if (events.isEmpty()) return SessionStats(0, 0, 0)
        val lengths = events.groupBy { it.sessionId }.values.map { group -> group.maxOf { it.endedAt } - group.minOf { it.startedAt } }
        return SessionStats(lengths.size, lengths.average().toLong(), lengths.max())
    }

    // open rows (end_reason null) that are no longer playing count as STOPPED
    fun reasonOf(event: EventLite): EndReason = event.endReason ?: EndReason.STOPPED

    fun isSkip(event: EventLite) = reasonOf(event) == EndReason.SKIPPED ||
        (event.listenedMs < PLAY_MS && (event.trackDurationMs == 0L || event.listenedMs < event.trackDurationMs / 2) && reasonOf(event) != EndReason.COMPLETED)

    fun skipRate(events: List<EventLite>): Float = if (events.isEmpty()) 0f else events.count(::isSkip).toFloat() / events.size

    fun completionRate(events: List<EventLite>): Float =
        if (events.isEmpty()) 0f else events.count { reasonOf(it) == EndReason.COMPLETED }.toFloat() / events.size

    fun isPlay(event: EventLite) = event.listenedMs >= PLAY_MS ||
        (event.trackDurationMs in 1 until 60_000 && event.listenedMs * 2 >= event.trackDurationMs)

    data class RepeatMoment(val trackId: String, val plays: Int, val day: LocalDate)

    fun onRepeatMoment(events: List<EventLite>, zone: ZoneId): RepeatMoment? =
        events.filter(::isPlay).groupBy { it.trackId to day(it.startedAt, zone) }
            .maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= 3 }
            ?.let { RepeatMoment(it.key.first, it.value.size, it.key.second) }

    // share of listening time on songs whose first-ever play falls in this period
    fun newShare(events: List<EventLite>): Float {
        val total = events.sumOf { it.listenedMs }
        if (total == 0L) return 0f
        val newTracks = events.filter { it.firstListen }.map { it.trackId }.toSet()
        return events.filter { it.trackId in newTracks }.sumOf { it.listenedMs }.toFloat() / total
    }

    fun personality(
        events: List<EventLite>,
        hourMs: Map<Int, Long>,
        weekdayMs: Map<DayOfWeek, Long>,
        artistMs: Map<String, Long>,
        averageSessionMs: Long,
    ): Personality {
        val total = events.sumOf { it.listenedMs }.coerceAtLeast(1)
        val plays = events.filter(::isPlay)
        fun hours(range: Iterable<Int>) = range.sumOf { hourMs[it % 24] ?: 0L }.toFloat() / total
        val candidates = mutableListOf<Trait>()
        fun rule(name: String, line: String, value: Float, threshold: Float) {
            if (value >= threshold) candidates += Trait(name, line, value / threshold)
        }
        rule("Night Owl", "Your music comes alive after dark.", hours(22..27), 0.35f)
        rule("Early Bird", "You start the day with a soundtrack.", hours(5..8), 0.30f)
        val top = artistMs.values.maxOrNull() ?: 0L
        rule("Loyalist", "One artist owns your ears.", top.toFloat() / total, 0.40f)
        val firstShare = if (plays.isEmpty()) 0f else plays.count { it.firstListen }.toFloat() / plays.size
        val spread = artistMs.size >= 15 && artistMs.values.all { it.toFloat() / total <= 0.20f }
        rule("Explorer", "Always looking for something new.", maxOf(firstShare / 0.35f, if (spread) 1f else 0f) * 0.35f, 0.35f)
        val distinct = plays.map { it.trackId }.toSet().size
        rule("On Repeat", "When you love a song, you really love it.", if (distinct == 0) 0f else plays.size.toFloat() / distinct, 6f)
        rule("Marathoner", "Long sessions, deep listening.", averageSessionMs / 60_000f, 75f)
        val weekend = ((weekdayMs[DayOfWeek.SATURDAY] ?: 0L) + (weekdayMs[DayOfWeek.SUNDAY] ?: 0L)).toFloat() / total
        rule("Weekend Warrior", "Saturdays and Sundays sound better.", weekend, 0.45f)
        val skip = skipRate(events)
        if (completionRate(events) >= 0.85f && skip <= 0.10f) candidates += Trait("Completionist", "You let every song finish.", completionRate(events) / 0.85f)
        rule("Skipper", "You know what you want, fast.", skip, 0.35f)
        val sorted = candidates.sortedByDescending { it.strength }
        val main = sorted.firstOrNull() ?: Trait("Balanced Listener", "A little bit of everything, at any hour.", 1f)
        return Personality(main, sorted.drop(1).take(2))
    }
}
