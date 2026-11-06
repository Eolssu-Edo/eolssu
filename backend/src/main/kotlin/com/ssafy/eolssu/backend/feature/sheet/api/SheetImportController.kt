package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.practice.api.StrictIntDeserializer
import com.ssafy.eolssu.backend.feature.practice.api.StrictStringDeserializer
import com.ssafy.eolssu.backend.feature.sheet.application.SheetImportCommand
import com.ssafy.eolssu.backend.feature.sheet.application.SheetImportFailure
import com.ssafy.eolssu.backend.feature.sheet.application.SheetImportMetadata
import com.ssafy.eolssu.backend.feature.sheet.application.SheetImportResult
import com.ssafy.eolssu.backend.feature.sheet.application.SheetImports
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetConversion
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetNotation
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetUpload
import jakarta.validation.constraints.Pattern
import java.nio.file.Path
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import tools.jackson.core.JacksonException
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.json.JsonMapper

private val SHEET_ID = Regex("[1-9][0-9]{0,17}")

/** 변환 중 조회 권장 간격(SHT-10 pollAfterMs). */
private const val POLL_AFTER_MS = 2_000

/** 내 악보 불러오기(SHT-07)와 변환 상태(SHT-10). 계약은 `docs/specs/user-sheets.md`·Notion SHT-07·10을 따른다. */
@RestController
@RequestMapping("/api/users/me/sheets")
@Profile("db | ops")
class SheetImportController(private val imports: SheetImports, private val json: JsonMapper,
    @org.springframework.beans.factory.annotation.Qualifier("temporarySheetSourceStorage")
    private val temporaryStorage: com.ssafy.eolssu.backend.feature.sheet.infrastructure.TemporarySheetSourceStorage) {
    /** SHT-07. 파일 파트 `files`(PDF 1개 또는 PNG·JPEG 1~10장)와 JSON 파트 `metadata`. */
    @PostMapping("/imports", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun import(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "[!-~]{1,100}") idempotencyKey: String,
        @RequestPart("files") files: List<MultipartFile>,
        @RequestPart("metadata") metadata: String,
    ): ResponseEntity<*> {
        val parsed = parse(metadata) ?: return error(ErrorCode.INVALID_REQUEST)
        // PDF·형식 검사는 경로로 읽으므로 임시 파일에 옮기고, 요청이 끝나면 지운다.
        val temporary = mutableListOf<Path>()
        val result = try {
            val uploads = files.map { part ->
                val path = temporaryStorage.createUpload().also(temporary::add)
                part.transferTo(path)
                SheetUpload(path, part.size, part.originalFilename.orEmpty())
            }
            imports.accept(SheetImportCommand(requireNotNull(jwt.subject).toLong(), idempotencyKey, uploads, parsed))
        } catch(_: com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException) {
            return error(ErrorCode.CONVERSION_UNAVAILABLE)
        } finally {
            temporary.forEach(temporaryStorage::releaseUpload)
        }
        return when (result) {
            is SheetImportResult.Accepted -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(SuccessResponse.of(HttpStatus.ACCEPTED, "악보 변환 요청 접수", SheetImportAccepted.of(result.conversion)))
            is SheetImportResult.Rejected -> error(result.failure.code())
        }
    }

    /** SHT-10. 불러오기로 만들지 않은 악보·다른 회원의 악보는 404. */
    @GetMapping("/{sheetId}/conversion")
    fun conversion(@AuthenticationPrincipal jwt: Jwt, @PathVariable sheetId: String): ResponseEntity<*> {
        val id = sheetId.takeIf(SHEET_ID::matches)?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        val conversion = imports.conversion(requireNotNull(jwt.subject).toLong(), id) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(SuccessResponse.of(HttpStatus.OK, "악보 변환 상태 조회 성공", SheetConversionView.of(conversion)))
    }

    /** JSON 타입이 명세와 다르거나 필수값이 없으면 null(400). 범위는 응용 계층이 본다. */
    private fun parse(metadata: String): SheetImportMetadata? {
        val request = try {
            json.readValue(metadata, SheetImportMetadataRequest::class.java)
        } catch (_: JacksonException) {
            return null
        } ?: return null
        val notation = request.notation?.let { value -> SheetNotation.entries.firstOrNull { it.name == value } } ?: return null
        val instrumentId = request.instrumentId?.takeIf(SHEET_ID::matches)?.toLong() ?: return null
        val jangdanId = request.jangdanId?.let { it.takeIf(SHEET_ID::matches)?.toLong() ?: return null }
        return SheetImportMetadata(request.title ?: return null, notation, instrumentId, jangdanId, request.bpm, request.hwangPitch)
    }

    private fun SheetImportFailure.code() = when (this) {
        SheetImportFailure.INVALID -> ErrorCode.INVALID_REQUEST
        SheetImportFailure.TOO_LARGE -> ErrorCode.FILE_TOO_LARGE
        SheetImportFailure.UNSUPPORTED_TYPE -> ErrorCode.UNSUPPORTED_FILE_TYPE
        SheetImportFailure.TOO_MANY_PAGES -> ErrorCode.TOO_MANY_PAGES
        SheetImportFailure.UNREADABLE -> ErrorCode.UNREADABLE_FILE
        SheetImportFailure.NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND
        SheetImportFailure.IN_PROGRESS -> ErrorCode.CONVERSION_IN_PROGRESS
        SheetImportFailure.LIMIT -> ErrorCode.SHEET_LIMIT_EXCEEDED
        SheetImportFailure.IDEMPOTENCY_CONFLICT -> ErrorCode.IDEMPOTENCY_CONFLICT
        SheetImportFailure.UNAVAILABLE -> ErrorCode.CONVERSION_UNAVAILABLE
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

// 명세 타입(STRING·INT)과 다른 JSON 값은 자동 변환하지 않고 400으로 거절한다(StrictJsonDeserializers).
data class SheetImportMetadataRequest(
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val title: String? = null,
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val notation: String? = null,
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val instrumentId: String? = null,
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val jangdanId: String? = null,
    @param:JsonDeserialize(using = StrictIntDeserializer::class) val bpm: Int? = null,
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val hwangPitch: String? = null,
)

data class SheetImportAccepted(
    val sheetId: String,
    val notation: String,
    val source: String,
    val conversionStatus: String,
    val pageCount: Int,
    val acceptedAt: Instant,
) {
    companion object {
        fun of(conversion: SheetConversion) = SheetImportAccepted(
            conversion.sheetId.toString(), conversion.notation.name, conversion.notation.source, conversion.status.name,
            conversion.pageCount, conversion.acceptedAt,
        )
    }
}

/** SHT-10 변환 상태와 확인이 남은 정간 수. */
data class SheetConversionView(
    val sheetId: String,
    val notation: String,
    val status: String,
    val stage: String?,
    val processedPageCount: Int,
    val pageCount: Int,
    val jangdanAuto: Boolean,
    val bpmAuto: Boolean,
    val pollAfterMs: Int?,
    val issueCount: Int?,
    val noteCount: Int?,
    val failureCode: String?,
    val attempt: Int,
    /** 내부 실패 원인과 횟수 상한으로 판정한다(SHT-14). */
    val retryable: Boolean,
    val acceptedAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
) {
    companion object {
        fun of(conversion: SheetConversion): SheetConversionView {
            val active = conversion.status == SheetImportStatus.QUEUED || conversion.status == SheetImportStatus.RUNNING
            val done = conversion.status == SheetImportStatus.DONE
            return SheetConversionView(
                sheetId = conversion.sheetId.toString(),
                notation = conversion.notation.name,
                status = conversion.status.name,
                stage = conversion.stage.takeIf { conversion.status == SheetImportStatus.RUNNING },
                processedPageCount = conversion.processedPageCount,
                pageCount = conversion.pageCount,
                jangdanAuto = conversion.notation == SheetNotation.STAFF && conversion.requestedJangdanId == null,
                bpmAuto = conversion.requestedBpm == null,
                pollAfterMs = POLL_AFTER_MS.takeIf { active },
                issueCount = conversion.pendingCellCount.takeIf { done },
                noteCount = conversion.noteCount.takeIf { done },
                failureCode = conversion.failureCode,
                attempt = conversion.attempt,
                retryable = com.ssafy.eolssu.backend.feature.sheet.application.ConversionRetryPolicy.allowed(
                    conversion.status.name,conversion.failureCause,conversion.attempt,conversion.timeoutCount),
                acceptedAt = conversion.acceptedAt,
                startedAt = conversion.startedAt,
                finishedAt = conversion.finishedAt,
            )
        }
    }
}
