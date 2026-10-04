package com.z1makoff.sport.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")

fun formatDay(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val days = ((startOfDay(now) - startOfDay(then)) / 86_400_000L).toInt()
    return when (days) {
        0 -> "сегодня"
        1 -> "вчера"
        else -> SimpleDateFormat(if (sameYear) "d MMM" else "d MMM yyyy", ru).format(Date(millis))
    }
}

fun formatDateTime(millis: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", ru).format(Date(millis))

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
