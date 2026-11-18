package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.network.NetworkResult
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendCatching
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** 연습 세션 생성 결과. */
sealed interface SessionStart {
    /** 세션이 생겼다(201). 같은 키로 다시 보내 이미 있던 세션을 받은 경우도 같다. [startedAtMillis]는 서버가 기록한 시작 시각이다. */
    data class Created(val sessionId: String, val startedAtMillis: Long, val memberId: String) : SessionStart

    /** 응답을 받지 못했거나 503·500·401. 같은 키로 다시 만들 수 있다. */
    data object Unreachable : SessionStart

    /** 404(평가할 수 없는 곡)·400·409. 같은 요청은 다시 보내도 거부된다. */
    data object Rejected : SessionStart
}

/**
 * 구간 재평가 세션의 범위와 기준 세션(PRA-01 `startNoteIdx`·`endNoteIdx`·`parentSessionId`).
 * 음표 번호는 악보에서 율명 음만 연주 순서로 센 번호(0부터, 양 끝 포함)이고, [parentSessionId]는 다시 연습하는 바탕이 된 평가 세션이다.
 */
data class SessionSection(val startNoteIdx: Int, val endNoteIdx: Int, val parentSessionId: String)

/** 연습 세션(PRA-01). 녹음을 올리기 전에 만든다. */
interface PracticeSessionRepository {
    /**
     * 곡 [songId]를 [bpm]으로 연주한 세션을 만든다. [idempotencyKey]는 녹음 하나에 하나이며, 응답을 놓쳐 다시 만들 때도 같은 키를 쓴다.
     * 서버는 같은 키·같은 본문이면 처음 만든 세션을 그대로 돌려준다. [section]이 있으면 그 구간만 연주한 재평가 세션이다.
     */
    suspend fun create(songId: String, bpm: Int, idempotencyKey: String, section: SessionSection? = null): SessionStart

    /** 녹음에 사용한 판본을 지정한다. 대역은 기존 생성 경계를 재사용할 수 있다. */
    suspend fun create(songId: String, bpm: Int, idempotencyKey: String, expectedScoreVersion: Int,
        section: SessionSection? = null): SessionStart = if (expectedScoreVersion > 0)
            create(songId, bpm, idempotencyKey, section) else SessionStart.Rejected
}

/** [PracticeSessionRepository]를 `POST /api/practice-sessions`로 보낸다. 구간이 없으면 곡 전체 연주를 만든다. */
class HttpPracticeSessionRepository(private val client: HttpClient, private val member: SignedInMember) : PracticeSessionRepository {
    override suspend fun create(songId: String, bpm: Int, idempotencyKey: String, section: SessionSection?): SessionStart =
        createRequest(songId, bpm, idempotencyKey, section, null)

    override suspend fun create(songId: String, bpm: Int, idempotencyKey: String, expectedScoreVersion: Int,
        section: SessionSection?): SessionStart {
        if (expectedScoreVersion <= 0) return SessionStart.Rejected
        return createRequest(songId, bpm, idempotencyKey, section, expectedScoreVersion)
    }

    private suspend fun createRequest(songId: String, bpm: Int, idempotencyKey: String,
        section: SessionSection?, expectedScoreVersion: Int?): SessionStart = member.whileSignedIn { memberId ->
        if (memberId.isNullOrBlank()) return@whileSignedIn SessionStart.Unreachable
        val sent = client.sendCatching {
            post("/api/practice-sessions") {
                header(IDEMPOTENCY_KEY, idempotencyKey)
                contentType(ContentType.Application.Json)
                if (section == null) setBody(CreateRequest(songId, bpm, expectedScoreVersion))
                else setBody(SectionRequest(songId, bpm, section.startNoteIdx, section.endNoteIdx, section.parentSessionId, expectedScoreVersion))
            }
        }
        val response = (sent as? NetworkResult.Received)?.response ?: return@whileSignedIn SessionStart.Unreachable
        when (response.status) {
            HttpStatusCode.Created -> response.apiResponse<CreatedSession>()?.result
                ?.let { SessionStart.Created(it.sessionId, Instant.parse(it.startedAt).toEpochMilliseconds(), memberId) }
                ?: SessionStart.Unreachable
            HttpStatusCode.BadRequest, HttpStatusCode.NotFound, HttpStatusCode.Conflict -> SessionStart.Rejected
            else -> SessionStart.Unreachable
        }
    }

    @Serializable
    private class CreateRequest(val songId: String, val bpm: Int, val expectedScoreVersion: Int? = null)

    @Serializable
    private class SectionRequest(val songId: String, val bpm: Int, val startNoteIdx: Int, val endNoteIdx: Int,
        val parentSessionId: String, val expectedScoreVersion: Int? = null)

    @Serializable
    private class CreatedSession(val sessionId: String, val startedAt: String)

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
    }
}
