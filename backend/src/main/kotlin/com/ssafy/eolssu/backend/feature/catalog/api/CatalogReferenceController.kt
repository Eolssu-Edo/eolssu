package com.ssafy.eolssu.backend.feature.catalog.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.catalog.application.CatalogReferenceQueries
import com.ssafy.eolssu.backend.feature.catalog.application.JangdanPreviewAudio
import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogJangdan
import com.ssafy.eolssu.backend.feature.catalog.domain.FingeringPattern
import com.ssafy.eolssu.backend.feature.notation.infrastructure.BundledNotationSymbols
import java.net.URI
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Profile("db | ops")
class CatalogReferenceController(
    private val queries: CatalogReferenceQueries,
    private val previews: JangdanPreviewAudio,
) {
    @GetMapping("/instruments")
    fun instruments() = SuccessResponse.of(HttpStatus.OK, "제공 중인 악기 목록 조회 성공", InstrumentListResult(
        queries.instruments().map { InstrumentItem(it.id.toString(), it.code, it.name, it.pitchToleranceCent,
            it.rangeLowPitchId.toString(), it.rangeHighPitchId.toString()) },
    ))

    @GetMapping("/instruments/{instrumentId}/authoring-capabilities")
    fun authoringCapabilities(@PathVariable instrumentId: String): ResponseEntity<*> {
        val id = positiveId(instrumentId) ?: return invalidRequest()
        val capabilities = queries.authoringCapabilities(id) ?: return notFound()
        return success("악보 작성 지원음 조회 성공", capabilities)
    }

    @GetMapping("/pitches")
    fun pitches(@RequestParam(required = false) instrumentId: String?): ResponseEntity<*> {
        val id = instrumentId?.let { positiveId(it) ?: return invalidRequest() }
        val pitches = queries.pitches(id) ?: return notFound()
        return success("율명·옥타브·표기 정보 조회 성공", PitchListResult(pitches.map {
            PitchItem(it.id.toString(), it.yul, it.octave, it.hanja, it.hangul, it.semitone, null, null)
        }))
    }

    @GetMapping("/instruments/{instrumentId}/fingerings")
    fun fingerings(@PathVariable instrumentId: String, @RequestParam(required = false) pitchId: String?): ResponseEntity<*> {
        val instrument = positiveId(instrumentId) ?: return invalidRequest()
        val pitch = pitchId?.let { positiveId(it) ?: return invalidRequest() }
        val fingerings = queries.fingerings(instrument, pitch) ?: return notFound()
        return success("운지법 목록·특정 음 운지 조회 성공", FingeringListResult(fingerings.map {
            FingeringItem(it.id.toString(), it.instrumentId.toString(), it.pitchId.toString(), it.imageUrl, it.description, it.pattern)
        }, null))
    }

    @GetMapping("/jangdans")
    fun jangdans() = SuccessResponse.of(HttpStatus.OK, "장단 목록 조회 성공", JangdanListResult(queries.jangdans().map {
        JangdanItem(it.id.toString(), it.name, it.defaultBpm, it.bpmUnit, it.pattern.beats.size, it.pattern.group.size, it.patternCellEighths,
            JangdanPatternResult(it.pattern.beats, it.pattern.group))
    }))

    @GetMapping("/jangdans/{jangdanId}")
    fun jangdan(@PathVariable jangdanId: String): ResponseEntity<*> {
        val id = positiveId(jangdanId) ?: return invalidRequest()
        val jangdan = queries.jangdan(id) ?: return notFound()
        return success("장단 패턴·기본 BPM 조회 성공", jangdan.detail())
    }

    @GetMapping("/jangdans/{jangdanId}/audio")
    fun jangdanAudio(@PathVariable jangdanId: String): ResponseEntity<*> {
        val id = positiveId(jangdanId) ?: return invalidRequest()
        if (queries.jangdan(id) == null || !previews.hasAudio(id)) return notFound()
        return audioRedirect(previews.playbackUrl(id))
    }

    @GetMapping("/jangdan-samples/{sample}/{sha256}")
    fun jangdanSample(@PathVariable sample: String, @PathVariable sha256: String): ResponseEntity<*> {
        if (!previews.hasSample(sample, sha256)) return notFound()
        return audioRedirect(previews.sampleUrl(sample, sha256))
    }

    private fun audioRedirect(url: URI?): ResponseEntity<*> {
        if (url == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control", "no-store")
                .body(ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE))
        return ResponseEntity.status(HttpStatus.FOUND).location(url).header("Cache-Control", "no-store").build<Void>()
    }

    @GetMapping("/notation-symbols")
    fun notationSymbols() = SuccessResponse.of(HttpStatus.OK, "쉼표·연장·시김새 기호 설명 조회 성공", NotationSymbolListResult(
        queries.notationSymbols().map {
            val content = BundledNotationSymbols.match(it)
            NotationSymbolItem(it.id.toString(), it.symbol, it.name, it.description,
                if (content == null) it.imageUrl else "/api/notation-symbols/${it.id}/image",
                content?.source, content?.descriptionStatus, content?.imageLicense)
        },
    ))

    @GetMapping("/notation-symbols/{symbolId}/image")
    fun notationSymbolImage(@PathVariable symbolId: String): ResponseEntity<*> {
        val id = positiveId(symbolId) ?: return invalidRequest()
        val stored = queries.notationSymbols().singleOrNull { it.id == id } ?: return notFound()
        val content = BundledNotationSymbols.match(stored) ?: return notFound()
        return ResponseEntity.ok().header("Content-Type", "image/svg+xml")
            .header("X-Content-Type-Options", "nosniff").body(content.image())
    }

    private fun positiveId(value: String) = value.takeIf { it.matches(Regex("[1-9][0-9]{0,18}")) }?.toLongOrNull()
    private fun success(message: String, result: Any) = ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, message, result))
    private fun invalidRequest() = ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))
    private fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))

    private fun CatalogJangdan.detail(): JangdanDetailResult {
        val soundUrl = "/api/jangdans/$id/audio".takeIf { previews.hasAudio(id) }
        val attributedSource = if (soundUrl == null) source else listOfNotNull(
            source, "음원: 국립국악원 국악기 디지털 음원 장구 단음(공공누리 제1유형)을 편집한 미리듣기",
        ).joinToString("\n")
        return JangdanDetailResult(
            id.toString(), name, defaultBpm, bpmUnit, JangdanPatternResult(pattern.beats, pattern.group), attributedSource, soundUrl, patternCellEighths,
        )
    }
}

