package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.sheet.domain.ImportJangdan
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_LIMIT_PER_MEMBER
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetConversion
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportRecord
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportReplay
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportRepository
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportSave
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetNotation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.support.TransactionTemplate

class MyBatisSheetImportRepository(
    private val mapper: SheetImportMapper,
    private val transaction: TransactionTemplate,
) : SheetImportRepository {
    override fun stopConversion(sheetId: Long,userId: Long) { mapper.stopConversion(sheetId,userId) }
    override fun findReplay(userId: Long, idempotencyKey: String) = mapper.findReplay(userId, idempotencyKey)?.toReplay()

    override fun blocker(userId: Long): SheetImportSave? = when {
        mapper.countSheets(userId) >= SHEET_LIMIT_PER_MEMBER -> SheetImportSave.LimitReached
        mapper.countActiveConversions(userId) > 0 -> SheetImportSave.InProgress
        else -> null
    }

    override fun instrumentIsActive(instrumentId: Long) = mapper.countActiveInstruments(instrumentId) > 0

    override fun jangdan(jangdanId: Long) = mapper.findJangdan(jangdanId)?.let { ImportJangdan(it.bpmUnit, it.defaultBpm) }

    override fun save(record: SheetImportRecord): SheetImportSave = checkNotNull(
        transaction.execute {
            checkNotNull(mapper.lockUser(record.userId)) { "Importing member does not exist" }
            mapper.findReplay(record.userId, record.idempotencyKey)?.let { return@execute SheetImportSave.Replayed(it.toReplay()) }
            blocker(record.userId)?.let { return@execute it }
            val acceptedAt = record.acceptedAt.utc()
            val sheet = NewImportSheetRow(
                record.userId, record.title, record.notation.source, record.jangdanId, record.sheetBpm, record.sheetBpmUnit,
                record.idempotencyKey, record.requestHash, acceptedAt,
            )
            mapper.insertSheet(sheet)
            val sheetId = checkNotNull(sheet.id)
            mapper.insertConversion(
                NewConversionRow(
                    sheetId, record.userId, record.notation.name, record.instrumentId, record.jangdanId, record.requestedBpm,
                    record.hwangPitch, record.pageCount, acceptedAt,
                ),
            )
            record.files.forEachIndexed { index, file ->
                mapper.insertSourceFile(NewSourceFileRow(sheetId, index, file.objectKey, file.type.contentType, file.byteSize, file.fileName))
            }
            SheetImportSave.Created(sheetId)
        },
    )

    override fun findConversion(sheetId: Long, userId: Long) = mapper.findConversion(sheetId, userId)?.let {
        SheetConversion(
            it.sheetId, SheetNotation.valueOf(it.notation), SheetImportStatus.valueOf(it.status), it.stage, it.pageCount,
            it.processedPageCount, it.jangdanId, it.bpm, it.failureCode, it.attempt, it.acceptedAt.instant(), it.startedAt?.instant(),
            it.finishedAt?.instant(), it.noteCount,it.failureCause,it.timeoutCount,it.pendingCellCount,
        )
    }

    override fun sourceKeys(sheetId: Long, userId: Long): List<String>? =
        mapper.findSourceKeys(sheetId, userId).takeIf { it.isNotEmpty() }?.mapNotNull { it.objectKey }

    private fun SheetImportReplayRow.toReplay() = SheetImportReplay(sheetId, requestHash)

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

    private fun LocalDateTime.instant() = toInstant(ZoneOffset.UTC)
}
