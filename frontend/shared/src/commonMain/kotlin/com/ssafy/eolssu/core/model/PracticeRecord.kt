package com.ssafy.eolssu.core.model

/**
 * 저장한 연습 기록 하나.
 *
 * @property section 구간 연습이면 '3–4각' 같은 구간 이름, 전체 연주면 null.
 * @property repeats 구간 연습의 반복 횟수. 전체 연주면 null.
 * @property recordedSeconds 녹음 길이(구간이면 한 번의 길이).
 * @property metrics 항목 점수. 서버 목록(REC-02)에는 없어 비어 있고, 기록 상세에서 평가(EVA-01)로 채운다.
 * @property errors 오류 위치. [metrics]와 같이 기록 상세에서 채운다.
 * @property eventErrors 음(연주 순서)마다의 틀린 종류(평가의 [EvaluationResult.eventErrors]). 나뉜 정간의 소박만 칠할 때 쓰고, 없으면 정간 단위로 칠한다.
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
    val eventErrors: Map<Int, List<ScoreErrorKind>> = emptyMap(),
    val notes: List<EvaluationNote> = emptyList(),
    val scoreVersion: Int? = null,
    val startNoteIndex: Int? = null,
    val endNoteIndex: Int? = null,
    val recordingAvailable: Boolean = false,
    val isNew: Boolean = false,
    val recordedAtMillis: Long = ((recordedAt.epochDay * 24 + recordedAt.hour - 9) * 60 + recordedAt.minute) * 60_000,
) {
    /** 악보가 준비된 경우에만 원본 판정과 구간 표기를 복구한다. */
    fun withScore(song: Song): PracticeRecord {
        val mapped = EvaluationResult(id, songId, score, "", metrics, errors, bpm, recordedSeconds,
            recordedAt, sample = false, scoreVersion = scoreVersion, eventErrors = eventErrors, notes = notes).withScore(song)
        val pitches = song.timeline.filter { it.noteIndex != null }
        val first = pitches.firstOrNull { it.noteIndex == startNoteIndex }
        val last = pitches.firstOrNull { it.noteIndex == endNoteIndex }
        val loadedSection = if (startNoteIndex != null && endNoteIndex != null && first != null && last != null) {
            when {
                first == pitches.first() && last == pitches.last() -> null
                first.column == last.column -> "${first.column + 1}각"
                else -> "${first.column + 1}–${last.column + 1}각"
            }
        } else section
        return copy(errors = mapped.errors, eventErrors = mapped.eventErrors, section = loadedSection)
    }
}
