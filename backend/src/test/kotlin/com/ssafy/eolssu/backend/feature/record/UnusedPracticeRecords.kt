package com.ssafy.eolssu.backend.feature.record

import com.ssafy.eolssu.backend.feature.record.domain.DeletableRecord
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.RecordingFile
import com.ssafy.eolssu.backend.feature.record.domain.SongRecordSummary
import java.time.LocalDateTime

/** 기록 저장소 대역의 바탕. 테스트는 쓰는 메서드만 덮어쓰고, 나머지를 부르면 실패한다. */
open class UnusedPracticeRecords : PracticeRecords {
    override fun savedRecordWeek(userId: Long, from: java.time.LocalDateTime, until: java.time.LocalDateTime): Nothing = error("사용하지 않는 주간 기록 조회")
    override fun lockStatus(sessionId: Long, userId: Long): String? = error("not used")

    override fun findSession(sessionId: Long, userId: Long): RecordSession? = error("not used")

    override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange): Unit = error("not used")

    override fun songSummaries(userId: Long): List<SongRecordSummary> = error("not used")

    override fun latestSavedRecord(userId: Long): RecordSession? = error("not used")

    override fun songRecords(userId: Long, songId: Long, sort: RecordSort, offset: Long, limit: Int): List<RecordSession> = error("not used")

    override fun findRecording(sessionId: Long, userId: Long): RecordingFile? = error("not used")

    override fun findDeletable(sessionId: Long, userId: Long): DeletableRecord? = error("not used")

    override fun markDeleted(sessionId: Long, userId: Long, deletedAt: LocalDateTime): Boolean = error("not used")
}
