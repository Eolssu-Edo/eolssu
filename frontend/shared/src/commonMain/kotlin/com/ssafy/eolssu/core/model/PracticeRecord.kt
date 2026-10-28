package com.ssafy.eolssu.core.model

/**
 * 저장한 연습 기록 하나.
 *
 * @property section 구간 연습이면 '3–4각' 같은 구간 이름, 전체 연주면 null.
 * @property repeats 구간 연습의 반복 횟수. 전체 연주면 null.
 * @property recordedSeconds 녹음 길이(구간이면 한 번의 길이).
 */
data class PracticeRecord(
    val id: String,
    val songId: String,
    val songTitle: String,
    val section: String?,
    val repeats: Int?,
    val recordedAt: KoreanDateTime,
    val recordedSeconds: Int,
    val bpm: Int,
    val score: Int,
    val metrics: List<MetricScore>,
    val memo: String,
    val errors: List<ScoreError>,
)
