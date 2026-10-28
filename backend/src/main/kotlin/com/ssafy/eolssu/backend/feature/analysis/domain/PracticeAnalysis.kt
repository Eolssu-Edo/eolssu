package com.ssafy.eolssu.backend.feature.analysis.domain

/** 분석을 기다리는(`PENDING`) 연습 세션의 현재 시도. 결과는 이 시도 번호일 때만 저장한다(PRA-03). */
data class PendingAnalysis(
    val sessionId: Long,
    val attempt: Int,
    val songId: Long,
    val scoreVersion: Int,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val bpm: Int,
    val bpmUnit: String,
    val recordingUrl: String,
)

/** 연습 구간의 정답 음표 하나. `idx`는 `songs.score_json` 기준 순번이고 `pitchId`는 `note_results.expected_pitch_id`로 저장한다. */
data class ScoreNote(
    val idx: Int,
    val semitone: Int,
    val beats: Double,
    val sigimsae: Boolean,
    val pitchId: Long,
)

/** AI 서비스에 보내는 분석 요청. `audioUrl`은 녹음 접근 토큰이라 로그에 남기지 않는다. */
data class AnalysisRequest(
    val sessionId: Long,
    val attempt: Int,
    val audioUrl: String,
    val bpm: Int,
    val bpmUnit: String,
    val notes: List<ScoreNote>,
) {
    override fun toString() = "AnalysisRequest(sessionId=$sessionId, attempt=$attempt, notes=${notes.size})"
}

enum class NoteVerdict { CORRECT, PITCH, TIMING, MISSED }

data class NoteJudgement(
    val idx: Int,
    val verdict: NoteVerdict,
    val pitchErrorCent: Double?,
    val onsetErrorMs: Int?,
    val durationErrorMs: Int?,
)

/** AI가 돌려주는 실패 사유. 시간 초과·연결 실패는 백엔드가 정한다(S15P31E102-174). */
enum class AnalysisFailReason { NO_SOUND, TOO_SHORT, DECODE_ERROR }

sealed interface AnalysisResult {
    data class Done(
        val modelVersion: String,
        val total: Int,
        val pitch: Int,
        val timing: Int,
        val duration: Int,
        val notes: List<NoteJudgement>,
    ) : AnalysisResult

    data class Failed(val reason: AnalysisFailReason) : AnalysisResult
}

fun interface AnalysisClient {
    fun analyze(request: AnalysisRequest): AnalysisResult
}

/** AI 응답이 계약(PRA-03 '분석 요청과 상태 조회 계약')에 맞지 않는다. S15P31E102-174에서 `SERVER_ERROR`로 바꾼다. */
class AnalysisResponseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 세션의 곡·연습 구간을 음표로 푼다. `songs.score_json` 형식(공통 악보 데이터)이 정해지면 구현한다. */
fun interface PracticeScoreNotes {
    fun notesFor(analysis: PendingAnalysis): List<ScoreNote>
}

/** AI가 녹음 원본을 읽을 15분짜리 서명 URL을 만든다. 저장소 연결(S15P31E102-181)에서 구현한다. */
fun interface RecordingReadUrls {
    fun readUrlFor(analysis: PendingAnalysis): String
}

interface PracticeAnalysisRepository {
    fun findPending(sessionId: Long): PendingAnalysis?

    /** 세션이 아직 같은 시도의 `PENDING`이면 평가·음표별 결과를 저장하고 `DONE`으로 바꾼다. 아니면 아무것도 저장하지 않고 false. */
    fun saveDone(analysis: PendingAnalysis, result: AnalysisResult.Done, notes: List<ScoreNote>): Boolean

    /** 세션이 아직 같은 시도의 `PENDING`이면 `FAILED`와 실패 코드로 바꾼다. 아니면 false. */
    fun saveFailed(analysis: PendingAnalysis, reason: AnalysisFailReason): Boolean
}
