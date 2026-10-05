package com.lumen.player

import com.lumen.player.core.db.EndReason
import com.lumen.player.core.db.EventLite
import com.lumen.player.data.RangeKind
import com.lumen.player.data.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class StatsCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 5)

    private fun event(track: String, start: Long, listened: Long, duration: Long = 200_000, reason: EndReason? = EndReason.COMPLETED, first: Boolean = false, session: String = "s") =
        EventLite(track, start, start + listened, listened, duration, reason, first, session)

    @Test
    fun streaksCountCurrentAndLongest() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(10), today.minusDays(11), today.minusDays(12), today.minusDays(13))
        val streaks = StatsCalculator.streaks(days, today)
        assertEquals(3, streaks.current)
        assertEquals(4, streaks.longest)
        // no listening yet today still keeps yesterday's streak
        assertEquals(2, StatsCalculator.streaks(setOf(today.minusDays(1), today.minusDays(2)), today).current)
        assertEquals(0, StatsCalculator.streaks(emptySet(), today).current)
    }

    @Test
    fun sessionsUseSessionIds() {
        val events = listOf(
            event("a", 0, 60_000, session = "1"), event("b", 70_000, 60_000, session = "1"),
            event("c", 10_000_000, 30_000, session = "2"),
        )
        val stats = StatsCalculator.sessions(events)
        assertEquals(2, stats.count)
        assertEquals(130_000, stats.longestMs)
        assertEquals((130_000 + 30_000) / 2L, stats.averageMs)
    }

    @Test
    fun ratesAndPlays() {
        val events = listOf(
            event("a", 0, 10_000, reason = EndReason.SKIPPED),
            event("b", 0, 200_000),
            event("c", 0, 100_000, reason = null),
            event("d", 0, 25_000, duration = 40_000, reason = EndReason.STOPPED),
        )
        assertEquals(0.25f, StatsCalculator.skipRate(events), 0.001f)
        assertEquals(0.25f, StatsCalculator.completionRate(events), 0.001f)
        assertTrue(StatsCalculator.isPlay(events[3]))
        assertTrue(!StatsCalculator.isPlay(events[0]))
    }

    @Test
    fun percentChange() {
        assertEquals(12, StatsCalculator.change(112, 100))
        assertEquals(-50, StatsCalculator.change(50, 100))
        assertNull(StatsCalculator.change(50, 0))
    }

    @Test
    fun periodsUseLocalDays() {
        val week = StatsCalculator.period(RangeKind.WEEK, 0, today, zone, Locale.UK)
        assertEquals(7, week.days.size)
        assertEquals(DayOfWeek.MONDAY, week.days.first().dayOfWeek)
        val month = StatsCalculator.period(RangeKind.MONTH, 1, today, zone)
        assertEquals("September 2026", month.label)
        assertEquals(30, month.days.size)
        assertEquals("Yesterday", StatsCalculator.period(RangeKind.TODAY, 1, today, zone).label)
    }

    @Test
    fun repeatMomentAndNewShare() {
        val day = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val events = (0 until 5).map { event("a", day + it * 300_000L, 200_000) } + event("b", day, 100_000, first = true)
        val moment = StatsCalculator.onRepeatMoment(events, zone)!!
        assertEquals("a", moment.trackId)
        assertEquals(5, moment.plays)
        assertEquals(100_000f / 1_100_000f, StatsCalculator.newShare(events), 0.001f)
    }

    @Test
    fun personalityPicksStrongestRule() {
        val events = (0 until 10).map { event("a", it * 1000L, 200_000) }
        val hours = mapOf(23 to 1_500_000L, 1 to 500_000L)
        val result = StatsCalculator.personality(events, hours, emptyMap(), mapOf("x" to 2_000_000L), 0)
        // 100 % at night (Night Owl 2.9x), one artist 100 % (Loyalist 2.5x), 10 plays of one song (On Repeat 1.7x) beat Completionist (1.2x)
        assertEquals("Night Owl", result.main.name)
        assertEquals(listOf("Loyalist", "On Repeat"), result.traits.map { it.name })
        val balanced = StatsCalculator.personality(emptyList(), emptyMap(), emptyMap(), emptyMap(), 0)
        assertEquals("Balanced Listener", balanced.main.name)
    }
}
