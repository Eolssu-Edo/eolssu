package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.lesson.domain.JudgeType
import com.ssafy.eolssu.backend.feature.lesson.domain.LearnTrack
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonDetail
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonRepository
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonStep
import java.time.LocalDateTime
import java.time.ZoneOffset

/** DB 시각은 UTC로 저장한다(V1 머리말). track·judge_type은 V13 CHECK가 막아 모르는 값이 오지 않는다. */
class MyBatisLessonRepository(
    private val mapper: LessonMapper,
    private val content: LessonContentJson,
) : LessonRepository {
    override fun findPublishedSteps(userId: Long, track: LearnTrack): List<LessonStep> =
        mapper.findPublishedSteps(userId, track.name).map {
            step(it.id, it.track, it.seq, it.title, it.judgeType, it.isOptional, it.completedAt)
        }

    override fun findPublishedDetail(userId: Long, lessonId: Long): LessonDetail? =
        mapper.findPublishedDetail(userId, lessonId)?.let {
            LessonDetail(
                step(it.id, it.track, it.seq, it.title, it.judgeType, it.isOptional, it.completedAt),
                content.parse(it.id, it.contentJson),
            )
        }

    private fun step(id: Long, track: String, seq: Short, title: String, judgeType: String, optional: Boolean, completedAt: LocalDateTime?) =
        LessonStep(
            id = id,
            track = LearnTrack.valueOf(track),
            seq = seq.toInt(),
            title = title,
            judgeType = JudgeType.valueOf(judgeType),
            optional = optional,
            completedAt = completedAt?.toInstant(ZoneOffset.UTC),
        )
}
