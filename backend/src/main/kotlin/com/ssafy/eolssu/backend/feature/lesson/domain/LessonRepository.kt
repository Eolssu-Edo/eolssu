package com.ssafy.eolssu.backend.feature.lesson.domain

/** 공개(PUBLISHED) 학습 단계와 회원 본인의 완료 표시. 초안 단계는 없는 것으로 본다. */
interface LessonRepository {
    /** 트랙의 공개 단계를 seq 순서로, 회원의 완료 시각과 함께. */
    fun findPublishedSteps(userId: Long, track: LearnTrack): List<LessonStep>

    /** 공개 단계 하나의 상세와 회원의 완료 시각. 없거나 초안이면 null. */
    fun findPublishedDetail(userId: Long, lessonId: Long): LessonDetail?
}
