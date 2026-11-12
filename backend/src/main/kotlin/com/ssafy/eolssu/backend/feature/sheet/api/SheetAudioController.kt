package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.sheet.application.*
import org.springframework.context.annotation.Profile
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode

@RestController
@Profile("db | ops")
@RequestMapping("/api/users/me/sheets/{sheetId}/audio")
class SheetAudioController(private val audio: SheetAudios) {
    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt,@PathVariable sheetId: String): ResponseEntity<*> = respond {
        HttpStatus.OK to audio.get(requireNotNull(jwt.subject).toLong(),id(sheetId))
    }
    @PostMapping
    fun request(@AuthenticationPrincipal jwt: Jwt,@PathVariable sheetId: String,@RequestBody(required=false) body: JsonNode?): ResponseEntity<*> = respond {
        if(body!=null && !body.isObject) invalid()
        val instrument=body?.get("instrumentId")?.let { if(!it.isString) invalid(); id(it.asString()) }
        val jangdan=body?.get("withJangdan")?.let { if(!it.isBoolean) invalid(); it.booleanValue() } ?: false
        val bpm=body?.get("bpm")?.let { if(!it.isInt) invalid(); it.intValue() }
        val result=audio.request(requireNotNull(jwt.subject).toLong(),id(sheetId),instrument,jangdan,bpm)
        (if(result.reused) HttpStatus.OK else HttpStatus.ACCEPTED) to result.body
    }
    private fun respond(action: ()->Pair<HttpStatus,SheetAudioView>): ResponseEntity<*> = try {
        val (status,body)=action()
        val message=if(status==HttpStatus.ACCEPTED) "음원 생성 요청 접수" else "음원 상태 조회 성공"
        ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(SuccessResponse.of(status,message,body))
    } catch(error: SheetAudioRejection) {
        ResponseEntity.status(error.code.status).cacheControl(CacheControl.noStore()).body(ErrorResponse.of(error.code, error.details))
    }
    private fun id(value: String): Long=value.takeIf { it.matches(Regex("[1-9][0-9]{0,17}")) }?.toLong() ?: invalid()
    private fun invalid(): Nothing=throw SheetAudioRejection(ErrorCode.INVALID_REQUEST)
}
