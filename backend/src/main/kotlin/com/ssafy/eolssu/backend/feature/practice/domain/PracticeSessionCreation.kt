package com.ssafy.eolssu.backend.feature.practice.domain

import java.time.Instant

// 메트로놈 1클릭의 단위. bpm은 이 단위로 센 값이다(PRA-01).
enum class BpmUnit { QUARTER, DOTTED_QUARTER, JEONGGAN, JANGDAN_BEAT }

// 연습할 수 있는 곡(PUBLISHED·채점 가능·삭제 안 됨)의 세션 생성에 필요한 값
data class PracticeSong(val id: Long, val bpmUnit: BpmUnit, val noteCount: Int, val scoreVersion: Int)

data class ParentSessionCandidate(val id: Long, val userId: Long, val songId: Long, val status: String)

data class NewPracticeSession(
    val userId: Long,
    val songId: Long,
    val parentSessionId: Long?,
    val scoreVersion: Int,
    val bpm: Int,
    val bpmUnit: BpmUnit,
    val idempotencyKey: String,
    val requestHash: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val startedAt: Instant,
)

data class PracticeSession(
    val id: Long,
    val songId: Long,
    val status: String,
    val bpm: Int,
    val bpmUnit: BpmUnit,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val startedAt: Instant,
    val requestHash: String?,
)

interface PracticeSessionCreationRepository {
    fun findByIdempotencyKey(userId: Long, idempotencyKey: String): PracticeSession?
    fun findPlayableSong(songId: Long): PracticeSong?
    fun findSession(sessionId: Long): ParentSessionCandidate?

    /** 저장한 세션 ID. 같은 회원·키의 세션이 이미 있으면(유일 제약) null. */
    fun insert(session: NewPracticeSession): Long?
}
