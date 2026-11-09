package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import kotlinx.serialization.Serializable

/** CAT-06~10. 서버의 등록 자료만 반환한다. 학습 순서·운지 구멍 위치를 추정하지 않는다. */
interface ReferenceCatalogRepository {
    suspend fun pitches(instrumentId: String): List<CatalogPitch>

    /** 악기를 가리지 않은 등록 음 전체(CAT-06 `instrumentId` 생략). 내 악보 저장은 이 목록의 율명·옥타브만 받는다. */
    suspend fun allPitches(): List<CatalogPitch> = throw CatalogUnavailable()
    suspend fun fingerings(instrumentId: String, pitchId: String? = null): CatalogFingerings
    suspend fun jangdans(): List<CatalogJangdanSummary>
    suspend fun jangdan(jangdanId: String): CatalogJangdanDetail?
    suspend fun symbols(): List<CatalogNotationSymbol>
}

class ApiReferenceCatalogRepository(private val client: HttpClient) : ReferenceCatalogRepository {
    override suspend fun pitches(instrumentId: String): List<CatalogPitch> {
        requireCatalogId(instrumentId)
        return loadPitches(instrumentId)
    }

    override suspend fun allPitches(): List<CatalogPitch> = loadPitches(null)

    private suspend fun loadPitches(instrumentId: String?): List<CatalogPitch> {
        val response = client.sendOrNull { get("/api/pitches") { instrumentId?.let { parameter("instrumentId", it) } } }
            ?: throw CatalogUnavailable()
        val items = response.takeIf { it.status.value == 200 }?.apiResponse<PitchList>()
            ?.takeIf { it.isSuccess }?.result?.items ?: throw CatalogUnavailable()
        checkIds(items.map { it.pitchId })
        if (items.any { it.yul.isBlank() || it.hanja.isBlank() || it.hangul.isBlank() || !safeCatalogLink(it.soundUrl) }) throw CatalogUnavailable()
        return items
    }

    override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings {
        requireCatalogId(instrumentId)
        pitchId?.let(::requireCatalogId)
        val response = client.sendOrNull {
            get("/api/instruments/$instrumentId/fingerings") { pitchId?.let { parameter("pitchId", it) } }
        } ?: throw CatalogUnavailable()
        val result = response.takeIf { it.status.value == 200 }?.apiResponse<CatalogFingerings>()
            ?.takeIf { it.isSuccess }?.result ?: throw CatalogUnavailable()
        checkIds(result.items.map { it.fingeringId })
        if (!safeCatalogLink(result.structureImageUrl) || result.items.any {
                it.instrumentId != instrumentId || !validCatalogId(it.pitchId) ||
                    (pitchId != null && it.pitchId != pitchId) || !safeCatalogLink(it.imageUrl) ||
                    (it.pattern != null && !it.pattern.valid())
            }) throw CatalogUnavailable()
        return result
    }

    override suspend fun jangdans(): List<CatalogJangdanSummary> {
        val response = client.sendOrNull { get("/api/jangdans") } ?: throw CatalogUnavailable()
        val items = response.takeIf { it.status.value == 200 }?.apiResponse<JangdanList>()
            ?.takeIf { it.isSuccess }?.result?.items ?: throw CatalogUnavailable()
        checkIds(items.map { it.jangdanId })
        if (items.any { !validJangdan(it.name, it.defaultBpm, it.bpmUnit) || it.beatCount <= 0 || it.groupCount <= 0 }) throw CatalogUnavailable()
        return items
    }

    override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? {
        requireCatalogId(jangdanId)
        val response = client.sendOrNull { get("/api/jangdans/$jangdanId") } ?: throw CatalogUnavailable()
        if (response.status.value == 404) return null
        val result = response.takeIf { it.status.value == 200 }?.apiResponse<CatalogJangdanDetail>()
            ?.takeIf { it.isSuccess }?.result ?: throw CatalogUnavailable()
        if (result.jangdanId != jangdanId || !validJangdan(result.name, result.defaultBpm, result.bpmUnit) ||
            result.pattern.beats.isEmpty() || result.pattern.group.isEmpty() || result.pattern.group.any { it <= 0 } ||
            result.pattern.group.sumOf { it.toLong() } != result.pattern.beats.size.toLong()
        ) throw CatalogUnavailable()
        if (result.soundUrl == "/api/jangdans/$jangdanId/audio") {
            val url = URLBuilder(response.call.request.url)
            if (url.protocol.name !in setOf("http", "https") || url.host.isBlank() || url.user != null || url.password != null) throw CatalogUnavailable()
            url.encodedPath = result.soundUrl
            url.parameters.clear()
            url.fragment = ""
            return result.copy(soundUrl = url.buildString())
        }
        if (!safeCatalogLink(result.soundUrl)) throw CatalogUnavailable()
        return result
    }

