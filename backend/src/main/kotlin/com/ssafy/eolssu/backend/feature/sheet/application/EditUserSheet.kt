package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetDraftUpdate
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetScoreUpdate
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import tools.jackson.core.io.JsonStringEncoder
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

data class SaveSheetScoreCommand(
    val userId: Long,
    val sheetId: Long,
    val baseScoreVersion: Int,
    /** null이면 저장된 제목을 그대로 둔다. */
    val title: String?,
    val jangdanId: Long?,
    val bpm: Int,
    /** 공통 악보 v1 문서 전체(JSON). */
    val scoreJson: String,
    val resolvedIssueIds: List<String>,
)

sealed interface SaveSheetScoreResult {
    data class Saved(val sheet: UserSheet, val events: List<ScoreTimelineEvent>) : SaveSheetScoreResult
    data object NotFound : SaveSheetScoreResult
    data object InvalidRequest : SaveSheetScoreResult
    data class UnfilledCells(val cellIds: List<String>) : SaveSheetScoreResult
    data object StateConflict : SaveSheetScoreResult
    data object TooLarge : SaveSheetScoreResult
}

data class SaveSheetDraftCommand(
    val userId: Long,
    val sheetId: Long,
    val baseScoreVersion: Int,
    val draftRevision: Int,
    val title: String?,
    val jangdanId: Long?,
    val bpm: Int,
    val scoreJson: String,
)

sealed interface SaveSheetDraftResult {
    data class Saved(val draftRevision: Int, val unfilledCellCount: Int, val savedAt: Instant) : SaveSheetDraftResult
    data object NotFound : SaveSheetDraftResult
    data object InvalidRequest : SaveSheetDraftResult
    data object StateConflict : SaveSheetDraftResult
    data object TooLarge : SaveSheetDraftResult
}

