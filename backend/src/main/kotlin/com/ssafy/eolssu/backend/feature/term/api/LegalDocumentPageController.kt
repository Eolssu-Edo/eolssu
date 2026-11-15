package com.ssafy.eolssu.backend.feature.term.api

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.LocalRecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.S3RecordingStorage
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
    @GetMapping("/api/terms/documents/{version:\\d{4}-\\d{2}-\\d{2}}/{document}", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(@PathVariable version: String, @PathVariable document: String): ResponseEntity<String> {
        if (version !in VERSIONS || document !in DOCUMENTS) return ResponseEntity.notFound().build()
        return if (publication.ready(version)) render(version, document) else unavailable()
    }

    /** 앱·Play Console이 연결하는 고정 주소. 실제 저장소에 맞는 현행 버전을 보여 주므로 버전이 바뀌어도 링크를 고치지 않는다. */
    @GetMapping("/api/terms/documents/current/{document}", produces = [MediaType.TEXT_HTML_VALUE])
    fun current(@PathVariable document: String): ResponseEntity<String> {
        if (document !in DOCUMENTS) return ResponseEntity.notFound().build()
        return publication.currentVersion()?.let { render(it, document) } ?: unavailable()
    }

    private fun unavailable(): ResponseEntity<String> = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
        .contentType(MediaType("text", "html", Charsets.UTF_8)).body("약관 공개 준비 중입니다.")

    private fun render(version: String, document: String): ResponseEntity<String> =
        ResponseEntity.ok().cacheControl(CacheControl.noCache())
            .contentType(MediaType("text", "html", Charsets.UTF_8))
            .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
            .header("X-Content-Type-Options", "nosniff")
            .header("Referrer-Policy", "no-referrer")
            .body(ClassPathResource("legal/$version/$document.html").getContentAsString(Charsets.UTF_8))

    companion object {
        /**
         * 2026-10-30은 국내 디스크 저장, 2026-11-02은 Cloudflare R2 국외 보관, 2026-11-03은 저장한 기록의 녹음 보관(S15P31E102-375),
         * 2026-11-15은 내 악보 업로드 처리와 재생 음원의 R2 국외 보관(S15P31E102-918)을 고지한다.
         * R2 운영 중에는 최신 판이 현행이고 앞의 판들은 이전 버전 이력이다. 판 날짜는 실제 운영 배포일과 같아야 하므로 배포가 늦어지면 병합 전에 고친다.
         */
        const val DOMESTIC_DISK_VERSION = "2026-10-30"
        const val R2_VERSION = "2026-11-02"
        const val SAVED_RECORDING_VERSION = "2026-11-03"
        const val USER_SHEET_VERSION = "2026-11-15"
        val VERSIONS = setOf(DOMESTIC_DISK_VERSION, R2_VERSION, SAVED_RECORDING_VERSION, USER_SHEET_VERSION)
        val DOCUMENTS = setOf("service", "privacy", "collection", "permissions", "age")
        val PUBLIC_PATHS = (VERSIONS + "current").flatMap { version -> DOCUMENTS.map { "/api/terms/documents/$version/$it" } }
    }
}

/**
 * 처리방침의 저장 위치 고지는 실제 저장소와 맞을 때만 공개한다. R2 운영 중에는 국내 저장 버전을 이전 버전 이력으로 남기고,
 * 국내 디스크 운영 중에는 국외 이전 고지를 미리 공개하지 않는다.
 */
@Component
class LegalPublicationGate(
    @Value("\${eolssu.legal.publication-enabled:false}") private val enabled: Boolean,
    private val storage: ObjectProvider<RecordingStorage>,
) {
    val ready: Boolean get() = enabled && storage.ifAvailable.let { it is LocalRecordingStorage || it is S3RecordingStorage }

    fun ready(version: String): Boolean = enabled && when (storage.ifAvailable) {
        is LocalRecordingStorage -> version == LegalDocumentPageController.DOMESTIC_DISK_VERSION
        is S3RecordingStorage -> true
        else -> false
    }

    fun currentVersion(): String? = if (!enabled) null else when (storage.ifAvailable) {
        is LocalRecordingStorage -> LegalDocumentPageController.DOMESTIC_DISK_VERSION
        is S3RecordingStorage -> LegalDocumentPageController.USER_SHEET_VERSION
        else -> null
    }
}
