package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.practice.api.StrictStringDeserializer
import com.ssafy.eolssu.backend.feature.sheet.application.CopyPublishedSheet
import com.ssafy.eolssu.backend.feature.sheet.application.SheetCopyFailure
import com.ssafy.eolssu.backend.feature.sheet.application.SheetCopyResult
import jakarta.validation.constraints.Pattern
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.annotation.JsonDeserialize

@RestController
@Profile("db | ops")
class PublishedSheetCopyController(private val copier: CopyPublishedSheet) {
    @PostMapping("/api/users/me/sheets/copies", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun copy(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "[!-~]{1,100}") key: String,
        @RequestBody request: PublishedSheetCopyRequest,
    ): ResponseEntity<*> {
        val songId = request.songId.takeIf(songIdPattern::matches)?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        return when (val result = copier.copy(requireNotNull(jwt.subject).toLong(), songId, key)) {
            is SheetCopyResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(
                SuccessResponse.of(HttpStatus.CREATED, "제공 곡 복사 성공", PublishedSheetCopyResponse(result.sheetId.toString())),
            )
            is SheetCopyResult.Rejected -> error(when (result.failure) {
                SheetCopyFailure.NotFound -> ErrorCode.RESOURCE_NOT_FOUND
                SheetCopyFailure.InvalidScore -> ErrorCode.STATE_CONFLICT
                SheetCopyFailure.IdempotencyConflict -> ErrorCode.IDEMPOTENCY_CONFLICT
                SheetCopyFailure.Limit -> ErrorCode.SHEET_LIMIT_EXCEEDED
                SheetCopyFailure.ConversionInProgress -> ErrorCode.CONVERSION_IN_PROGRESS
            })
        }
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))

    private companion object {
        val songIdPattern = Regex("[1-9][0-9]{0,17}")
    }
}

data class PublishedSheetCopyRequest(
    @param:JsonDeserialize(using = StrictStringDeserializer::class) val songId: String,
)

data class PublishedSheetCopyResponse(val sheetId: String)
