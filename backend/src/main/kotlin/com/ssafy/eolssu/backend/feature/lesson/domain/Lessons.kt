package com.ssafy.eolssu.backend.feature.lesson.domain

import java.time.Instant

/**
 * '기초부터 배우기' 학습 트랙. 앱의 학습 악기(GuideInstrument)와 하나씩 대응하며 `instruments` 테이블과는 묶지 않는다.
 * 정악·산조대금은 같은 악기지만 운지표가 달라 트랙을 나눈다(docs/specs/learning-progress-apis.md).
 */
enum class LearnTrack {
    DANSO,
    SOGEUM,
    DAEGEUM_JEONGAK,
    DAEGEUM_SANJO,
    ;

    companion object {
        /** API 값은 대문자 코드 그대로만 받는다. 모르면 null. */
        fun parse(code: String?): LearnTrack? = entries.firstOrNull { it.name == code }
    }
}

/** 단계의 통과 확인 방식 안내. 서버는 판정하지 않고 사용자가 누른 완료 표시만 저장한다. */
enum class JudgeType { READ, PITCH, BEAT, LISTEN }

/** 공개 단계 하나와 본인 완료 시각(미완료면 null). */
data class LessonStep(
    val id: Long,
    val track: LearnTrack,
    val seq: Int,
    val title: String,
    val judgeType: JudgeType,
    val optional: Boolean,
    val completedAt: Instant?,
)

/** 트랙 진도. 선택 단계는 세지 않고, 다음 단계는 완료하지 않은 가장 앞 필수 단계다. */
data class LessonProgress(val completedCount: Int, val requiredCount: Int, val nextLessonId: Long?) {
    companion object {
        fun of(steps: List<LessonStep>): LessonProgress {
            val required = steps.filterNot { it.optional }.sortedBy { it.seq }
            return LessonProgress(
                completedCount = required.count { it.completedAt != null },
                requiredCount = required.size,
                nextLessonId = required.firstOrNull { it.completedAt == null }?.id,
            )
        }
    }
}

/** 공개 단계 하나의 상세 문구와 본인 완료 시각. */
data class LessonDetail(val step: LessonStep, val content: LessonContent)

/**
 * LRN-02 단계 상세. (트랙, 단계)마다 공통 문구와 트랙별 내용을 합친 완성본이며 해당하지 않는 목록은 비어 있다.
 * pitchNames·firstPitch는 앱 운지표와 같은 율명 한자다.
 */
data class LessonContent(
    val goal: String,
    val why: String,
    val tasks: List<LessonTask>,
    val passCriteria: PassCriteria?,
    val pitchNames: List<String>,
    val pitchNote: PitchNote?,
    val parts: List<String>,
    val ranges: List<PitchRange>,
    val songs: List<SongCandidate>,
    val source: String,
)

data class LessonTask(val title: String, val description: String?)

data class PassCriteria(val title: String, val description: String)

data class PitchNote(val text: String, val caution: String?)

/** 6단계 음역 하나. blow는 취법(HIGH 역취·LOW 저취), firstPitch는 처음 내 볼 음. */
data class PitchRange(val blow: String, val label: String, val description: String, val firstPitch: String)

/** 8단계 연습곡 후보. 곡 연결은 앱이 같은 제목의 평가 가능한 곡을 찾는다. */
data class SongCandidate(val title: String, val meta: String)
