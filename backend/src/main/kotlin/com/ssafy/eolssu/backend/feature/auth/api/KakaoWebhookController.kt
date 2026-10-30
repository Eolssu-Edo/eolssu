package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.feature.auth.application.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.KakaoUnlinkStore
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataAccessException
import org.springframework.transaction.TransactionException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.slf4j.LoggerFactory

@RestController
@Profile("db | ops")
class KakaoWebhookController(private val verifier:KakaoEventVerifier,private val store:KakaoUnlinkStore,private val deadline:KakaoEventDeadline) {
    @PostMapping("/api/webhooks/kakao/events")
    fun receive(request:HttpServletRequest):ResponseEntity<*> {
        return try {
            if(request.contentType?.substringBefore(';')?.trim()?.lowercase()!="application/secevent+jwt") throw KakaoEventFailure("invalid_request")
            val bytes=request.inputStream.readNBytes(16385)
            if(bytes.size>16384) throw KakaoEventFailure("invalid_request")
            deadline.run{store.accept(verifier.verify(String(bytes,Charsets.UTF_8)))}
            ResponseEntity.accepted().build<Void>()
        } catch(e:KakaoEventFailure) {
            ResponseEntity.badRequest().body(mapOf("err" to e.error,"description" to "Security event validation failed"))
        } catch(_:KakaoEventUnavailable) {
            unavailable()
        } catch(_:DataAccessException) {
            unavailable()
        } catch(_:TransactionException) {
            unavailable()
        }
    }
    private fun unavailable():ResponseEntity<Void> {
        // 서명 토큰·subject·SQL/예외 메시지를 로그에 포함하지 않는다.
        LoggerFactory.getLogger(javaClass).warn("Kakao security event processing unavailable; retry required")
        return ResponseEntity.status(503).build()
    }
}
