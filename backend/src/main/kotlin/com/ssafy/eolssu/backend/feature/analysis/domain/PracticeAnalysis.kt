package com.ssafy.eolssu.backend.feature.analysis.domain

import java.time.Duration
import java.time.LocalDateTime

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

/**
 * 연습 구간의 정답 음표 하나. `idx`는 `songs.score_json`에서 `NOTE`만 센 순번이고 `pitchId`는 `note_results.expected_pitch_id`로 저장한다.
 * `startBeat`는 곡 처음부터 센 시작 박이다. 쉼은 음표로 보내지 않으므로 AI는 `beats`가 아니라 이 값으로 기대 시각을 정한다.
 */
data class ScoreNote(
    val idx: Int,
    val semitone: Int,
    val startBeat: Double,
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

/**
 * `detectedSemitone`은 조율 보정을 적용한 뒤 가장 가까운 반음(`pitches.semitone` 기준)이고,
 * `recordingOffsetMs`는 업로드 원본 녹음의 재생 0ms부터 잰 음표 시작 시각이다. 모르는 AI 응답이거나 `MISSED`면 null이다.
 */
data class NoteJudgement(
    val idx: Int,
    val verdict: NoteVerdict,
    val pitchErrorCent: Double?,
    val onsetErrorMs: Int?,
    val durationErrorMs: Int?,
    val detectedSemitone: Int? = null,
    val recordingOffsetMs: Int? = null,
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
        val tuningOffsetCent: Double? = null,
        val tuningOutOfRange: Boolean? = null,
    ) : AnalysisResult

    data class Failed(val reason: AnalysisFailReason) : AnalysisResult
}

fun interface AnalysisClient {
    fun analyze(request: AnalysisRequest): AnalysisResult
}

/** AI 응답이 계약(PRA-03 '분석 요청과 상태 조회 계약')에 맞지 않는다. 분석 흐름은 `SERVER_ERROR`로 기록한다. */
class AnalysisResponseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 분석 시도 제한 시간 기본값(PRA-03). 처리 시간을 서버에서 다시 잰 뒤 설정으로 바꾼다. */
val DEFAULT_ANALYSIS_TIME_LIMIT: Duration = Duration.ofSeconds(60)

/** 백엔드가 정하는 실패 코드. AI가 주는 코드는 [AnalysisFailReason]. */
const val SERVER_ERROR = "SERVER_ERROR"
const val TIMEOUT = "TIMEOUT"

/** 세션의 곡·연습 구간을 음표로 푼다. 풀 수 없으면 예외를 던지고 분석은 `SERVER_ERROR`로 끝난다. */
fun interface PracticeScoreNotes {
    fun notesFor(analysis: PendingAnalysis): List<ScoreNote>
}

/** AI가 녹음 원본을 읽을 15분짜리 서명 URL을 만든다. 저장소 연결(S15P31E102-181)에서 구현한다. */
fun interface RecordingReadUrls {
    fun readUrlFor(analysis: PendingAnalysis): String
}

/**
 * 시각은 UTC `LocalDateTime`이다. `startedAfter`는 마감 기준이다: 시도 시작 시각이 이 값 이상이어야(제한 시간 안) 조회·저장한다.
 */
interface PracticeAnalysisRepository {
    fun findPending(sessionId: Long, startedAfter: LocalDateTime): PendingAnalysis?

    /** 세션이 아직 같은 시도의 마감 전 `PENDING`이면 평가·음표별 결과를 저장하고 `DONE`으로 바꾼다. 아니면 아무것도 저장하지 않고 false. */
    fun saveDone(analysis: PendingAnalysis, result: AnalysisResult.Done, notes: List<ScoreNote>, startedAfter: LocalDateTime): Boolean

    /** 세션이 아직 같은 시도의 마감 전 `PENDING`이면 `FAILED`와 실패 코드로 바꾼다. 아니면 false. */
    fun saveFailed(analysis: PendingAnalysis, failReason: String, startedAfter: LocalDateTime): Boolean

    /** 시도가 `startedAfter`보다 먼저 시작한 `PENDING`이면 `FAILED`·`TIMEOUT`으로 바꾼다. 바꿨으면 true. */
    fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime): Boolean
}

/** 연습 세션의 분석 상태. `failReason`은 `FAILED`일 때만 있고 `attempt`는 현재 분석 시도 번호다(0은 시작 전). */
data class AnalysisState(val status: String, val failReason: String?, val attempt: Int)

/** 같은 녹음으로 다시 분석할 수 있는 실패 코드(PRA-03 '분석 다시 시도'). 나머지는 새로 녹음해야 하거나 이미 끝났다. */
val RETRYABLE_FAIL_REASONS = setOf(TIMEOUT, SERVER_ERROR)

/** 분석 다시 시도(S15P31E102-175). 세션은 본인 것일 때만 찾는다. 시각은 UTC `LocalDateTime`이다. */
interface AnalysisRetryRepository {
    fun findState(sessionId: Long, userId: Long): AnalysisState?

    /**
     * 본인 세션이 아직 `attempt`번째 시도이고 녹음이 있는 `FAILED`이며 실패 코드가 [RETRYABLE_FAIL_REASONS]이면
     * 시도 번호를 `attempt + 1`로 올리고 `startedAt`에 시작한 `PENDING`으로 바꾼다. 조건부 갱신이라 동시 요청 중 하나만 true다.
     */
    fun startRetry(sessionId: Long, userId: Long, attempt: Int, startedAt: LocalDateTime): Boolean

    /** 방금 시작한 `attempt`번째 시도(`startedAt`)를 분석에 넘기지 못했을 때 `SERVER_ERROR`로 끝낸다. 이미 끝났으면 false. */
    fun failStart(sessionId: Long, attempt: Int, startedAt: LocalDateTime): Boolean
}
