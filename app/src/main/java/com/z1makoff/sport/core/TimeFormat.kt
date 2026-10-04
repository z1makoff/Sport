package com.z1makoff.sport.core

import kotlin.math.abs

/** "12:34" or "1:02:03". Seconds are truncated, never rounded up. */
fun formatTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "12:34.5" — used while the timer is running. */
fun formatTimeTenths(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0) % 1000) / 100
    return "${formatTime(ms)}.$tenths"
}

/** "+0:07" or "−0:07"; zero is shown as "±0:00". */
fun formatDelta(ms: Long): String {
    if (ms / 1000 == 0L && ms != 0L) {
        val tenths = abs(ms) / 100
        return (if (ms > 0) "+" else "−") + "0:00.$tenths"
    }
    if (ms == 0L) return "±0:00"
    return (if (ms > 0) "+" else "−") + formatTime(abs(ms))
}

/**
 * Parses "12:34", "1:02:03", "12:34.5" or plain seconds "95".
 * Returns null when the text is not a time.
 */
fun parseTime(text: String): Long? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    val parts = t.split(':')
    if (parts.size > 3) return null
    val secPart = parts.last()
    val seconds = secPart.toDoubleOrNull() ?: return null
    if (seconds < 0) return null
    if (parts.size > 1 && seconds >= 60) return null
    var totalMs = Math.round(seconds * 1000)
    val ints = parts.dropLast(1).map { it.toLongOrNull() ?: return null }
    if (ints.any { it < 0 }) return null
    when (ints.size) {
        1 -> totalMs += ints[0] * 60_000
        2 -> {
            if (ints[1] >= 60) return null
            totalMs += ints[0] * 3_600_000 + ints[1] * 60_000
        }
    }
    return if (totalMs > 0) totalMs else null
}