/** SHT-04 확정 저장, SHT-11 자동 저장, SHT-02 삭제(docs/specs/user-sheets.md '2차 결정'). */
class EditUserSheet(
    private val repository: UserSheetRepository,
    private val scores: SheetScores,
    private val clock: Clock,
    private val json: JsonMapper = JsonMapper.builder().build(),
) {
    fun save(command: SaveSheetScoreCommand): SaveSheetScoreResult {
        if (command.scoreJson.toByteArray().size > MAX_SCORE_BYTES) return SaveSheetScoreResult.TooLarge
        val title = command.title?.trim()
        if (title != null && !titleIsValid(title) || command.bpm !in BPM_RANGE) return SaveSheetScoreResult.InvalidRequest
        val sheet = repository.find(command.sheetId, command.userId) ?: return SaveSheetScoreResult.NotFound
        if (sheet.scoreVersion != command.baseScoreVersion) return SaveSheetScoreResult.StateConflict
        if(sheet.imported!=null && sheet.imported.conversionStatus!=com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportStatus.DONE) return SaveSheetScoreResult.StateConflict
        val details=sheet.imported?.resultJson?.let { json.readTree(it) as ObjectNode }
        val issues=details?.path("issues")?.toList() ?: emptyList()
        val ids=issues.map { it.path("issueId").asString() }.toSet()
        if(command.resolvedIssueIds.any { it !in ids }) return SaveSheetScoreResult.InvalidRequest
        val remaining=issues.filter { it.path("issueId").asString() !in command.resolvedIssueIds }
        val pending=remaining.map { it.path("cellId").takeIf { id -> id.isString }?.asString() ?: it.path("eventId").asString() }.toSet().size
        details?.putArray("issues")?.addAll(remaining)

        val version = command.baseScoreVersion + 1
        val prepared = when (val result = scores.prepare(command.scoreJson, version)) {
            is PreparedScore.Ready -> result
            is PreparedScore.Unfilled -> return SaveSheetScoreResult.UnfilledCells(result.cellIds)
            PreparedScore.Malformed -> return SaveSheetScoreResult.InvalidRequest
        }
        if (command.jangdanId != null) {
            val jangdan = repository.findJangdan(command.jangdanId) ?: return SaveSheetScoreResult.NotFound
            // 장단을 바꾸면 앱이 그 장단으로 칸과 tick을 다시 만든다. 악보가 다른 장단이면 정간 수가 맞지 않는다.
            if (prepared.jangdanName != jangdan.name || prepared.bpmUnit != jangdan.bpmUnit) return SaveSheetScoreResult.InvalidRequest
        }
        val events = scores.timeline(prepared.json, version, prepared.noteCount, command.bpm, prepared.bpmUnit)
            ?: return SaveSheetScoreResult.InvalidRequest

        val updated = repository.updateScore(
            SheetScoreUpdate(
                sheetId = sheet.id,
                userId = command.userId,
                baseScoreVersion = command.baseScoreVersion,
                title = title ?: sheet.title,
                jangdanId = command.jangdanId,
                bpm = command.bpm,
                bpmUnit = prepared.bpmUnit,
                columnCount = prepared.columnCount,
                scoreJson = prepared.json,
                noteCount = prepared.noteCount,
                updatedAt = now(),
                pendingCellCount = pending,
                conversionResultJson = details?.let(json::writeValueAsString),
            ),
        )
        // 읽은 뒤 다른 기기가 먼저 저장했다. 지웠으면 없는 악보다.
        if (!updated) return repository.find(command.sheetId, command.userId)?.let { SaveSheetScoreResult.StateConflict } ?: SaveSheetScoreResult.NotFound
        // 저장 직후 같은 회원이 지웠으면 돌려줄 악보가 없다.
        val saved = repository.find(command.sheetId, command.userId) ?: return SaveSheetScoreResult.NotFound
        return SaveSheetScoreResult.Saved(saved, events)
    }

    fun saveDraft(command: SaveSheetDraftCommand): SaveSheetDraftResult {
        if (command.scoreJson.toByteArray().size > MAX_SCORE_BYTES) return SaveSheetDraftResult.TooLarge
        val title = command.title?.trim()
        if (title != null && !titleIsValid(title) || command.bpm !in BPM_RANGE || command.draftRevision < 0) return SaveSheetDraftResult.InvalidRequest
        val unfilled = scores.unfilledCellCount(command.scoreJson) ?: return SaveSheetDraftResult.InvalidRequest
        val sheet = repository.find(command.sheetId, command.userId) ?: return SaveSheetDraftResult.NotFound
        if(sheet.imported!=null && sheet.imported.conversionStatus!=com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportStatus.DONE) return SaveSheetDraftResult.StateConflict
        if (sheet.scoreVersion != command.baseScoreVersion || sheet.draftRevision != command.draftRevision) return SaveSheetDraftResult.StateConflict

        val savedAt = now()
        val draft = """{"title":${quote(title ?: sheet.title)},"jangdanId":${command.jangdanId?.let { quote(it.toString()) } ?: "null"},""" +
            """"bpm":${command.bpm},"score":${command.scoreJson}}"""
        val updated = repository.updateDraft(SheetDraftUpdate(sheet.id, command.userId, command.baseScoreVersion, command.draftRevision, draft, savedAt))
        if (!updated) return repository.find(command.sheetId, command.userId)?.let { SaveSheetDraftResult.StateConflict } ?: SaveSheetDraftResult.NotFound
        return SaveSheetDraftResult.Saved(command.draftRevision + 1, unfilled, savedAt)
    }

    fun delete(userId: Long, sheetId: Long): Boolean = repository.delete(sheetId, userId)

    private fun titleIsValid(title: String) = title.codePointCount(0, title.length) in 1..MAX_TITLE_LENGTH

    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)

    private fun quote(value: String) = "\"" + String(JsonStringEncoder.getInstance().quoteAsCharArray(value)) + "\""

    companion object {
        /** 공통 악보 문서 상한(1MiB, UTF-8 바이트). */
        const val MAX_SCORE_BYTES = 1024 * 1024
        /** 제목 상한(앞뒤 공백을 자른 코드 포인트 수). */
        const val MAX_TITLE_LENGTH = 50
        private val BPM_RANGE = 30..160
    }
}