data class InstrumentListResult(val items: List<InstrumentItem>)
data class InstrumentItem(val instrumentId: String, val code: String, val name: String, val pitchToleranceCent: Int,
    val rangeLowPitchId: String, val rangeHighPitchId: String)
data class PitchListResult(val items: List<PitchItem>)
data class PitchItem(val pitchId: String, val yul: String, val octave: Int, val hanja: String, val hangul: String,
    val semitone: Int, val description: String?, val soundUrl: String?)
data class FingeringListResult(val items: List<FingeringItem>, val structureImageUrl: String?)
data class FingeringItem(val fingeringId: String, val instrumentId: String, val pitchId: String,
    val imageUrl: String?, val description: String?, val pattern: FingeringPattern?)
data class JangdanListResult(val items: List<JangdanItem>)
data class JangdanItem(val jangdanId: String, val name: String, val defaultBpm: Int, val bpmUnit: String,
    val beatCount: Int, val groupCount: Int, val patternCellEighths: Int?, val pattern: JangdanPatternResult)
data class JangdanDetailResult(val jangdanId: String, val name: String, val defaultBpm: Int, val bpmUnit: String,
    val pattern: JangdanPatternResult, val source: String?, val soundUrl: String?, val patternCellEighths: Int?)
data class JangdanPatternResult(val beats: List<String>, val group: List<Int>)
data class NotationSymbolListResult(val items: List<NotationSymbolItem>)
data class NotationSymbolItem(val symbolId: String, val symbol: String, val name: String, val description: String,
    val imageUrl: String?, val source: String? = null, val descriptionStatus: String? = null, val imageLicense: String? = null)
