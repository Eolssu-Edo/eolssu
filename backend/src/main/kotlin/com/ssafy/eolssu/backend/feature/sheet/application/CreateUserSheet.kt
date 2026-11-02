package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.NewUserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import java.security.MessageDigest
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.HexFormat

enum class SheetStartType { BLANK, COPY, PDF }

data class CreateUserSheetCommand(
    val userId: Long,
    val idempotencyKey: String,
    val title: String,
    val startType: SheetStartType,
    val jangdanId: Long?,
    val bpm: Int,
    val columnCount: Int?,
    val copyFromSongId: Long? = null,
    val copyFromSheetId: Long? = null,
    val pdfSheetId: Long? = null,
)

sealed interface CreateUserSheetResult {
    data class Created(val sheet: UserSheet, val events: List<ScoreTimelineEvent>) : CreateUserSheetResult
    data object NotFound : CreateUserSheetResult
    data object InvalidRequest : CreateUserSheetResult
    data object StateConflict : CreateUserSheetResult
    data object IdempotencyConflict : CreateUserSheetResult
}

/** SHT-03 새 정간보 악보 만들기. 빈 악보와 제공 곡·내 악보 복사만 만든다(docs/specs/user-sheets.md). */
class CreateUserSheet(
    private val repository: UserSheetRepository,
    private val scores: SheetScores,
    private val clock: Clock,
) {
    fun execute(command: CreateUserSheetCommand): CreateUserSheetResult {
        val title = command.title.trim()
        if (title.codePointCount(0, title.length) !in 1..MAX_TITLE_LENGTH) return CreateUserSheetResult.InvalidRequest
        if (!shapeIsValid(command)) return CreateUserSheetResult.InvalidRequest
        val hash = requestHash(command.copy(title = title))
        // 같은 키의 재전송은 처음 만든 악보를 돌려준다(SHT-03 규칙 2).
        repository.findByIdempotencyKey(command.userId, command.idempotencyKey)?.let { return replay(it, hash) }

        val draft = when (command.startType) {
            SheetStartType.BLANK -> blank(command) ?: return CreateUserSheetResult.NotFound
            SheetStartType.COPY -> when (val copied = copy(command)) {
                is Draft -> copied
                is Rejected -> return copied.result
            }
            SheetStartType.PDF -> return CreateUserSheetResult.InvalidRequest
        }
        val events = draft.scoreJson?.let { scores.timeline(it, FIRST_VERSION, draft.noteCount, command.bpm, draft.bpmUnit) ?: return CreateUserSheetResult.StateConflict }
            ?: emptyList()
        val sheet = NewUserSheet(
            userId = command.userId,
            title = title,
            status = draft.status,
            jangdanId = draft.jangdanId,
            bpm = command.bpm,
            columnCount = draft.columnCount,
            scoreJson = draft.scoreJson,
            noteCount = draft.noteCount,
            idempotencyKey = command.idempotencyKey,
            requestHash = hash,
            createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
        )
        val id = repository.insert(sheet)
            // 같은 키의 동시 요청이 먼저 저장했다. 그 결과로 같은 판정을 한다.
            ?: return replay(checkNotNull(repository.findByIdempotencyKey(command.userId, command.idempotencyKey)), hash)
        return CreateUserSheetResult.Created(checkNotNull(repository.find(id, command.userId)), events)
    }

    private fun shapeIsValid(command: CreateUserSheetCommand): Boolean {
        if (command.bpm !in 30..160 || command.pdfSheetId != null) return false
        if (command.columnCount != null && command.columnCount !in 1..MAX_COLUMN_COUNT) return false
        return when (command.startType) {
            SheetStartType.BLANK ->
                command.jangdanId != null && command.columnCount != null && command.copyFromSongId == null && command.copyFromSheetId == null
            SheetStartType.COPY -> (command.copyFromSongId == null) != (command.copyFromSheetId == null)
            // PDF 방향은 회의(2026-11-02 안건 ①) 전이라 받지 않는다.
            SheetStartType.PDF -> false
        }
    }

    private fun blank(command: CreateUserSheetCommand): Draft? {
        val jangdan = repository.findJangdan(checkNotNull(command.jangdanId)) ?: return null
        return Draft(SheetStatus.NEEDS_REVIEW, jangdan.id, jangdan.bpmUnit, checkNotNull(command.columnCount), null, 0)
    }

    /** 원본과 연결하지 않고 내용만 새 악보 버전으로 옮긴다(SHT-03 규칙 1). */
    private fun copy(command: CreateUserSheetCommand): CopyOutcome {
        val draft = command.copyFromSongId?.let { songId ->
            val song = repository.findSongSource(songId) ?: return Rejected(CreateUserSheetResult.NotFound)
            val copied = scores.copy(song.scoreJson, FIRST_VERSION) ?: return Rejected(CreateUserSheetResult.StateConflict)
            val status = if (song.scorable) SheetStatus.EVALUABLE else SheetStatus.NEEDS_REVIEW
            Draft(status, song.jangdanId, song.bpmUnit, copied.columnCount, copied.json, copied.noteCount)
        } ?: run {
            val source = repository.find(checkNotNull(command.copyFromSheetId), command.userId) ?: return Rejected(CreateUserSheetResult.NotFound)
            val json = source.scoreJson?.let { scores.copy(it, FIRST_VERSION) ?: return Rejected(CreateUserSheetResult.StateConflict) }
            Draft(source.status, source.jangdanId, source.bpmUnit, source.columnCount, json?.json, source.noteCount)
        }
        // 생략하면 원본을 따르고, 주면 원본과 같아야 한다. 장단·각 수를 바꾸는 편집은 SHT-04에서 한다.
        if (command.jangdanId != null && command.jangdanId != draft.jangdanId) return Rejected(CreateUserSheetResult.InvalidRequest)
        if (command.columnCount != null && command.columnCount != draft.columnCount) return Rejected(CreateUserSheetResult.InvalidRequest)
        return draft
    }

    private fun replay(existing: UserSheet, hash: String): CreateUserSheetResult {
        if (existing.requestHash != hash) return CreateUserSheetResult.IdempotencyConflict
        val events = existing.scoreJson?.let {
            scores.timeline(it, existing.scoreVersion, existing.noteCount, existing.bpm, existing.bpmUnit)
                ?: return CreateUserSheetResult.StateConflict
        } ?: emptyList()
        return CreateUserSheetResult.Created(existing, events)
    }

    private sealed interface CopyOutcome

    private data class Rejected(val result: CreateUserSheetResult) : CopyOutcome

    private data class Draft(
        val status: SheetStatus,
        val jangdanId: Long,
        val bpmUnit: String,
        val columnCount: Int,
        val scoreJson: String?,
        val noteCount: Int,
    ) : CopyOutcome

    companion object {
        const val MAX_TITLE_LENGTH = 50
        const val MAX_COLUMN_COUNT = 40
        private const val FIRST_VERSION = 1

        /** 앞뒤 공백을 뺀 제목과 본문 값의 SHA-256. JSON 공백·키 순서가 달라도 같은 요청이면 같다. */
        fun requestHash(command: CreateUserSheetCommand): String {
            val canonical = listOf(
                "v1",
                command.title,
                command.startType.name,
                command.jangdanId ?: "-",
                command.bpm,
                command.columnCount ?: "-",
                command.copyFromSongId ?: "-",
                command.copyFromSheetId ?: "-",
            ).joinToString("|")
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()))
        }
    }
}
