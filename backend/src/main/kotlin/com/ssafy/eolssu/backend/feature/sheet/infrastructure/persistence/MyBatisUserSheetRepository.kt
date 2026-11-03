package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.sheet.domain.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.dao.DuplicateKeyException

class MyBatisUserSheetRepository(private val mapper: UserSheetMapper) : UserSheetRepository {
    override fun findByIdempotencyKey(userId: Long, idempotencyKey: String) =
        mapper.findByIdempotencyKey(userId, idempotencyKey)?.toSheet()

    override fun findJangdan(jangdanId: Long) = mapper.findJangdan(jangdanId)?.let { SheetJangdan(it.id, it.bpmUnit, it.name) }

    override fun findSongSource(songId: Long) =
        mapper.findSongSource(songId)?.let {
            SongCopySource(it.jangdanId, it.bpmUnit, it.scoreJson, it.scorable, it.scoreVersion, it.noteCount, it.defaultBpm)
        }

    override fun find(sheetId: Long, userId: Long) = mapper.find(sheetId, userId)?.toSheet()

    // 트랜잭션 없이 한 문장으로 저장한다. 유일 제약에 진 요청이 이어서 하는 조회가 이긴 요청의 커밋된 행을 본다.
    override fun insert(sheet: NewUserSheet): Long? {
        val row = UserSheetInsert(
            sheet.userId, sheet.title, SHEET_KIND_JEONGGANBO, SHEET_SOURCE_MANUAL, sheet.status.name, sheet.jangdanId, sheet.bpm,
            sheet.bpmUnit, sheet.columnCount, sheet.scoreJson, sheet.noteCount, sheet.idempotencyKey, sheet.requestHash,
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

    // 확인 필요 칸이 아직 없어(변환 기능 전) 확정 저장한 악보는 모두 EVALUABLE이다.
    override fun updateScore(update: SheetScoreUpdate) = mapper.updateScore(
        SheetScoreUpdateRow(
            update.sheetId, update.userId, update.baseScoreVersion, update.title, SheetStatus.EVALUABLE.name, update.jangdanId, update.bpm,
            update.bpmUnit, update.columnCount, update.scoreJson, update.noteCount, LocalDateTime.ofInstant(update.updatedAt, ZoneOffset.UTC),
        ),
    ) == 1

    override fun updateDraft(update: SheetDraftUpdate) = mapper.updateDraft(
        SheetDraftUpdateRow(
            update.sheetId, update.userId, update.baseScoreVersion, update.draftRevision, update.draftJson,
            LocalDateTime.ofInstant(update.savedAt, ZoneOffset.UTC),
        ),
    ) == 1

    override fun delete(sheetId: Long, userId: Long) = mapper.delete(sheetId, userId) == 1

    private fun UserSheetRow.toSheet() = UserSheet(
        id, userId, title, kind, source, SheetStatus.valueOf(status), jangdanId, jangdanName, bpm, bpmUnit, columnCount, scoreVersion,
        scoreJson, noteCount, pendingCellCount, requestHash, updatedAt.toInstant(ZoneOffset.UTC),
        draftJson?.let { SheetDraft(it, checkNotNull(draftBaseVersion), checkNotNull(draftSavedAt).toInstant(ZoneOffset.UTC)) }, draftRevision,
    )
}
