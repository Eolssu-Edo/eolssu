package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.sheet.domain.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.dao.DuplicateKeyException

class MyBatisUserSheetRepository(private val mapper: UserSheetMapper) : UserSheetRepository {
    override fun findByIdempotencyKey(userId: Long, idempotencyKey: String) =
        mapper.findByIdempotencyKey(userId, idempotencyKey)?.toSheet()

    override fun findJangdan(jangdanId: Long) = mapper.findJangdan(jangdanId)?.let { SheetJangdan(it.id, it.bpmUnit) }

    override fun findSongSource(songId: Long) =
        mapper.findSongSource(songId)?.let { SongCopySource(it.jangdanId, it.bpmUnit, it.scoreJson, it.scorable) }

    override fun find(sheetId: Long, userId: Long) = mapper.find(sheetId, userId)?.toSheet()

    // 트랜잭션 없이 한 문장으로 저장한다. 유일 제약에 진 요청이 이어서 하는 조회가 이긴 요청의 커밋된 행을 본다.
    override fun insert(sheet: NewUserSheet): Long? {
        val row = UserSheetInsert(
            sheet.userId, sheet.title, SHEET_KIND_JEONGGANBO, SHEET_SOURCE_MANUAL, sheet.status.name, sheet.jangdanId, sheet.bpm,
            sheet.columnCount, sheet.scoreJson, sheet.noteCount, sheet.idempotencyKey, sheet.requestHash,
            LocalDateTime.ofInstant(sheet.createdAt, ZoneOffset.UTC),
        )
        try {
            check(mapper.insert(row) == 1)
        } catch (_: DuplicateKeyException) {
            // user_sheets의 유일 제약은 (user_id, idempotency_key) 하나뿐이다.
            return null
        }
        check(row.id > 0)
        return row.id
    }

    override fun page(userId: Long, kind: String?, page: Int, size: Int) = SheetPage(
        mapper.findPage(userId, kind, page.toLong() * size, size).map { it.toSheet() },
        mapper.count(userId, kind),
    )

    private fun UserSheetRow.toSheet() = UserSheet(
        id, userId, title, kind, source, SheetStatus.valueOf(status), jangdanId, jangdanName, bpm, bpmUnit, columnCount, scoreVersion,
        scoreJson, noteCount, pendingCellCount, requestHash, updatedAt.toInstant(ZoneOffset.UTC),
    )
}
