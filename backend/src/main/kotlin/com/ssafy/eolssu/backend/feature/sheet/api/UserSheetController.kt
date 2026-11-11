package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.practice.api.StrictIntDeserializer
import com.ssafy.eolssu.backend.feature.practice.api.StrictStringDeserializer
import com.ssafy.eolssu.backend.feature.sheet.application.EditUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.GetUserSheets
import com.ssafy.eolssu.backend.feature.sheet.application.SaveSheetDraftCommand
import com.ssafy.eolssu.backend.feature.sheet.application.SaveSheetDraftResult
import com.ssafy.eolssu.backend.feature.sheet.application.SaveSheetScoreCommand
import com.ssafy.eolssu.backend.feature.sheet.application.SaveSheetScoreResult
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScoreLookup
import com.ssafy.eolssu.backend.feature.sheet.application.SheetSourceCleaner
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetDraft
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.json.JsonMapper

// DB ID는 양의 bigint 문자열(명세 공통 타입). Long 범위를 넘는 19자리는 받지 않는다.
private const val ID_PATTERN = "[1-9][0-9]{0,17}"
private val ID = Regex(ID_PATTERN)
// 악보 버전은 1부터의 INT를 문자열로 주고받는다(SHT-08 scoreVersion).
private const val VERSION_PATTERN = "[1-9][0-9]{0,8}"

