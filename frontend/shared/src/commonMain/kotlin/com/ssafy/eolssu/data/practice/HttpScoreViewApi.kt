package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * 체류 기록 API(GRO-04)를 앱 공통 HTTP 클라이언트로 보낸다. 구간 키는 `Idempotency-Key` 헤더,
 * 시각은 UTC ISO-8601 문자열이다(`docs/specs/practice-time.md`의 '체류 기록 API'). 같은 구간은 늘 같은 본문이 된다.
 */
class HttpScoreViewApi(private val client: HttpClient) : ScoreViewApi {
    override suspend fun post(segment: PracticeTimeSegment): Int? = client.sendOrNull {
        post("/api/users/me/score-views") {
            header(IDEMPOTENCY_KEY, segment.key)
            contentType(ContentType.Application.Json)
            setBody(
                ScoreViewRequest(
                    songId = segment.songId,
                    startedAt = Instant.fromEpochMilliseconds(segment.startedAtMillis).toString(),
                    endedAt = Instant.fromEpochMilliseconds(segment.endedAtMillis).toString(),
                    visibleSeconds = segment.visibleSeconds,
                    countedSeconds = segment.countedSeconds,
                ),
            )
        }
    }?.status?.value

    @Serializable
    private class ScoreViewRequest(
        val songId: String,
        val startedAt: String,
        val endedAt: String,
        val visibleSeconds: Int,
        val countedSeconds: Int,
    )

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
    }
}