    override suspend fun symbols(): List<CatalogNotationSymbol> {
        val response = client.sendOrNull { get("/api/notation-symbols") } ?: throw CatalogUnavailable()
        val items = response.takeIf { it.status.value == 200 }?.apiResponse<SymbolList>()
            ?.takeIf { it.isSuccess }?.result?.items ?: throw CatalogUnavailable()
        checkIds(items.map { it.symbolId })
        return items.map { item ->
            if (item.symbol.isBlank() || item.name.isBlank() ||
                (item.descriptionStatus != null && item.descriptionStatus !in setOf("DOCUMENTED", "EDUCATIONAL", "INFERRED")) ||
                (item.descriptionStatus != null && item.source.isNullOrBlank())
            ) throw CatalogUnavailable()
            val image = item.imageUrl
            if (image != null && image == "/api/notation-symbols/${item.symbolId}/image") {
                // Resolve only this item's fixed endpoint against the actual API request origin.
                val url = URLBuilder(response.call.request.url)
                if (url.protocol.name !in setOf("http", "https") || url.host.isBlank() || url.user != null || url.password != null) throw CatalogUnavailable()
                url.encodedPath = image
                url.parameters.clear()
                url.fragment = ""
                item.copy(imageUrl = url.buildString())
            } else {
                if (!safeCatalogLink(image)) throw CatalogUnavailable()
                item
            }
        }
    }

    private fun checkIds(ids: List<String>) { if (ids.any { !validCatalogId(it) } || ids.distinct().size != ids.size) throw CatalogUnavailable() }
    private fun requireCatalogId(id: String) { if (!validCatalogId(id)) throw CatalogUnavailable() }
    private fun validJangdan(name: String, bpm: Int, unit: String) = name.isNotBlank() && bpm in 30..400 && unit in setOf("QUARTER", "DOTTED_QUARTER")
    @Serializable private class PitchList(val items: List<CatalogPitch>)
    @Serializable private class JangdanList(val items: List<CatalogJangdanSummary>)
    @Serializable private class SymbolList(val items: List<CatalogNotationSymbol>)
}

/** 외부 자료 링크는 HTTPS만 열고 사용자 정보·fragment를 전달하지 않는다. */
internal fun safeCatalogLink(value: String?): Boolean = value == null || runCatching {
    val url = Url(value)
    url.protocol.name == "https" && url.host.isNotBlank() && url.user == null && url.password == null && url.fragment.isEmpty()
}.getOrDefault(false)

@Serializable data class CatalogPitch(val pitchId: String, val yul: String, val octave: Int, val hanja: String,
    val hangul: String, val semitone: Int, val description: String? = null, val soundUrl: String? = null)
@Serializable data class CatalogFingerings(val items: List<CatalogFingering>, val structureImageUrl: String? = null)
@Serializable data class CatalogFingering(val fingeringId: String, val instrumentId: String, val pitchId: String,
    val imageUrl: String? = null, val description: String? = null, val pattern: CatalogFingeringPattern? = null)
@Serializable enum class CatalogFingeringBlow { LOW, MID, HIGH }
@Serializable enum class CatalogFingeringHole { CLOSED, OPEN, HALF, UNKNOWN }
@Serializable data class CatalogFingeringPattern(
    val blow: CatalogFingeringBlow,
    val hanja: String,
    val name: String,
    val reading: String,
    val holes: List<CatalogFingeringHole>,
    val basic: Boolean,
    val source: String,
    val instrumentNote: String,
    val nominalHwangPitchClass: Int,
    val verificationStatus: String,
) {
    internal fun valid() = holes.size in 5..6 && listOf(hanja, name, reading, source, instrumentNote).all { it.isNotBlank() } &&
        nominalHwangPitchClass in 0..11 && verificationStatus == "BUILTIN_REFERENCE"
}
@Serializable data class CatalogJangdanSummary(val jangdanId: String, val name: String, val defaultBpm: Int,
    val bpmUnit: String, val beatCount: Int, val groupCount: Int)
@Serializable data class CatalogJangdanDetail(val jangdanId: String, val name: String, val defaultBpm: Int,
    val bpmUnit: String, val pattern: CatalogJangdanPattern, val source: String? = null, val soundUrl: String? = null)
@Serializable data class CatalogJangdanPattern(val beats: List<String>, val group: List<Int>)
@Serializable data class CatalogNotationSymbol(val symbolId: String, val symbol: String, val name: String,
    val description: String, val imageUrl: String? = null, val source: String? = null,
    val descriptionStatus: String? = null, val imageLicense: String? = null)
