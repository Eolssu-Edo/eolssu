package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.practice.domain.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.dao.DuplicateKeyException

class MyBatisPracticeSessionCreationRepository(private val mapper: PracticeSessionMapper) : PracticeSessionCreationRepository {
    override fun findByIdempotencyKey(userId: Long, idempotencyKey: String) =
        mapper.findByIdempotencyKey(userId, idempotencyKey)?.toSession()

    override fun findPlayableSong(songId: Long) =
        mapper.findPlayableSong(songId)?.let { PracticeSong(it.id, BpmUnit.valueOf(it.bpmUnit), it.noteCount, it.scoreVersion) }

    override fun findSession(sessionId: Long) =
        mapper.findById(sessionId)?.let { ParentSessionCandidate(it.id, it.userId, it.songId, it.status) }

    // 트랜잭션 없이 한 문장으로 저장한다. 유일 제약에 진 요청이 이어서 하는 조회가 이긴 요청의 커밋된 행을 본다.
    override fun insert(session: NewPracticeSession): Long? {
        val row = PracticeSessionInsert(
            session.userId, session.songId, session.parentSessionId, session.scoreVersion, session.bpm, session.bpmUnit.name,
            session.idempotencyKey, session.requestHash, session.startNoteIdx, session.endNoteIdx,
            LocalDateTime.ofInstant(session.startedAt, ZoneOffset.UTC),
        )
        try {
            check(mapper.insert(row) == 1)
        } catch (_: DuplicateKeyException) {
            // practice_sessions의 유일 제약은 (user_id, idempotency_key) 하나뿐이다.
            return null
        }
        check(row.id > 0)
        return row.id
    }

    private fun PracticeSessionRow.toSession() = PracticeSession(
        id, songId, status, bpm.toInt(), BpmUnit.valueOf(bpmUnit), startNoteIdx, endNoteIdx, startedAt.toInstant(ZoneOffset.UTC), requestHash,
    )
}
