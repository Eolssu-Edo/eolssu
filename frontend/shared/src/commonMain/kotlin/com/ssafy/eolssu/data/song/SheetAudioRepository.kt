package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.audio.SheetAudioPerformance
import com.ssafy.eolssu.core.network.ApiError
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** SHT-13 `status`. 서버가 QUEUED → RUNNING → DONE/FAILED 순서로 바꾼다. */
enum class SheetAudioStatus {
    Queued, Running, Done, Failed;

    /** 아직 만드는 중이라 `pollAfterMs` 뒤에 다시 물어야 한다. */
    val inProgress: Boolean get() = this == Queued || this == Running

    companion object {
        fun fromCode(code: String): SheetAudioStatus? = when (code) {
            "QUEUED" -> Queued
            "RUNNING" -> Running
            "DONE" -> Done
            "FAILED" -> Failed
            else -> null
        }
    }
}

/** SHT-13 `failureCode`. FAILED일 때만 오고, 모르는 값은 [Unknown]이다. SHT-12로 다시 요청할 수 있다. */
enum class SheetAudioFailure {
    Timeout, TooLarge, ServerError, Expired, Unknown;

    companion object {
        fun fromCode(code: String?): SheetAudioFailure = when (code) {
            "TIMEOUT" -> Timeout
            "TOO_LARGE" -> TooLarge
            "SERVER_ERROR" -> ServerError
            "AUDIO_EXPIRED" -> Expired
            else -> Unknown
        }
    }
}

/**
 * 임시 M4A 다운로드 정보. [url]은 로그나 화면에 적지 않는다.
 *
 * @property urlExpiresAt 링크와 다운로드 파일의 만료 시각(epoch ms).
 * @property sha256 M4A에만 있다. 기기에 저장한 파일이 최신인지 대조할 때 쓴다.
 * @property durationMs M4A에만 있다. 잔향을 포함한 길이.
 */
data class SheetAudioFile(
    val url: String,
    val urlExpiresAt: Long,
    val contentType: String,
    val byteSize: Long,
    val fileName: String,
    val sha256: String? = null,
    val durationMs: Long? = null,
    val performance: SheetAudioPerformance? = null,
) {
    /** [now]부터 [marginMs] 안에 만료되거나 이미 만료됐으면 true. */
    fun expiresWithin(now: Long, marginMs: Long): Boolean = now + marginMs >= urlExpiresAt
}

/**
 * 내 악보 음원 요청 상태(SHT-12·13 `result`). 완료한 최신 M4A에 다운로드 기한이 있다.
 *
 * @property scoreVersion 음원을 만든(만드는) 악보 버전. [currentScoreVersion]과 다르면 [outdated]다.
 * @property pollAfterMs QUEUED·RUNNING일 때 다음 조회까지 기다릴 시간. 그 밖에는 null.
 */
data class SheetAudio(
    val sheetId: String,
    val status: SheetAudioStatus,
    val scoreVersion: String,
    val currentScoreVersion: String,
    val outdated: Boolean,
    val instrumentId: String,
    val withJangdan: Boolean,
    val bpm: Int,
    val failureCode: SheetAudioFailure? = null,
    val pollAfterMs: Long? = null,
    val audio: SheetAudioFile? = null,
)

/** SHT-12 요청 본문. null인 칸은 보내지 않아 서버 기본값(단소, 악보 bpm)을 쓴다. [withJangdan]은 장단 없는 악보에 true면 400이다. */
data class SheetAudioSettings(val instrumentId: String? = null, val withJangdan: Boolean = false, val bpm: Int? = null)

/**
 * SHT-12 결과. 202 접수와 200(현재 버전·같은 설정의 음원이 이미 DONE)은 모두 [Accepted]이며 [Accepted.audio]의 상태로 구분한다.
 * 회원당 생성 중 1건, 분당 10회 제한이 있다.
 */
sealed interface SheetAudioRequest {
    data class Accepted(val audio: SheetAudio) : SheetAudioRequest

    /** 400 `EXPORT_TOO_LONG`: 악보와 bpm으로 계산한 연주 길이가 600초를 넘는다. */
    data object TooLong : SheetAudioRequest

    /** 400 `INVALID_REQUEST`: 장단 없는 악보에 장단 소리, bpm 범위 밖(30~160) 등. */
    data object InvalidRequest : SheetAudioRequest

    /** 404: 악보가 없거나 남의 것, 없는 악기. */
    data object NotFound : SheetAudioRequest

    /** 409 `STATE_CONFLICT`: 저장 전·빈 칸·확인 필요 칸·변환 중이라 음원을 만들 수 없다. */
    data object NotReady : SheetAudioRequest

    /** 409 `AUDIO_IN_PROGRESS`: 이 회원의 다른 음원을 만드는 중이다. */
    data object InProgress : SheetAudioRequest

    /** 429: 분당 요청 한도를 넘었다. */
    data object TooManyRequests : SheetAudioRequest

    /** 503 `AUDIO_UNAVAILABLE`, 그 밖의 오류, 응답 없음, 계약과 다른 응답. */
    data object Unavailable : SheetAudioRequest
}

/** SHT-13 결과. */
sealed interface SheetAudioLookup {
    data class Found(val audio: SheetAudio) : SheetAudioLookup

    /** 404: 악보가 없거나 음원을 요청한 적이 없다. 앱은 '음원 만들기'를 보인다. */
    data object None : SheetAudioLookup

    /** 503, 그 밖의 오류, 응답 없음, 계약과 다른 응답. */
    data object Unavailable : SheetAudioLookup
}

/** 내 악보 음원(SHT-12 생성 요청, SHT-13 상태·재생 URL 조회). 서명 URL은 저장하지 않고 응답마다 새로 받는다. */
interface SheetAudioRepository {
    suspend fun request(sheetId: String, settings: SheetAudioSettings = SheetAudioSettings()): SheetAudioRequest

