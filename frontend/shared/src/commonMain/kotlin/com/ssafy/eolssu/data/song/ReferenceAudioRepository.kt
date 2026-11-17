package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.audio.ReferenceAudioFileCache
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import kotlinx.serialization.Serializable

data class ReferenceAudioQuery(val datasetId: String, val category: String? = null, val register: String? = null, val yulmyeong: String? = null)

interface ReferenceAudioRepository {
    suspend fun clips(query: ReferenceAudioQuery): List<ReferenceAudioClip>

    /** 기기에 받아 둔 예시음 파일. null이면 원격 주소로 재생한다(웹·받기 실패). */
    suspend fun localFile(clip: ReferenceAudioClip): LocalAudioFile? = null
}

@Serializable
data class ReferenceAudioClip(val assetId: String, val datasetId: String, val labelId: String,
    val category: String, val technique: String, val register: String, val yulmyeong: String,
    val displayLabel: String, val durationMs: Int, val review: String, val soundUrl: String,
    val sha256: String = "", val contentType: String = "")

class ApiReferenceAudioRepository(
    private val client: HttpClient,
    private val server: ServerAddress,
    private val fileCache: ReferenceAudioFileCache? = null,
) : ReferenceAudioRepository {
    override suspend fun localFile(clip: ReferenceAudioClip): LocalAudioFile? =
        fileCache?.fileFor(clip.soundUrl, clip.sha256, clip.contentType)

    override suspend fun clips(query: ReferenceAudioQuery): List<ReferenceAudioClip> {
        if (query.datasetId !in datasets || (query.category != null && query.category !in categories) ||
            !validFilter(query.register) || !validFilter(query.yulmyeong)) throw CatalogUnavailable()
        val response = client.sendOrNull {
            get("/api/reference-audios") {
                parameter("datasetId", query.datasetId)
                query.category?.let { parameter("category", it) }
                query.register?.let { parameter("register", it) }
                query.yulmyeong?.let { parameter("yulmyeong", it) }
            }
        } ?: throw CatalogUnavailable()
        val clips = response.takeIf { it.status.value == 200 }?.apiResponse<ClipList>()
            ?.takeIf { it.isSuccess }?.result?.items ?: throw CatalogUnavailable()
        if (clips.map { it.assetId }.distinct().size != clips.size) throw CatalogUnavailable()
        return clips.map { clip ->
            if (!assetId.matches(clip.assetId) || clip.datasetId != query.datasetId ||
                clip.category !in categories || (query.category != null && clip.category != query.category) ||
                (query.register != null && clip.register != query.register) ||
                (query.yulmyeong != null && clip.yulmyeong != query.yulmyeong) ||
                listOf(clip.labelId, clip.technique, clip.register, clip.yulmyeong, clip.displayLabel).any(String::isBlank) ||
                clip.durationMs <= 0 || clip.review !in setOf("inferred", "reviewed") ||
                clip.soundUrl != "/api/reference-audios/${clip.assetId}/play") throw CatalogUnavailable()
            val url = URLBuilder(server.baseUrl)
            if (url.protocol.name !in setOf("https", "http") || url.host.isBlank() || url.user != null || url.password != null) throw CatalogUnavailable()
            url.encodedPath = clip.soundUrl
            url.parameters.clear()
            url.fragment = ""
            clip.copy(soundUrl = url.buildString())
        }
    }

    private fun validFilter(value: String?) = value == null || (value.isNotBlank() && value.length <= 32 && value.none(Char::isISOControl))
    @Serializable private data class ClipList(val items: List<ReferenceAudioClip>)
    private companion object {
        val datasets = setOf("danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum")
        val categories = setOf("기본음계", "시김새")
        val assetId = Regex("[0-9a-f]{64}")
    }
}
