package com.ssafy.eolssu.backend.feature.term.api

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.LocalRecordingStorage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/** 검토본을 배포만 했다는 이유로 현재 운영 방침으로 공개하지 않는다. */
@RestController
class LegalDocumentPageController(
    private val publication: LegalPublicationGate,
) {
    @GetMapping("/api/terms/documents/2026-10-30/{document}", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(@PathVariable document: String): ResponseEntity<String> {
        if (document !in DOCUMENTS) return ResponseEntity.notFound().build()
        if (!publication.ready) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .contentType(MediaType("text", "html", Charsets.UTF_8)).body("약관 공개 준비 중입니다.")
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
            .contentType(MediaType("text", "html", Charsets.UTF_8))
            .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
            .header("X-Content-Type-Options", "nosniff")
            .header("Referrer-Policy", "no-referrer")
            .body(ClassPathResource("legal/2026-10-30/$document.html").getContentAsString(Charsets.UTF_8))
    }

    companion object {
        val DOCUMENTS = setOf("service", "privacy", "collection", "permissions", "age")
        val PUBLIC_PATHS = DOCUMENTS.map { "/api/terms/documents/2026-10-30/$it" }
    }
}

@Component
class LegalPublicationGate(
    @Value("\${eolssu.legal.publication-enabled:false}") private val enabled: Boolean,
    private val storage: ObjectProvider<RecordingStorage>,
) {
    val ready: Boolean get() = enabled && storage.ifAvailable is LocalRecordingStorage
}
