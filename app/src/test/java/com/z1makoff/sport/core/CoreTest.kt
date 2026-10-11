package com.z1makoff.sport.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreTest {
    @Test fun formats() {
        assertEquals("0:00", formatTime(999))
        assertEquals("12:34", formatTime(754_900))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("12:34.9", formatTimeTenths(754_900))
        assertEquals("+0:07", formatDelta(7_200))
        assertEquals("−1:05", formatDelta(-65_000))
        assertEquals("−0:00.4", formatDelta(-400))
        assertEquals("±0:00", formatDelta(0))
    }

    @Test fun parses() {
        assertEquals(754_000L, parseTime("12:34"))
        assertEquals(754_500L, parseTime("12:34.5"))
        assertEquals(754_500L, parseTime("12:34,5"))
        assertEquals(95_000L, parseTime("95"))
        assertEquals(3_723_000L, parseTime("1:02:03"))
        assertNull(parseTime("12:75"))
        assertNull(parseTime("abc"))
        assertNull(parseTime(""))
        assertNull(parseTime("0"))
    }

    @Test fun steps() {
        val s = buildSteps(listOf(ExerciseSpec("A", "10"), ExerciseSpec("B", "20")), 3)
        assertEquals(6, s.size)
        assertEquals(Step(2, 1, "B", "20"), s.last())
    }

    @Test fun record() {
        val a = listOf(AttemptTimes(1, 600_000, emptyList()), AttemptTimes(2, 590_000, emptyList()), AttemptTimes(3, 590_000, emptyList()))
        assertEquals(2L, bestAttempt(a)?.id)
        assertNull(bestAttempt(emptyList()))
        assertEquals(589_000L, goalMs(590_000))
        assertNull(goalMs(null))
    }

    @Test fun ghost() {
        val rec = listOf(60_000L, 120_000L, 180_000L)
        // At the start, before the record finished step 1: even.
        assertEquals(0L, ghostDeltaMs(30_000, emptyList(), rec, 3))
        // Record finished step 1 at 60 s, we are at 65 s and still on it: 5 s behind.
        assertEquals(5_000L, ghostDeltaMs(65_000, emptyList(), rec, 3))
        // We finished step 1 at 55 s: 5 s ahead, and stay ahead until 120 s.
        assertEquals(-5_000L, ghostDeltaMs(100_000, listOf(55_000), rec, 3))
        assertEquals(3_000L, ghostDeltaMs(123_000, listOf(55_000), rec, 3))
        // Finished.
        assertEquals(-2_000L, ghostDeltaMs(178_000, listOf(55_000, 118_000, 178_000), rec, 3))
        // Record without matching splits.
        assertNull(ghostDeltaMs(10_000, emptyList(), listOf(1L), 3))
    }

    @Test fun ghostStatusBand() {
        assertEquals(GhostStatus.Even, ghostStatus(0))
        assertEquals(GhostStatus.Even, ghostStatus(499))
        assertEquals(GhostStatus.Even, ghostStatus(-499))
        assertEquals(GhostStatus.Behind, ghostStatus(500))
        assertEquals(GhostStatus.Ahead, ghostStatus(-500))
    }

    @Test fun restWords() {
        assertEquals("меньше секунды", spokenDuration(400))
        assertEquals("5 сек", spokenDuration(5_900))
        assertEquals("2 мин", spokenDuration(120_000))
        assertEquals("1 мин 5 сек", spokenDuration(65_000))
        assertEquals("Впереди рекорда на 5 сек. Ты впереди рекорда, не сбавляй!", restAnnouncement(-5_200))
        assertEquals("Позади рекорда на 3 сек. Выложись до самого конца!", restAnnouncement(3_000))
        assertEquals("Идёшь вровень с рекордом, поднажми!", restAnnouncement(200))
    }

    @Test fun splitsRoundTrip() {
        assertEquals(listOf(1L, 22L, 333L), decodeSplits(encodeSplits(listOf(1, 22, 333))))
        assertEquals(emptyList<Long>(), decodeSplits(""))
    }
}
