package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.domain.ColumnRange
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import com.ssafy.eolssu.backend.feature.record.domain.rangeOf
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.annotation.Transactional

/**
 * 연습 기록 저장·메모 수정(REC-01). 분석이 끝난(DONE) 세션만 저장한다. 다시 보내도 처음 저장 시각은 그대로다.
 * 같은 세션의 기록 삭제(REC-04)와 엇갈리지 않도록 세션 행을 잠그고 판단한다.
 */
open class SavePracticeRecord(
    private val records: PracticeRecords,
    private val columns: ScoreColumns,
    private val clock: Clock,
) {
    @Transactional
    open fun execute(userId: Long, sessionId: Long, memo: MemoChange): SaveRecordOutcome {
        val status = records.lockStatus(sessionId, userId) ?: return SaveRecordOutcome.NotFound
        if (status != "DONE") return SaveRecordOutcome.NotDone
        records.save(sessionId, userId, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), memo)
        // 잠근 행을 이 트랜잭션이 바꿨으므로 다시 읽으면 저장한 값이 보인다.
        val saved = checkNotNull(records.findSession(sessionId, userId)) { "Locked practice session disappeared" }
        return SaveRecordOutcome.Saved(saved, columns.rangeOf(saved))
    }
}

sealed interface SaveRecordOutcome {
    /** 없거나 다른 회원의 세션, 기록을 지운 세션. */
    data object NotFound : SaveRecordOutcome

    /** 분석이 끝나지 않아 저장할 수 없다. */
    data object NotDone : SaveRecordOutcome

    data class Saved(val record: RecordSession, val columns: ColumnRange?) : SaveRecordOutcome
}
