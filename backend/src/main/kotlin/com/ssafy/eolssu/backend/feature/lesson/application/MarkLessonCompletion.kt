package com.ssafy.eolssu.backend.feature.lesson.application

import com.ssafy.eolssu.backend.feature.lesson.domain.LessonProgress
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * LRN-03 완료 표시·해제. 사용자가 누른 표시만 저장하고 서버는 통과 기준을 판정하지 않는다.
 * 같은 요청을 다시 보내도 결과가 같고, 처음 완료 시각을 유지한다. 응답의 완료 시각과 진도는 저장한 뒤 다시 읽는다.
 */
class MarkLessonCompletion(private val repository: LessonRepository, private val clock: Clock) {
    fun execute(userId: Long, lessonId: Long, completed: Boolean): LessonCompletion {
        val step = repository.findPublishedStep(userId, lessonId) ?: return LessonCompletion.NotFound
        if (completed) {
            // DB는 마이크로초(DATETIME(6))까지 저장한다. 반올림 대신 미리 잘라 저장값과 같게 둔다.
            repository.markCompleted(userId, lessonId, clock.instant().truncatedTo(ChronoUnit.MICROS))
        } else {
            repository.clearCompletion(userId, lessonId)
        }
        val steps = repository.findPublishedSteps(userId, step.track)
        return LessonCompletion.Done(lessonId, completed, steps.firstOrNull { it.id == lessonId }?.completedAt, LessonProgress.of(steps))
    }
}

sealed interface LessonCompletion {
    data object NotFound : LessonCompletion

    data class Done(val lessonId: Long, val completed: Boolean, val completedAt: Instant?, val progress: LessonProgress) : LessonCompletion
}
