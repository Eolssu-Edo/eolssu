package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.sheet.domain.*
import java.time.LocalDateTime
import java.time.ZoneOffset

class MyBatisUserSheetRepository(private val mapper: UserSheetMapper,manager: org.springframework.transaction.PlatformTransactionManager) : UserSheetRepository {
    private val transaction=org.springframework.transaction.support.TransactionTemplate(manager)
    override fun findJangdan(jangdanId: Long) = mapper.findJangdan(jangdanId)?.let { SheetJangdan(it.id, it.bpmUnit, it.name, it.pattern) }

    override fun find(sheetId: Long, userId: Long) = mapper.find(sheetId, userId)?.toSheet()

    override fun page(userId: Long, kind: String?, page: Int, size: Int) = SheetPage(
        mapper.findPage(userId, kind, page.toLong() * size, size).map { it.toSheet() },
        mapper.count(userId, kind),
    )

    override fun updateScore(update: SheetScoreUpdate): Boolean = transaction.execute {
        // Match conversion retry/completion: lock the conversion before its sheet.
        if (update.conversionResultJson != null && mapper.lockConversion(update.sheetId, update.userId) == null) {
            return@execute false
        }
        val changed=mapper.updateScore(
        SheetScoreUpdateRow(
            update.sheetId, update.userId, update.baseScoreVersion, update.title, if(update.pendingCellCount==0) SheetStatus.EVALUABLE.name else SheetStatus.NEEDS_REVIEW.name, update.jangdanId, update.bpm,
            update.bpmUnit, update.columnCount, update.scoreJson, update.noteCount, LocalDateTime.ofInstant(update.updatedAt, ZoneOffset.UTC),update.pendingCellCount, update.authoringJson,
        ),
        ) == 1
        if(changed && update.conversionResultJson!=null) mapper.updateConversionResult(update.sheetId,update.conversionResultJson)
        changed
    }==true

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
        conversionStatus?.let { SheetImportSource(SheetImportStatus.valueOf(it), checkNotNull(sourcePageCount), sourceFileName, hwangPitch,conversionResultJson) },
        authoringJson,
    )
}
