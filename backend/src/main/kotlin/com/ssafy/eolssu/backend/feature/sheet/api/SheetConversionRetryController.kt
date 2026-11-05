package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@Profile("db | ops")
class SheetConversionRetryController(private val queue: ConversionQueue,private val clock: Clock,
    private val client: com.ssafy.eolssu.backend.feature.sheet.infrastructure.RecognitionClient,
    private val storage: com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage) {
    @PostMapping("/api/users/me/sheets/{sheetId}/conversion/retry")
    fun retry(@AuthenticationPrincipal jwt: Jwt,@PathVariable sheetId: String): ResponseEntity<*> {
        val id=sheetId.takeIf { Regex("[1-9][0-9]{0,17}").matches(it) }?.toLong() ?: return error(ErrorCode.INVALID_REQUEST)
        val result=queue.retry(requireNotNull(jwt.subject).toLong(),id,clock.instant().truncatedTo(ChronoUnit.MICROS),client.available && storage.available)
        return when(result.status) {
            "NOT_FOUND" -> error(ErrorCode.RESOURCE_NOT_FOUND)
            "STATE_CONFLICT" -> error(ErrorCode.STATE_CONFLICT)
            "CONVERSION_IN_PROGRESS" -> error(ErrorCode.CONVERSION_IN_PROGRESS)
            "UNAVAILABLE" -> error(ErrorCode.CONVERSION_UNAVAILABLE)
            else -> ResponseEntity.status(HttpStatus.ACCEPTED).body(SuccessResponse.of(HttpStatus.ACCEPTED,"악보 변환 재시도 접수",
                SheetRetryAccepted(sheetId,result.status,result.attempt,checkNotNull(result.acceptedAt))))
        }
    }
    private fun error(code: ErrorCode)=ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}
data class SheetRetryAccepted(val sheetId: String,val conversionStatus: String,val attempt: Int,val acceptedAt: Instant)