    suspend fun status(sheetId: String): SheetAudioLookup
}

/** 응답 코드와 `result.errorCode`로만 분기한다. 서버 ID가 아닌 예시 악보는 서버에 묻지 않는다. */
class ApiSheetAudioRepository(
    private val client: HttpClient,
    private val member: SignedInMember? = null,
    server: ServerAddress? = null,
) : SheetAudioRepository {
    private val debugLoopback = server?.baseUrl?.takeIf { it.startsWith("http://127.0.0.1:") }?.let(::Url)

    override suspend fun request(sheetId: String, settings: SheetAudioSettings): SheetAudioRequest = member.inSession {
        if (!validCatalogId(sheetId)) return@inSession SheetAudioRequest.NotFound
        val response = client.sendOrNull {
            post("/api/users/me/sheets/$sheetId/audio") {
                contentType(ContentType.Application.Json)
                setBody(AudioRequestBody(settings.instrumentId, settings.withJangdan, settings.bpm))
            }
        } ?: return@inSession SheetAudioRequest.Unavailable
        when (response.status.value) {
            200, 202 -> response.audio(sheetId)?.let { SheetAudioRequest.Accepted(it) } ?: SheetAudioRequest.Unavailable
            400 -> if (response.errorCode() == "EXPORT_TOO_LONG") SheetAudioRequest.TooLong else SheetAudioRequest.InvalidRequest
            404 -> SheetAudioRequest.NotFound
            409 -> when (response.errorCode()) {
                "AUDIO_IN_PROGRESS" -> SheetAudioRequest.InProgress
                "STATE_CONFLICT" -> SheetAudioRequest.NotReady
                else -> SheetAudioRequest.Unavailable
            }
            429 -> SheetAudioRequest.TooManyRequests
            else -> SheetAudioRequest.Unavailable
        }
    }

    override suspend fun status(sheetId: String): SheetAudioLookup = member.inSession {
        if (!validCatalogId(sheetId)) return@inSession SheetAudioLookup.None
        val response = client.sendOrNull { get("/api/users/me/sheets/$sheetId/audio") } ?: return@inSession SheetAudioLookup.Unavailable
        when (response.status.value) {
            200 -> response.audio(sheetId)?.let { SheetAudioLookup.Found(it) } ?: SheetAudioLookup.Unavailable
            404 -> SheetAudioLookup.None
            else -> SheetAudioLookup.Unavailable
        }
    }

    private suspend fun HttpResponse.errorCode(): String? = apiResponse<ApiError>()?.result?.errorCode

    /** 계약과 다른 응답(모르는 상태, 다른 악보, 허용되지 않은 URL, 파일 없는 DONE)은 null이다. */
    private suspend fun HttpResponse.audio(sheetId: String): SheetAudio? {
        val dto = apiResponse<AudioDto>()?.takeIf { it.isSuccess }?.result ?: return null
        return dto.toModel(::allowedDownload)?.takeIf { it.sheetId == sheetId }
    }

    private fun allowedDownload(value: String): Boolean {
        if (isPublicAudioUrl(value)) return true
        val local = debugLoopback ?: return false
        if (!value.startsWith("http://127.0.0.1:") || value.any { it.isWhitespace() || it.code < 32 } || '#' in value) return false
        val url = runCatching { Url(value) }.getOrNull() ?: return false
        return url.host == local.host && url.port == local.port && url.user == null && url.password == null
    }
}

/** [withJangdan]은 기본값을 두지 않아 false도 그대로 보낸다. null인 칸은 생략된다. */
@Serializable
private class AudioRequestBody(val instrumentId: String? = null, val withJangdan: Boolean, val bpm: Int? = null)

@Serializable
private class AudioDto(
    val sheetId: String,
    val status: String,
    val scoreVersion: String,
    val currentScoreVersion: String,
    val outdated: Boolean,
    val instrumentId: String,
    val withJangdan: Boolean,
    val bpm: Int,
    val failureCode: String? = null,
    val pollAfterMs: Long? = null,
    val audio: AudioFileDto? = null,
) {
    fun toModel(allowedDownload: (String) -> Boolean): SheetAudio? {
        val status = SheetAudioStatus.fromCode(status) ?: return null
        val audio = audio?.let { it.toModel(allowedDownload) ?: return null }
        if (status == SheetAudioStatus.Done && audio == null) return null
        return SheetAudio(
            sheetId = sheetId,
            status = status,
            scoreVersion = scoreVersion,
            currentScoreVersion = currentScoreVersion,
            outdated = outdated,
            instrumentId = instrumentId,
            withJangdan = withJangdan,
            bpm = bpm,
            failureCode = if (status == SheetAudioStatus.Failed) SheetAudioFailure.fromCode(failureCode) else null,
            pollAfterMs = pollAfterMs?.takeIf { it > 0 },
            audio = audio,
        )
    }
}

@Serializable
private class AudioFileDto(
    val url: String,
    val urlExpiresAt: String,
    val contentType: String,
    val byteSize: Long,
    val fileName: String,
    val sha256: String? = null,
    val durationMs: Long? = null,
    val performance: SheetAudioPerformance? = null,
) {
    fun toModel(allowedDownload: (String) -> Boolean): SheetAudioFile? {
        if (!allowedDownload(url) || byteSize <= 0 || fileName.isBlank()) return null
        val expiresAt = runCatching { Instant.parse(urlExpiresAt).toEpochMilliseconds() }.getOrNull() ?: return null
        return SheetAudioFile(url, expiresAt, contentType, byteSize, fileName, sha256, durationMs, performance)
    }
}
