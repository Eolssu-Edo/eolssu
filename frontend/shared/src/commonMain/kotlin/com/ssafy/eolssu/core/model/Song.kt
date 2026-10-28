package com.ssafy.eolssu.core.model

import kotlin.math.roundToInt

/** 곡 난이도. 화면 문구는 각 기능의 문자열 리소스로 정한다. */
enum class SongLevel { Intro, Elementary, Intermediate }

/**
 * 곡 목록·상세·연습·결과가 함께 쓰는 곡.
 *
 * @property id 영문 소문자 식별자. 정간 식별자([ScoreEvent.id])의 앞부분이 된다.
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
) {
    /** 연주 순서로 펼친 음 목록. */
    val timeline: List<ScoreEvent> by lazy { score.timeline(id) }

    /** [bpm] 속도로 처음부터 끝까지 연주하는 시간(초, 반올림). */
    fun durationSeconds(bpm: Int = baseBpm): Int = (score.totalUnits * jangdan.cellMillis(bpm) / 1000).roundToInt()
}
