package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.data.user.LessonDetail
import com.ssafy.eolssu.data.user.LessonItem

/*
 * 기초부터 배우기 9단계. 단계 문구·트랙별 내용은 서버(LRN-01·02, `docs/specs/learning-progress-apis.md`)가 유일한 출처이고,
 * 앱은 화면 구성·섹션 제목과 4·5·6단계 할 일·패턴을 만드는 규칙만 둔다(S15P31E102-370).
 */

/** 단계의 판정 방식. */
enum class LearnJudge {
    Read, Pitch, Beat, Listen;

    companion object {
        /** 서버 `judgeType`(READ·PITCH·BEAT·LISTEN). 모르는 값은 설명 단계로 본다. */
        fun of(code: String): LearnJudge = when (code) {
            "PITCH" -> Pitch
            "BEAT" -> Beat
            "LISTEN" -> Listen
            else -> Read
        }
    }
}

/** 할 일 한 줄. [detail]이 null이면 제목만 보인다. */
data class LearnTask(val title: String, val detail: String? = null)

/** 통과 기준. [rule]을 굵게, [note]를 회색으로 쓴다. */
data class LearnPass(val rule: String, val note: String)

/**
 * 단계 목록의 한 단계(LRN-01). 단계 상세 위·아래(제목·이전/다음·주 버튼)도 이것으로 그린다.
 *
 * @property optional 진행률에 넣지 않는 선택 단계(9단계).
 */
data class LearnStage(
    val number: Int,
    val title: String,
    val judge: LearnJudge,
    val optional: Boolean = false,
    val lessonId: String = "",
)

/** 6단계에서 새로 내는 음역. [first]부터 낸다. */
data class LearnRange(val blow: Blow, val label: String, val description: String, val first: String)

/** 8단계 연습할 곡 후보. 같은 제목의 평가 가능한 곡이 있으면 곡 상세로 간다. */
data class LearnSong(val title: String, val meta: String)

/**
 * 단계 상세 내용(LRN-02). 공통 문구와 지금 악기(트랙)의 내용을 합친 것이다.
 *
 * @property pitchNames 율명 한자. 3단계는 처음 낼 음, 4단계는 익힐 순서(앞 두 음의 운지를 보인다), 5·8단계는 기본 5음(낮은 음부터).
 * @property pitchCaution 4단계 익힐 순서 아래 주의 문구(앞 '※ '는 화면이 붙인다).
 * @property parts 1단계 악기 부분 이름.
 */
data class LearnStageDetail(
    val goal: String,
    val why: String,
    val source: String,
    val tasks: List<LearnTask> = emptyList(),
    val pass: LearnPass? = null,
    val pitchNames: List<String> = emptyList(),
    val pitchNote: String? = null,
    val pitchCaution: String? = null,
    val parts: List<String> = emptyList(),
    val ranges: List<LearnRange> = emptyList(),
    val songs: List<LearnSong> = emptyList(),
)

/** 단계 목록 응답을 화면 단계로 바꾼다. */
internal fun LessonItem.toStage() = LearnStage(seq, title, LearnJudge.of(judgeType), optional, lessonId)

/** 단계 상세 응답을 화면 내용으로 바꾼다. 통과 기준 설명이 없으면 빈 문구로 둔다. */
internal fun LessonDetail.toStageDetail() = LearnStageDetail(
    goal = goal,
    why = why,
    source = source,
    tasks = tasks.map { LearnTask(it.title, it.description) },
    pass = pass?.let { LearnPass(it.title, it.description.orEmpty()) },
    pitchNames = pitchNames,
    pitchNote = pitchNote?.text,
    pitchCaution = pitchNote?.caution,
    parts = parts,
    ranges = ranges.map { LearnRange(it.blow, it.label, it.description, it.firstPitch) },
    songs = songs.map { LearnSong(it.title, it.meta) },
)

/** 5단계 연습 패턴. 건너뛰기·세 음 동요는 같은 5음의 반복이라 뺐다(S15P31E102-655). */
enum class LearnPattern { Ascending, Descending }

/** [scale](기본 5음, 낮은 음부터)로 만든 패턴의 음 순서. 기본 5음이 모자라면 빈 목록이다. */
fun LearnPattern.notes(scale: List<String>): List<String> {
    if (scale.size < 5) return emptyList()
    return when (this) {
        LearnPattern.Ascending -> scale
        LearnPattern.Descending -> scale.reversed()
    }
}
