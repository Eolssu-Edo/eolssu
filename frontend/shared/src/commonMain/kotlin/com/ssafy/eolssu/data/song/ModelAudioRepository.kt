package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.diagnostics.PerfSource
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.ScorePerformance
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.coroutines.coroutineContext

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
    val performanceUrl: String? = null,
    val sourceCommonSha256: String? = null,
    @Transient val performance: ScorePerformance? = null,
    @Transient val sheetPlayback: SheetPlayback? = null,
    val playbackRevisionSupported: Boolean = false,
) {
    /** A current score must never play a different version or a differently defined beat. */
    fun matches(song: Song): Boolean = songId == song.id && scoreVersion == song.scoreVersion &&
        (sheetPlayback != null || bpm == song.baseBpm) && bpmUnit == song.score.timing?.bpmUnit?.name &&
        (performance?.let { it.matches(song) && it.audioSha256 == sha256 &&
            it.audioDurationMs == durationMs && it.audioStartOffsetMs == startOffsetMs } ?: true)
}

data class SheetPlayback(val withJangdan: Boolean, val localFile: LocalAudioFile)

interface ModelAudioRepository {
    /** null means no published matching audio. Transport/protocol failures throw [CatalogUnavailable]. */
    suspend fun get(song: Song): ModelAudio?
}

class ApiModelAudioRepository(private val client: HttpClient) : ModelAudioRepository {
    private val cacheLock = Mutex()
    private val verifiedBySong = linkedMapOf<String, ModelAudio>()

    override suspend fun get(song: Song): ModelAudio? {
        val songId = song.id
        if (!validCatalogId(songId)) throw CatalogUnavailable()
        // The score does not identify the currently published audio. Check metadata before reusing its timeline.
        val response = client.sendOrNull { get("/api/songs/$songId/model-audio") } ?: throw CatalogUnavailable()
        if (response.status.value == 404) return null
        if (response.status.value != 200) throw CatalogUnavailable()
        val audio = response.apiResponse<ModelAudio>()?.takeIf { it.isSuccess }?.result ?: throw CatalogUnavailable()
        if (audio.songId != songId || audio.scoreVersion <= 0 || audio.bpm !in 30..400 ||
            audio.bpmUnit !in setOf("QUARTER", "DOTTED_QUARTER") ||
            audio.durationMs !in 1..Int.MAX_VALUE.toLong() || audio.startOffsetMs !in 0 until audio.durationMs ||
            audio.contentType !in setOf("audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav") ||
            !audio.sha256.matches(Sha256) || !audio.matches(song)
        ) throw CatalogUnavailable()
        val playPath = "/api/songs/$songId/model-audio/play"
        val usesServerPlayback = audio.audioUrl == playPath
        val canPinPlayback = usesServerPlayback && audio.playbackRevisionSupported
        val resolved = audio.copy(audioUrl = when {
            usesServerPlayback -> sameServerPlaybackUrl(
                response.call.request.url, playPath, audio.takeIf { canPinPlayback },
            )
            isPublicAudioUrl(audio.audioUrl) -> audio.audioUrl
            else -> throw CatalogUnavailable()
        })
        val performancePath = audio.performanceUrl
        val sourceHash = audio.sourceCommonSha256
        if (performancePath == null && sourceHash == null) return resolved
        if (performancePath != "/api/songs/$songId/model-audio/performance" || sourceHash == null || !Sha256.matches(sourceHash)) {
            throw CatalogUnavailable()
        }
        // Each new player load can get a fresh signed redirect for the verified publication.
        if (canPinPlayback) {
            val cached = cacheLock.withLock {
                verifiedBySong[songId]?.takeIf { it.copy(performance = null) == resolved && it.matches(song) }
            }
            if (cached != null) {
                coroutineContext[PerformanceVisit]?.reuse(PerfTarget.MODEL_AUDIO_TIMELINE, songId, PerfSource.RETAINED, song.scoreVersion)
                return cached
            }
        }
        val performanceResponse = client.sendOrNull { get(performancePath) } ?: throw CatalogUnavailable()
        if (performanceResponse.status.value != 200) throw CatalogUnavailable()
        val json = performanceResponse.bodyAsText()
        if (json.length > MaxPerformanceChars) throw CatalogUnavailable()
        val verified = try {
            resolved.withPerformance(json, song, sourceHash)
        } catch (_: IllegalArgumentException) {
            throw CatalogUnavailable()
        }
        if (canPinPlayback) {
            cacheLock.withLock {
                verifiedBySong.remove(songId)
                verifiedBySong[songId] = verified
                if (verifiedBySong.size > MaxCachedPerformances) {
                    verifiedBySong.remove(verifiedBySong.keys.first())
                }
            }
        }
        return verified
    }
}

/** Resolve only the exact server-generated path; protocol-relative/foreign paths are never fetched. */
private fun sameServerPlaybackUrl(base: Url, path: String, revision: ModelAudio?): String {
    if (base.protocol.name != "https" && !(base.protocol.name == "http" && base.host in setOf("localhost", "127.0.0.1", "::1", "[::1]"))) {
        throw CatalogUnavailable()
    }
    return URLBuilder(base).apply {
        encodedPath = path
        parameters.clear()
        fragment = ""
        revision?.let {
            parameters.append("expectedScoreVersion", it.scoreVersion.toString())
            parameters.append("expectedAudioSha256", it.sha256)
        }
    }.buildString()
}

private val Sha256 = Regex("[a-f0-9]{64}")
private const val MaxPerformanceChars = 2_000_000
private const val MaxCachedPerformances = 4

internal fun isPublicAudioUrl(value: String): Boolean {
    if (!value.startsWith("https://") || value.any { it.isWhitespace() || it.code < 32 } || '#' in value) return false
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.protocol.name == "https" && url.host.isNotBlank() && url.user == null && url.password == null
}
