package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

/** Compatibility endpoint: source bytes have been discarded, so callers must re-upload. */
@RestController
@Profile("db | ops")
class SheetConversionRetryController(private val queue: ConversionQueue) {
    @PostMapping("/api/users/me/sheets/{sheetId}/conversion/retry")
    fun retry(@AuthenticationPrincipal jwt: Jwt,@PathVariable sheetId: String): ResponseEntity<*> {
        val id=sheetId.takeIf { Regex("[1-9][0-9]{0,17}").matches(it) }?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        val result=queue.retry(requireNotNull(jwt.subject).toLong(),id)
        return error(if(result.status=="NOT_FOUND") ErrorCode.RESOURCE_NOT_FOUND else ErrorCode.STATE_CONFLICT)
    }
    private fun error(code: ErrorCode)=ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}
