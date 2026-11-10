package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.sheet.application.PublishedSheetCopy
import com.ssafy.eolssu.backend.feature.sheet.application.PublishedSheetCopyRepository
import com.ssafy.eolssu.backend.feature.sheet.application.PublishedSheetSource
import com.ssafy.eolssu.backend.feature.sheet.application.SheetCopyFailure
import com.ssafy.eolssu.backend.feature.sheet.application.SheetCopyRequest
import com.ssafy.eolssu.backend.feature.sheet.application.SheetCopyResult
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_LIMIT_PER_MEMBER
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongScoreMapper
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.transaction.support.TransactionTemplate

class MyBatisPublishedSheetCopyRepository(
    private val songs: SongScoreMapper,
    private val sheets: UserSheetMapper,
    private val imports: SheetImportMapper,
    private val transaction: TransactionTemplate,
) : PublishedSheetCopyRepository {
    override fun replay(request: SheetCopyRequest): SheetCopyResult? =
        imports.findReplay(request.memberId, request.key)?.let {
            if (it.requestHash == request.requestHash) SheetCopyResult.Created(it.sheetId)
            else SheetCopyResult.Rejected(SheetCopyFailure.IdempotencyConflict)
        }

    override fun findSource(songId: Long): PublishedSheetSource? = songs.findPublished(songId)?.let {
        PublishedSheetSource(it.title, it.jangdanId, it.defaultBpm, it.bpmUnit, it.scoreVersion, it.scoreJson, it.noteCount)
    }

    override fun save(copy: PublishedSheetCopy): SheetCopyResult = checkNotNull(transaction.execute {
        val request = copy.request
        // 파일 접수와 같은 잠금으로 회원당 악보 한도를 함께 지킨다.
        if (imports.lockUser(request.memberId) == null) return@execute SheetCopyResult.Rejected(SheetCopyFailure.NotFound)
        replay(request)?.let { return@execute it }
        if (imports.countSheets(request.memberId) >= SHEET_LIMIT_PER_MEMBER) {
            return@execute SheetCopyResult.Rejected(SheetCopyFailure.Limit)
        }
        if (imports.countActiveConversions(request.memberId) > 0) {
            return@execute SheetCopyResult.Rejected(SheetCopyFailure.ConversionInProgress)
        }
        val row = NewPublishedSheetCopyRow(
            request.memberId, copy.title, copy.jangdanId, copy.bpm, copy.score.bpmUnit, copy.score.columnCount,
            copy.score.json, copy.score.noteCount, request.key, request.requestHash, LocalDateTime.ofInstant(copy.createdAt, ZoneOffset.UTC),
        )
        sheets.insertPublishedCopy(row)
        SheetCopyResult.Created(checkNotNull(row.id))
    })
}
