package com.ssafy.eolssu.backend.feature.term.api

import org.springframework.core.io.ClassPathResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountDeletionPageController(private val publication: LegalPublicationGate) {
    @GetMapping("/api/terms/documents/account-deletion", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(): ResponseEntity<String> {
        if (!publication.ready) return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
            .contentType(MediaType("text", "html", Charsets.UTF_8)).body("약관 공개 준비 중입니다.")
        return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .contentType(MediaType("text", "html", Charsets.UTF_8))
        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
        .header("X-Content-Type-Options", "nosniff")
        .header("Referrer-Policy", "no-referrer")
        .body(ClassPathResource("legal/account-deletion.html").getContentAsString(Charsets.UTF_8))
    }
}
