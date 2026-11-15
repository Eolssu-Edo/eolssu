package com.ssafy.eolssu.core.model

/** 평가 항목. 와이어프레임 API 정리의 평가 항목 키 pitch·rhythm·length와 같다. */
enum class EvaluationMetric { Pitch, Rhythm, Length }

/** 항목 점수(0~100 정수). */
data class MetricScore(val metric: EvaluationMetric, val score: Int)

/** 음별 판정. 음 미검출은 합산 점수의 음 길이 항목과 별개다. */
enum class ScoreErrorKind {
    Pitch, Rhythm, Length, Missed;

    companion object {
        /**
         * 서버 판정의 틀린 종류. 정상 판정과 이 앱이 모르는 판정은 null이다.
         *
         * 모르는 판정은 예외를 던지면 결과 화면 전체가 열리지 않으므로, 그 음만 표시하지 않고 나머지를 보여 준다.
         * 서버에 새 판정이 생겨도 이미 설치된 앱이 멈추지 않게 하려는 것이다(S15P31E102-912, 배포 순서 앱 → 백엔드 → AI).
         */
        fun fromVerdict(verdict: String): ScoreErrorKind? = when (verdict) {
            "PITCH" -> Pitch
            "TIMING" -> Rhythm
            "LENGTH" -> Length
            "MISSED" -> Missed
            else -> null
        }
    }
}

/** 틀린 정간 하나. 정간보의 각·정간 위치와 서버 판정 종류. */
data class ScoreError(val column: Int, val row: Int, val kind: ScoreErrorKind) {
    constructor(column: Int, row: Int, metric: EvaluationMetric) : this(
        column, row, when (metric) {
            EvaluationMetric.Pitch -> ScoreErrorKind.Pitch
            EvaluationMetric.Rhythm -> ScoreErrorKind.Rhythm
            EvaluationMetric.Length -> ScoreErrorKind.Length
        },
    )
}

/**
 * 연주 평가 결과. 점수 단위·항목 키는 서버 결과를 그대로 쓰고 화면에서 다시 계산하지 않는다.
 *
 * @property feedback 총평 한 줄(서버가 준다).
 * @property sample 예시 결과인지. 실제 분석이 붙기 전에는 true로 화면에 '예시 결과'를 표시한다.
 * @property eventErrors 악보 연주 순서([ScoreEvent.index]) → 그 음의 틀린 종류. 한 정간을 나눈 소박마다 오류를 보여줄 때 쓴다.
 *   서버 판정에서만 채우고, 예시 결과는 비어 있어 정간 단위 [errors]로 대신한다.
 */
data class EvaluationResult(
    val id: String,
    val songId: String,
    val total: Int,
    val feedback: String,
    val metrics: List<MetricScore>,
    val errors: List<ScoreError>,
    val bpm: Int,
    val recordedSeconds: Int,
    val recordedAt: KoreanDateTime,
    val sample: Boolean,
    /** 서버 결과의 악보 버전. 예시 fixture에서만 null을 허용한다. */
    val scoreVersion: Int? = null,
    val eventErrors: Map<Int, List<ScoreErrorKind>> = emptyMap(),
    /** 악보를 받기 전에도 보관하는 서버 음 판정. */
    val notes: List<EvaluationNote> = emptyList(),
) {
    /** 점수는 유지하고 정확한 판본에만 정간·소박 오류를 매핑한다. */
    fun withScore(song: Song): EvaluationResult {
        require(song.id == songId && (scoreVersion == null || song.scoreVersion == scoreVersion))
        if (notes.isEmpty()) return this
        val events = song.timeline.filter { it.noteIndex != null }.associateBy { it.noteIndex }
        val mapped = notes.mapNotNull { note ->
            val event = checkNotNull(events[note.noteIndex]) { "평가 음이 악보에 없음" }
            ScoreErrorKind.fromVerdict(note.verdict)?.let { event to it }
        }
        return copy(
            errors = mapped.map { (event, kind) -> ScoreError(event.column, event.row, kind) }.distinct(),
            eventErrors = mapped.groupBy({ it.first.index }, { it.second }).mapValues { (_, kinds) -> kinds.distinct().sortedBy { it.ordinal } },
        )
    }

    /**
     * 틀린 정간([errors])이 가장 많이 몰린 이웃한 두 각(0부터)과 그 수. 틀린 정간이 없으면 null.
     * 연주 결과에서 처음 고를 정간을 정할 때만 쓰고 수는 화면에 보이지 않는다.
     * 화면의 '틀린 부분' 개수는 소박·음 단위로 센다(`core/ui/score`의 `errorCount`).
     */
    fun densestColumnPair(): Pair<Int, Int>? {
        if (errors.isEmpty()) return null
        val byColumn = errors.groupingBy { it.column }.eachCount()
        val last = byColumn.keys.max()
        return (0..maxOf(0, last - 1))
            .map { c -> c to (byColumn[c] ?: 0) + (byColumn[c + 1] ?: 0) }
            .maxBy { it.second }
    }
}

/** 음 인덱스는 정간 좌표가 아니며, 해당 판본의 시간축에서 해석한다. */
data class EvaluationNote(val noteIndex: Int, val verdict: String)

/**
 * 분석 실패 사유. 서버 `fail_reason` 코드와 같다(PRA-03 스펙).
 *
 * @property retryable 같은 녹음으로 '분석 다시 시도'할 수 있는지. 아니면 '다시 연주하기'로 새로 녹음한다.
 */
enum class AnalysisFailure(val code: String, val retryable: Boolean) {
    NoSound("NO_SOUND", false),
    TooShort("TOO_SHORT", false),
    DecodeError("DECODE_ERROR", false),
    UploadTimeout("UPLOAD_TIMEOUT", false),
    Timeout("TIMEOUT", true),
    ServerError("SERVER_ERROR", true);

    companion object {
        /** 서버 코드에 맞는 사유. 모르는 코드나 빈 값은 [ServerError]로 본다. */
        fun fromCode(code: String?): AnalysisFailure = entries.firstOrNull { it.code == code } ?: ServerError
    }
}
