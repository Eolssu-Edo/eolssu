package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent

/** 내 악보의 공통 악보 v1 처리. 제공 곡(CAT-04)과 같은 검증·타임라인 규칙을 쓴다. */
interface SheetScores {
    /** CAT-04 조회용 타임라인. 저장 악보의 구조·음표 수·버전·단위가 맞지 않으면 null. */
    fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String): List<ScoreTimelineEvent>?

    /** SHT-04 저장 전 확인. 미입력 칸(units 빈 배열)을 먼저 찾고, 없으면 악보 버전만 [scoreVersion]으로 바꾼다. 타임라인 검증은 [timeline]이 한다. */
    fun prepare(scoreJson: String, scoreVersion: Int): PreparedScore

    /** SHT-11 초안의 미입력 칸 수. 공통 악보 v1의 최상위 모양이 아니면 null. */
    fun unfilledCellCount(scoreJson: String): Int?
}

sealed interface PreparedScore {
    /** [jangdanName]·[bpmUnit]은 music 값이다. 장단이 없는 악보는 jangdan이 빈 문자열일 수 있다. */
    data class Ready(val json: String, val columnCount: Int, val noteCount: Int, val jangdanName: String, val bpmUnit: String) : PreparedScore
    data class Unfilled(val cellIds: List<String>) : PreparedScore
    data object Malformed : PreparedScore
}
