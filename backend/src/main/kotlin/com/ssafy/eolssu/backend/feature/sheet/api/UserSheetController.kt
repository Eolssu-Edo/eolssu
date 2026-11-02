package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.practice.api.StrictIntDeserializer
import com.ssafy.eolssu.backend.feature.practice.api.StrictStringDeserializer
import com.ssafy.eolssu.backend.feature.sheet.application.CreateUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.CreateUserSheetCommand
import com.ssafy.eolssu.backend.feature.sheet.application.CreateUserSheetResult
import com.ssafy.eolssu.backend.feature.sheet.application.GetUserSheets
import com.ssafy.eolssu.backend.feature.sheet.application.SheetStartType
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.json.JsonMapper

// DB ID는 양의 bigint 문자열(명세 공통 타입). Long 범위를 넘는 19자리는 받지 않는다.
private const val ID_PATTERN = "[1-9][0-9]{0,17}"
private val ID = Regex(ID_PATTERN)

/** 내 악보 API(SHT-01·03·08). 계약은 `docs/specs/user-sheets.md`를 따른다. */
@RestController
@RequestMapping("/api/users/me/sheets")
@Profile("db | ops")
class UserSheetController(
    private val createUserSheet: CreateUserSheet,
    private val getUserSheets: GetUserSheets,
    private val json: JsonMapper,
) {
    /** SHT-03 `POST /users/me/sheets`. 빈 악보와 제공 곡·내 악보 복사만 만든다. */
    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        // DB 칸이 VARCHAR(100) ascii_bin이라 출력 가능한 ASCII 1~100자만 받는다.
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "[!-~]{1,100}") idempotencyKey: String,
        @Valid @RequestBody request: CreateUserSheetRequest,
    ): ResponseEntity<*> {
        val command = CreateUserSheetCommand(
            userId = requireNotNull(jwt.subject).toLong(),
            idempotencyKey = idempotencyKey,
            title = request.title!!,
            startType = SheetStartType.valueOf(request.startType!!),
            jangdanId = request.jangdanId?.toLong(),
            bpm = request.bpm!!,
            columnCount = request.columnCount,
            copyFromSongId = request.copyFromSongId?.toLong(),
            copyFromSheetId = request.copyFromSheetId?.toLong(),
            pdfSheetId = request.pdfSheetId?.toLong(),
        )
        return when (val result = createUserSheet.execute(command)) {
            is CreateUserSheetResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(
                SuccessResponse.of(HttpStatus.CREATED, "새 정간보 악보 만들기 성공", CreatedSheet.of(result.sheet, result.events)),
            )
            CreateUserSheetResult.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            CreateUserSheetResult.InvalidRequest -> error(ErrorCode.INVALID_REQUEST)
            CreateUserSheetResult.StateConflict -> error(ErrorCode.STATE_CONFLICT)
            CreateUserSheetResult.IdempotencyConflict -> error(ErrorCode.IDEMPOTENCY_CONFLICT)
        }
    }

    /** SHT-08 `GET /users/me/sheets/{sheetId}/score`. 없거나 다른 회원의 악보는 404. */
    @GetMapping("/{sheetId}/score")
    fun score(@AuthenticationPrincipal jwt: Jwt, @PathVariable sheetId: String): ResponseEntity<*> {
        val id = sheetId.takeIf(ID::matches)?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        val sheet = getUserSheets.get(requireNotNull(jwt.subject).toLong(), id) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "공통 악보 조회 성공", SheetScore.of(sheet, json)))
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
                    items = result.items.map(SheetItem::of),
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
data class CreateUserSheetRequest(
    @field:NotNull @param:JsonDeserialize(using = StrictStringDeserializer::class) val title: String? = null,
    // enum으로 받으면 숫자 0이 첫 값으로 바뀌어 문자열로 받아 이름을 확인한다.
    @field:NotNull @field:Pattern(regexp = "BLANK|COPY|PDF") @param:JsonDeserialize(using = StrictStringDeserializer::class)
    val startType: String? = null,
    @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val jangdanId: String? = null,
    @field:NotNull @param:JsonDeserialize(using = StrictIntDeserializer::class) val bpm: Int? = null,
    @param:JsonDeserialize(using = StrictIntDeserializer::class) val columnCount: Int? = null,
    @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val copyFromSongId: String? = null,
    @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val copyFromSheetId: String? = null,
    @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val pdfSheetId: String? = null,
)

data class CreatedSheet(
    val sheetId: String,
    val kind: String,
    val status: String,
    val scoreVersion: String,
    val events: List<ScoreTimelineEvent>,
    val updatedAt: Instant,
) {
    companion object {
        fun of(sheet: UserSheet, events: List<ScoreTimelineEvent>) =
            CreatedSheet(sheet.id.toString(), sheet.kind, sheet.status.name, sheet.scoreVersion.toString(), events, sheet.updatedAt)
    }
}

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
    val source: Any?,
    val issues: List<Any>,
    val updatedAt: Instant,
) {
    companion object {
        fun of(sheet: UserSheet, json: JsonMapper) = SheetScore(
            sheet.id.toString(), sheet.title, sheet.kind, sheet.jangdanId?.toString(), sheet.bpm, sheet.bpmUnit, sheet.columnCount,
            sheet.scoreVersion.toString(), sheet.status.name, sheet.scoreJson?.let(json::readTree), null, emptyList(), sheet.updatedAt,
        )
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
    val updatedAt: Instant,
) {
    companion object {
        fun of(sheet: UserSheet) = SheetItem(
            sheet.id.toString(), sheet.kind, sheet.title, sheet.status.name, sheet.jangdanId?.toString(), sheet.jangdanName,
            sheet.source, sheet.pendingCellCount, null, sheet.updatedAt,
        )
    }
}
