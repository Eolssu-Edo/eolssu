package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
data class ModelAudio(
    val songId: String,
    val scoreVersion: Int,
    val audioUrl: String,
    val bpm: Int,
    val bpmUnit: String,
    val durationMs: Long,
    val startOffsetMs: Long,
    val contentType: String,
    val sha256: String,
) {
    /** A current score must never play a different version or a differently defined beat. */
    fun matches(song: Song): Boolean = songId == song.id && scoreVersion == song.scoreVersion &&
        bpm == song.baseBpm && bpmUnit == song.score.timing?.bpmUnit?.name
}

interface ModelAudioRepository {
    /** null means no published matching audio. Transport/protocol failures throw [CatalogUnavailable]. */
    suspend fun get(songId: String): ModelAudio?
}

class ApiModelAudioRepository(private val client: HttpClient) : ModelAudioRepository {
    override suspend fun get(songId: String): ModelAudio? {
        if (!validCatalogId(songId)) throw CatalogUnavailable()
        val response = client.sendOrNull { get("/api/songs/$songId/model-audio") } ?: throw CatalogUnavailable()
        if (response.status.value == 404) return null
        if (response.status.value != 200) throw CatalogUnavailable()
        val audio = response.apiResponse<ModelAudio>()?.takeIf { it.isSuccess }?.result ?: throw CatalogUnavailable()
        if (audio.songId != songId || audio.scoreVersion <= 0 || audio.bpm !in 30..400 ||
            audio.bpmUnit !in setOf("QUARTER", "DOTTED_QUARTER") ||
            audio.durationMs !in 1..Int.MAX_VALUE.toLong() || audio.startOffsetMs !in 0 until audio.durationMs ||
            audio.contentType !in setOf("audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav") ||
            !audio.sha256.matches(Regex("[a-f0-9]{64}")) || !isPublicAudioUrl(audio.audioUrl)
        ) throw CatalogUnavailable()
        return audio
    }
}

internal fun isPublicAudioUrl(value: String): Boolean {
    if (!value.startsWith("https://") || value.any { it.isWhitespace() || it.code < 32 } || '#' in value) return false
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.protocol.name == "https" && url.host.isNotBlank() && url.user == null && url.password == null
}
