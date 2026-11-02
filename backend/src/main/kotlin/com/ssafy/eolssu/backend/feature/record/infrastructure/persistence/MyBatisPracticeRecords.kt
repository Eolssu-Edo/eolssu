package com.ssafy.eolssu.backend.feature.record.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import java.time.LocalDateTime

class MyBatisPracticeRecords(private val mapper: PracticeRecordMapper) : PracticeRecords {
    override fun lockStatus(sessionId: Long, userId: Long) = mapper.lockStatus(sessionId, userId)

    override fun findSession(sessionId: Long, userId: Long) = mapper.findSession(sessionId, userId)?.run {
        RecordSession(
            sessionId, songId, songTitle, noteCount, scoreVersion, startNoteIdx, endNoteIdx, bpm, bpmUnit, status, startedAt,
            recordingDurationMs, totalScore, savedAt, memo,
        )
    }

    override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange) {
        when (memo) {
            MemoChange.Keep -> mapper.save(sessionId, userId, savedAt, false, null)
            MemoChange.Clear -> mapper.save(sessionId, userId, savedAt, true, null)
            is MemoChange.Replace -> mapper.save(sessionId, userId, savedAt, true, memo.memo)
        }
    }
}
