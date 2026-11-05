package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.sheet.domain.*
import java.time.LocalDateTime
import java.time.ZoneOffset

class MyBatisUserSheetRepository(private val mapper: UserSheetMapper) : UserSheetRepository {
    override fun findJangdan(jangdanId: Long) = mapper.findJangdan(jangdanId)?.let { SheetJangdan(it.id, it.bpmUnit, it.name) }

    override fun find(sheetId: Long, userId: Long) = mapper.find(sheetId, userId)?.toSheet()

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
        scoreJson, noteCount, pendingCellCount, updatedAt.toInstant(ZoneOffset.UTC),
        draftJson?.let { SheetDraft(it, checkNotNull(draftBaseVersion), checkNotNull(draftSavedAt).toInstant(ZoneOffset.UTC)) }, draftRevision,
        conversionStatus?.let { SheetImportSource(SheetImportStatus.valueOf(it), checkNotNull(sourcePageCount), sourceFileName, hwangPitch) },
    )
}
