package com.ssafy.eolssu.core.model

import kotlin.math.roundToInt

/** 곡 난이도. 화면 문구는 각 기능의 문자열 리소스로 정한다. */
enum class SongLevel { Intro, Elementary, Intermediate }

/**
 * 곡 목록·상세·연습·결과가 함께 쓰는 곡.
 *
 * @property id 서비스가 발급한 식별자. sourceId·악기 코드·이벤트 ID와 구분하며 임의로 재작성하지 않는다.
 * @property evaluable 음표·박자 기준 데이터가 있어 AI 연주 평가를 할 수 있는지. 아니면 혼자 연습만 할 수 있다.
 * @property solfege 이 곡·악기 기준의 율명 → 계이름 대응. 데이터가 없으면 null이고 계이름을 표시하지 않는다.
 */
data class Song(
    val id: String,
    val title: String,
    val level: SongLevel,
    val jangdan: Jangdan,
    val baseBpm: Int,
    val description: String,
    val evaluable: Boolean,
    val solfege: Map<Yul, String>?,
    val score: Score,
    val instrumentId: String? = null,
    val scoreVersion: Int? = null,
    val noteCount: Int? = null,
) {
    /** 연주 순서로 펼친 음 목록. */
    val timeline: List<ScoreEvent> by lazy { score.timeline(id) }

    val displayUnits: List<ScoreDisplayUnit> by lazy { score.displayUnits(timeline) }

    /** 공통 악보의 BPM 단위를 그대로 사용한다. 장단 이름만으로 정간 길이를 추정하지 않는다. */
    fun cellDurationMs(bpm: Int = baseBpm): Double {
        require(bpm > 0) { "BPM은 양수여야 한다" }
        return score.timing?.let { 60_000.0 / bpm * it.ticksPerJeonggan / it.ticksPerBeat }
            ?: jangdan.cellMillis(bpm)
    }

    fun eventDurationMs(event: ScoreEvent, bpm: Int = baseBpm): Double {
        require(bpm > 0) { "BPM은 양수여야 한다" }
        return score.timing?.let { 60_000.0 / bpm * requireNotNull(event.durationTick) / it.ticksPerBeat }
            ?: (event.unitDuration * cellDurationMs(bpm))
    }

    fun durationMs(bpm: Int = baseBpm): Double = score.totalUnits * cellDurationMs(bpm)

    /** [bpm] 속도로 처음부터 끝까지 연주하는 시간(초, 반올림). */
    fun durationSeconds(bpm: Int = baseBpm): Int = (durationMs(bpm) / 1000).roundToInt()
}