/** 내 악보 API(SHT-01·02·04·08·11). 계약은 `docs/specs/user-sheets.md`를 따른다. */
@RestController
@RequestMapping("/api/users/me/sheets")
@Profile("db | ops")
class UserSheetController(
    private val getUserSheets: GetUserSheets,
    private val editUserSheet: EditUserSheet,
    private val json: JsonMapper,
    private val sources: SheetSourceCleaner,
    private val audio: com.ssafy.eolssu.backend.feature.sheet.application.SheetAudios? = null,
) {
    /** SHT-08 `GET /users/me/sheets/{sheetId}/score`. 없거나 다른 회원의 악보는 404. */
    @GetMapping("/{sheetId}/score")
    fun score(@AuthenticationPrincipal jwt: Jwt, @PathVariable sheetId: String): ResponseEntity<*> {
        val id = sheetId.takeIf(ID::matches)?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        return when (val found = getUserSheets.get(requireNotNull(jwt.subject).toLong(), id)) {
            is SheetScoreLookup.Found ->
                ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                    SuccessResponse.of(HttpStatus.OK, "공통 악보 조회 성공", SheetScore.of(found.sheet, found.events, json)))
            SheetScoreLookup.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            SheetScoreLookup.Broken -> error(ErrorCode.STATE_CONFLICT)
        }
    }

    /** SHT-04 `PUT /users/me/sheets/{sheetId}/score`. 공통 악보 문서 전체를 확정 저장하고 버전을 1 올린다. */
    @PutMapping("/{sheetId}/score")
    fun saveScore(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable sheetId: String,
        @Valid @RequestBody request: SaveSheetScoreRequest,
    ): ResponseEntity<*> {
        val id = sheetId.takeIf(ID::matches)?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        val score = request.score?.takeIf { it.isObject } ?: return error(ErrorCode.INVALID_REQUEST)
        // JsonNode 속성의 JSON null은 NullNode로 온다. 선택 필드이므로 생략과 같게 본다.
        val resolved = request.resolvedIssueIds?.takeUnless { it.isNull }
        if (resolved != null && !(resolved.isArray && resolved.all { it.isString })) return error(ErrorCode.INVALID_REQUEST)
        val command = SaveSheetScoreCommand(
            userId = requireNotNull(jwt.subject).toLong(),
            sheetId = id,
            baseScoreVersion = request.baseScoreVersion!!.toInt(),
            title = request.title,
            jangdanId = request.jangdanId?.toLong(),
            bpm = request.bpm!!,
            scoreJson = json.writeValueAsString(score),
            resolvedIssueIds = resolved?.toList()?.map { it.stringValue() }.orEmpty(),
        )
        return when (val result = editUserSheet.save(command)) {
            is SaveSheetScoreResult.Saved ->
                ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "정간보 편집 저장 성공", SavedSheetScore.of(result.sheet, result.events)))
            is SaveSheetScoreResult.UnfilledCells -> ResponseEntity.status(ErrorCode.UNFILLED_CELLS.status)
                .body(ErrorResponse.of(ErrorCode.UNFILLED_CELLS, mapOf("cellIds" to result.cellIds)))
            SaveSheetScoreResult.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            SaveSheetScoreResult.InvalidRequest -> error(ErrorCode.INVALID_REQUEST)
            SaveSheetScoreResult.StateConflict -> error(ErrorCode.STATE_CONFLICT)
            SaveSheetScoreResult.TooLarge -> error(ErrorCode.PAYLOAD_TOO_LARGE)
        }
    }

    /** SHT-11 `PUT /users/me/sheets/{sheetId}/score/draft`. 버전을 올리지 않는 자동 저장 초안. */
    @PutMapping("/{sheetId}/score/draft")
    fun saveDraft(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable sheetId: String,
        @Valid @RequestBody request: SaveSheetDraftRequest,
    ): ResponseEntity<*> {
        val id = sheetId.takeIf(ID::matches)?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        val score = request.score?.takeIf { it.isObject } ?: return error(ErrorCode.INVALID_REQUEST)
        val command = SaveSheetDraftCommand(
            userId = requireNotNull(jwt.subject).toLong(),
            sheetId = id,
            baseScoreVersion = request.baseScoreVersion!!.toInt(),
            draftRevision = request.draftRevision!!,
            title = request.title,
            jangdanId = request.jangdanId?.toLong(),
            bpm = request.bpm!!,
            scoreJson = json.writeValueAsString(score),
        )
        return when (val result = editUserSheet.saveDraft(command)) {
            is SaveSheetDraftResult.Saved -> ResponseEntity.ok(
                SuccessResponse.of(
                    HttpStatus.OK, "악보 초안 저장 성공",
                    SavedSheetDraft(id.toString(), result.draftRevision, result.unfilledCellCount, result.savedAt),
                ),
            )
            SaveSheetDraftResult.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            SaveSheetDraftResult.InvalidRequest -> error(ErrorCode.INVALID_REQUEST)
            SaveSheetDraftResult.StateConflict -> error(ErrorCode.STATE_CONFLICT)
            SaveSheetDraftResult.TooLarge -> error(ErrorCode.PAYLOAD_TOO_LARGE)
        }
    }

    /** SHT-02 `DELETE /users/me/sheets/{sheetId}`. 초안·변환·원본 행은 함께 지워진다(V26 ON DELETE CASCADE). */
    @DeleteMapping("/{sheetId}")
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable sheetId: String): ResponseEntity<*> {
        val id = sheetId.takeIf(ID::matches)?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        val userId = requireNotNull(jwt.subject).toLong()
        // 원본 객체를 먼저 지운다. 저장소가 실패하면 행을 남겨 다시 지울 수 있게 한다(SHT-02 규칙 5).
        var ownsDeletion=false
        val deleted = try {
            if(audio!=null) {
                if(!audio.beginDeletion(userId,id)) return error(ErrorCode.RESOURCE_NOT_FOUND)
                ownsDeletion=true
            }
            audio?.delete(userId,id)
            sources.deleteSources(userId, id) && editUserSheet.delete(userId, id)
        } catch (e: Exception) {
            // 악보가 남으면 음원을 다시 만들 수 있어야 한다. 풀지 못해도 원래 응답을 지키고 재시작 때 푼다.
            if(ownsDeletion) runCatching { audio?.release(userId, id) }
            if (e is SheetSourceStorageException) return error(ErrorCode.SERVICE_UNAVAILABLE)
            throw e
        } finally {
            if(ownsDeletion) audio?.endDeletion(id)
        }
        if (!deleted) return error(ErrorCode.RESOURCE_NOT_FOUND)
        return ResponseEntity.noContent().build<Unit>()
    }

    /** SHT-01 `GET /users/me/sheets`. 본인 악보만 최근 수정 순으로 돌려준다. */
    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) @Pattern(regexp = "JEONGGANBO|MIDI|PDF") kind: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): ResponseEntity<*> {
        val result = getUserSheets.list(requireNotNull(jwt.subject).toLong(), kind, page, size)
        return ResponseEntity.ok(
            SuccessResponse.of(
                HttpStatus.OK, "내 악보 목록 조회 성공",
                SheetList(
                    items = result.items.map { SheetItem.of(it, json) },
                    page = page,
                    size = size,
                    totalElements = result.totalElements,
                    totalPages = (result.totalElements + size - 1) / size,
                ),
            ),
        )
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

// 명세 타입(STRING·INT)과 다른 JSON 값은 자동 변환하지 않고 400으로 거절한다(StrictJsonDeserializers).
data class SaveSheetScoreRequest(
    @field:NotNull @field:Pattern(regexp = VERSION_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class)
    val baseScoreVersion: String? = null,
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val title: String? = null,
    @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val jangdanId: String? = null,
    @field:NotNull @param:JsonDeserialize(using = StrictIntDeserializer::class) val bpm: Int? = null,
    // 공통 악보 문서와 확인한 항목 목록의 모양은 컨트롤러·응용에서 검사한다.
    @field:NotNull val score: JsonNode? = null,
    val resolvedIssueIds: JsonNode? = null,
)

data class SaveSheetDraftRequest(
    @field:NotNull @field:Pattern(regexp = VERSION_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class)
    val baseScoreVersion: String? = null,
    @field:NotNull @param:JsonDeserialize(using = StrictIntDeserializer::class) val draftRevision: Int? = null,
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val title: String? = null,
    @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val jangdanId: String? = null,
    @field:NotNull @param:JsonDeserialize(using = StrictIntDeserializer::class) val bpm: Int? = null,
    @field:NotNull val score: JsonNode? = null,
)

data class SavedSheetScore(
    val sheetId: String,
    val scoreVersion: String,
    val reviewStatus: String,
    val noteCount: Int,
    val columnCount: Int,
    val pendingIssueCount: Int,
    val events: List<ScoreTimelineEvent>,
    val updatedAt: Instant,
) {
    companion object {
        fun of(sheet: UserSheet, events: List<ScoreTimelineEvent>) = SavedSheetScore(
            sheet.id.toString(), sheet.scoreVersion.toString(), sheet.status.name, sheet.noteCount, sheet.columnCount, sheet.pendingCellCount,
            events, sheet.updatedAt,
        )
    }
}

data class SavedSheetDraft(val sheetId: String, val draftRevision: Int, val unfilledCellCount: Int, val savedAt: Instant)

/** `scoreData`는 저장한 공통 악보 원본이며, 한 번도 저장하지 않은 빈 악보는 null이다. */
data class SheetScore(
    val sheetId: String,
    val title: String,
    val kind: String,
    val jangdanId: String?,
    val bpm: Int,
    val bpmUnit: String,
    val columnCount: Int,
    val scoreVersion: String,
    val reviewStatus: String,
    val scoreData: JsonNode?,
    /** scoreData의 CAT-04 형식 타임라인. 빈 악보는 []. */
    val events: List<ScoreTimelineEvent>,
    /** 불러오기 악보의 원본 정보. MANUAL은 null. */
    val source: SheetSourceView?,
    /** 변환 상태(SHT-10과 같음). MANUAL은 null. */
    val conversionStatus: String?,
    /** 확인 필요 칸과 악보 전체 알림은 변환 작업자(S15P31E102-472)가 채운다. */
    val issues: List<Any>,
    val notices: List<Any>,
    /** 확정 저장 전 자동 저장 초안. 없으면 null. */
    val draft: SheetDraftView?,
    val updatedAt: Instant,
) {
    companion object {
        fun of(sheet: UserSheet, events: List<ScoreTimelineEvent>, json: JsonMapper): SheetScore {
            val details=sheet.imported?.resultJson?.let(json::readTree)
            return SheetScore(
            sheet.id.toString(), sheet.title, sheet.kind, sheet.jangdanId?.toString(), sheet.bpm, sheet.bpmUnit,
            // 변환이 끝나기 전 각 수는 없다(행에는 CHECK 때문에 1을 둔다).
            if (sheet.imported != null && sheet.imported.conversionStatus != SheetImportStatus.DONE) 0 else sheet.columnCount,
            sheet.scoreVersion.toString(), sheet.status.name, sheet.scoreJson?.let(json::readTree), events,
            sheet.imported?.let { SheetSourceView(sheet.source, it.fileName, it.hwangPitch,emptyList(),null) }, sheet.imported?.conversionStatus?.name,
            details?.path("issues")?.toList() ?: emptyList(), details?.path("notices")?.toList() ?: emptyList(), sheet.draft?.let { SheetDraftView.of(it, sheet.draftRevision, json) }, sheet.updatedAt,
        )
        }
    }
}

/** SHT-08: 원본은 보관하지 않으므로 pages=[]와 pagesExpireAt=null을 유지한다(534). */
data class SheetSourceView(
    val type: String,
    val fileName: String?,
    val hwangPitch: String?,
    val pages: List<SheetSourcePage> = emptyList(),
    val pagesExpireAt: Instant? = null,
)
data class SheetSourcePage(val pageIdx: Int,val imageUrl: String,val width: Int,val height: Int)

/** 초안 문서({title, jangdanId, bpm, score})와 저장 정보. */
data class SheetDraftView(
    val title: String,
    val jangdanId: String?,
    val bpm: Int,
    val score: JsonNode,
    val baseScoreVersion: String,
    val draftRevision: Int,
    val savedAt: Instant,
) {
    companion object {
        fun of(draft: SheetDraft, revision: Int, json: JsonMapper): SheetDraftView {
            val document = json.readTree(draft.json)
            return SheetDraftView(
                document.path("title").stringValue(), document.path("jangdanId").takeIf { it.isString }?.stringValue(),
                document.path("bpm").intValue(), document.path("score"), draft.baseScoreVersion.toString(), revision, draft.savedAt,
            )
        }
    }
}

data class SheetList(
    val items: List<SheetItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long,
)

data class SheetItem(
    val sheetId: String,
    val kind: String,
    val title: String,
    val status: String,
    val jangdanId: String?,
    val jangdanName: String?,
    val source: String,
    val pendingCellCount: Int,
    val sourcePageCount: Int?,
    /** 자동 저장 초안(SHT-11)이 있으면 true. */
    val hasDraft: Boolean,
    /** 불러오기 악보의 변환 상태. MANUAL은 null. */
    val conversionStatus: String?,
    val updatedAt: Instant,
    /** 편집기에 불러올 공통 악보의 악기 코드. 초안이 있으면 그 초안의 값이다. */
    val instrumentCode: String?,
) {
    companion object {
        fun of(sheet: UserSheet, json: JsonMapper) = SheetItem(
            sheet.id.toString(), sheet.kind, sheet.title, sheet.status.name, sheet.jangdanId?.toString(), sheet.jangdanName,
            sheet.source, sheet.pendingCellCount, sheet.imported?.pageCount, sheet.draft != null, sheet.imported?.conversionStatus?.name,
            sheet.updatedAt, instrumentCode(sheet, json),
        )

        private fun instrumentCode(sheet: UserSheet, json: JsonMapper): String? {
            val draft = sheet.draft
            val source = draft?.json ?: sheet.scoreJson ?: return null
            return try {
                val document = json.readTree(source)
                val score = if (draft == null) document else document.path("score")
                score.path("score").path("instrument")
                    .takeIf { it.isString }?.stringValue()?.takeIf { it.isNotBlank() }
            } catch (_: JacksonException) {
                null
            }
        }
    }
}
