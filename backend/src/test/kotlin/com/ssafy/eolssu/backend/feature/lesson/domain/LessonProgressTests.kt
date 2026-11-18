package com.ssafy.eolssu.backend.feature.lesson.domain

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LessonProgressTests {
    private val done = Instant.parse("2026-11-02T03:00:00Z")

    private fun step(id: Long, seq: Int, completed: Boolean, optional: Boolean = false) =
        LessonStep(id, LearnTrack.DANSO, seq, "단계 $seq", JudgeType.READ, optional, if (completed) done else null)

    @Test
    fun `counts only required stages and points to the first unfinished one`() {
        val steps = listOf(step(11, 1, true), step(12, 2, false), step(13, 3, true), step(19, 9, true, optional = true))

        assertEquals(LessonProgress(completedCount = 2, requiredCount = 3, nextLessonId = 12), LessonProgress.of(steps))
    }

    @Test
    fun `has no next stage when every required stage is done even if the optional one is not`() {
        val steps = listOf(step(11, 1, true), step(12, 2, true), step(19, 9, false, optional = true))

        val progress = LessonProgress.of(steps)

        assertEquals(2, progress.completedCount)
        assertNull(progress.nextLessonId)
    }

    @Test
    fun `orders by stage number rather than input order`() {
        assertEquals(21L, LessonProgress.of(listOf(step(22, 2, false), step(21, 1, false))).nextLessonId)
    }

    @Test
    fun `is empty for a track without published stages`() {
        assertEquals(LessonProgress(0, 0, null), LessonProgress.of(emptyList()))
    }

    @Test
    fun `accepts only exact upper case track codes`() {
        assertEquals(LearnTrack.DAEGEUM_SANJO, LearnTrack.parse("DAEGEUM_SANJO"))
        assertNull(LearnTrack.parse("danso"))
        assertNull(LearnTrack.parse("PIRI"))
        assertNull(LearnTrack.parse(null))
    }
}
