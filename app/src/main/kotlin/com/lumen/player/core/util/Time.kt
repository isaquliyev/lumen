package com.lumen.player.core.util

import java.util.Locale

fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}

// "1 h 32 min", "42 min", "5 min"
fun formatLength(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return when {
        hours > 0 && minutes % 60 > 0 -> "$hours h ${minutes % 60} min"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
}

fun formatCount(count: Int, word: String) = "%,d %s".format(Locale.US, count, if (count == 1) word else "${word}s")

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
}
