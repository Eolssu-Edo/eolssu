package com.ssafy.eolssu.backend.feature.growth.domain

/** 곡마다 전체 곡 연주의 최고 총점(docs/specs/practice-time.md '성취도'). */
data class SongBest(val songId: Long, val title: String, val bestTotalScore: Int)

/** 평가가 있는 전체 곡 연주 한 번. 항목 성장은 곡·악보 버전·채점 모델·BPM이 같은 연주끼리 비교한다. */
data class FullSongPlay(
    val sessionId: Long,
    val songId: Long,
    val title: String,
    val scoreVersion: Int,
    val modelVersion: String,
    val bpm: Int,
    val bpmUnit: String,
    val pitchScore: Int,
    val timingScore: Int,
    val durationScore: Int,
)
