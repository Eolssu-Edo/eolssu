package com.ssafy.eolssu.core.model

/**
 * 저장한 연습 기록 하나.
 *
 * @property section 구간 연습이면 '3–4각' 같은 구간 이름, 전체 연주면 null.
 * @property repeats 구간 연습의 반복 횟수. 전체 연주면 null.
 * @property recordedSeconds 녹음 길이(구간이면 한 번의 길이).
 * @property metrics 항목 점수. 서버 목록(REC-02)에는 없어 비어 있고, 기록 상세에서 평가(EVA-01)로 채운다.
 * @property errors 오류 위치. [metrics]와 같이 기록 상세에서 채운다.
 * @property recordingAvailable 녹음을 다시 들을 수 있는지(REC-02·PRA-03 `recordingAvailable`).
 * @property isNew 저장한 뒤 기록 상세를 아직 열지 않았는지(REC-02 `isNew`).
 * @property recordedAtMillis 연주 순서 비교에 쓰는 시작 시각. 분 단위 시각만 있는 샘플은 그 분의 시작을 쓴다.
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
    val recordingAvailable: Boolean = false,
    val isNew: Boolean = false,
    val recordedAtMillis: Long = ((recordedAt.epochDay * 24 + recordedAt.hour - 9) * 60 + recordedAt.minute) * 60_000,
)
