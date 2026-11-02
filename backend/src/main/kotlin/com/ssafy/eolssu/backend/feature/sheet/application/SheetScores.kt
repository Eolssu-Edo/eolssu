package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent

/** 내 악보의 공통 악보 v1 처리. 제공 곡(CAT-04)과 같은 검증·타임라인 규칙을 쓴다. */
interface SheetScores {
    /** 악보 버전만 [scoreVersion]으로 바꾼 사본. 공통 악보 v1이 아니면 null. */
    fun copy(scoreJson: String, scoreVersion: Int): CopiedScore?

    /** CAT-04 조회용 타임라인. 저장 악보의 구조·음표 수·버전·단위가 맞지 않으면 null. */
    fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String): List<ScoreTimelineEvent>?
}

data class CopiedScore(val json: String, val columnCount: Int, val noteCount: Int)
