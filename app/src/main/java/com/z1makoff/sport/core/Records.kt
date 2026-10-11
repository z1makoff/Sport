package com.z1makoff.sport.core

/** One timed step of a workout: exercise [exerciseIndex] in round [round] (both 0-based). */
data class Step(val round: Int, val exerciseIndex: Int, val name: String, val reps: String)

data class ExerciseSpec(val name: String, val reps: String)

fun buildSteps(exercises: List<ExerciseSpec>, rounds: Int): List<Step> =
    (0 until rounds.coerceAtLeast(1)).flatMap { r ->
        exercises.mapIndexed { i, e -> Step(r, i, e.name, e.reps) }
    }

/** A finished attempt: total time and cumulative split per step (may be empty for manual entries). */
data class AttemptTimes(val id: Long, val totalMs: Long, val splitsMs: List<Long>)

/** The record is the fastest attempt; on a tie the earlier one (lower id) keeps it. */
fun bestAttempt(attempts: List<AttemptTimes>): AttemptTimes? =
    attempts.minWithOrNull(compareBy<AttemptTimes> { it.totalMs }.thenBy { it.id })

/** Today's goal: one second faster than the record. */
fun goalMs(recordMs: Long?): Long? = recordMs?.let { (it - 1000).coerceAtLeast(1000) }

/**
 * How far the current attempt is from the record attempt ("ghost"), in ms.
 * Negative means ahead of the record, positive means behind.
 *
 * With per-step splits of the record: the gap fixed at the last completed step,
 * or, if the record had already finished the current step by now, the growing gap
 * since then, whichever is larger.
 *
 * Returns null when the record has no matching splits; the caller then compares
 * only against the record's total.
 */
fun ghostDeltaMs(elapsedMs: Long, splitsMs: List<Long>, recordSplitsMs: List<Long>, stepCount: Int): Long? {
    if (recordSplitsMs.size != stepCount || stepCount == 0) return null
    val done = splitsMs.size
    val lastDelta = if (done == 0) 0L else splitsMs[done - 1] - recordSplitsMs[done - 1]
    if (done >= stepCount) return lastDelta
    val liveBehind = elapsedMs - recordSplitsMs[done]
    return maxOf(lastDelta, liveBehind)
}

fun encodeSplits(splits: List<Long>): String = splits.joinToString(",")

fun decodeSplits(text: String): List<Long> =
    if (text.isBlank()) emptyList() else text.split(',').mapNotNull { it.trim().toLongOrNull() }

enum class GhostStatus { Ahead, Even, Behind }

/**
 * Within half a second either way counts as even with the record, so the line does not
 * flicker between "ahead" and "behind" on tenths. Same band as rounding the gap to whole seconds.
 */
fun ghostStatus(deltaMs: Long): GhostStatus = when {
    deltaMs <= -500 -> GhostStatus.Ahead
    deltaMs >= 500 -> GhostStatus.Behind
    else -> GhostStatus.Even
}

/** The push shown and spoken during a rest, depending on where you stand against the record. */
fun restPush(status: GhostStatus): String = when (status) {
    GhostStatus.Ahead -> "Ты впереди рекорда, не сбавляй!"
    GhostStatus.Even -> "Идёшь вровень с рекордом, поднажми!"
    GhostStatus.Behind -> "Выложись до самого конца!"
}

/** "5 сек", "1 мин 5 сек"; under a second is "меньше секунды". Truncates like [formatTime]. */
fun spokenDuration(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return when {
        s == 0L -> "меньше секунды"
        s < 60 -> "$s сек"
        s % 60 == 0L -> "${s / 60} мин"
        else -> "${s / 60} мин ${s % 60} сек"
    }
}

/** Spoken at the start of a rest: the gap to the record ghost and a push. */
fun restAnnouncement(deltaMs: Long): String {
    val status = ghostStatus(deltaMs)
    val gap = when (status) {
        GhostStatus.Ahead -> "Впереди рекорда на ${spokenDuration(-deltaMs)}. "
        GhostStatus.Behind -> "Позади рекорда на ${spokenDuration(deltaMs)}. "
        GhostStatus.Even -> ""
    }
    return gap + restPush(status)
}
