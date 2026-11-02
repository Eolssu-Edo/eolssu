package com.ssafy.eolssu.backend.feature.lesson.application

import com.ssafy.eolssu.backend.feature.lesson.domain.LearnTrack
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonDetail
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonProgress
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonRepository
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonStep

/** LRN-01 단계 목록·진도와 LRN-02 단계 상세. 회원 본인의 완료 표시만 읽는다. */
class LessonQueries(private val repository: LessonRepository) {
    fun list(userId: Long, track: LearnTrack): LessonList {
        val steps = repository.findPublishedSteps(userId, track)
        return LessonList(track, LessonProgress.of(steps), steps)
    }

    /** 없거나 초안이면 null. */
    fun detail(userId: Long, lessonId: Long): LessonDetail? = repository.findPublishedDetail(userId, lessonId)
}

data class LessonList(val track: LearnTrack, val progress: LessonProgress, val steps: List<LessonStep>)
