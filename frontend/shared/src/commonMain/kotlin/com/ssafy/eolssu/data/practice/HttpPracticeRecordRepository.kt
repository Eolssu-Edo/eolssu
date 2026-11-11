package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.measureLoad
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.ensureAccountSession
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * 서버 연습 기록 API(REC-01~04, `docs/specs/practice-records.md`). 기록 ID는 연습 세션 ID다.
 *
 * - 목록(REC-02)은 곡별로 나눠 주므로, 곡마다 받아 최신순 한 목록으로 합친다. 목록에는 항목 점수·오류 위치가 없다.
 * - 상세는 PRA-03(메모·다시 듣기 여부, 처음 열면 서버가 NEW를 끈다)과 평가(EVA-01·02, [evaluations])를 합친다.
 * - 저장(REC-01)은 연주 결과 화면에서 적은 메모를 같은 요청에 실어 한 번에 저장한다(메모가 없으면 본문 `{}`). 기록 상세의 메모 수정도 같은 요청에 `memo`를 싣는다(앞뒤 공백은 서버가 자르고 빈 값은 지운다).
 */
class HttpPracticeRecordRepository(
    private val client: HttpClient,
    private val evaluations: EvaluationRepository,
    private val member: SignedInMember? = null,
) : PracticeRecordRepository {
    private val _records = MutableStateFlow<List<PracticeRecord>>(emptyList())
    override val records: StateFlow<List<PracticeRecord>> = _records.asStateFlow()

    init { member?.onChange { _records.value = emptyList() } }

    override suspend fun refresh(): Boolean = member.inSession {
        val first = list(songId = null, page = 0) ?: return@inSession false
        val items = mutableListOf<RecordItem>()
        for (song in first.songs) {
            var page = 0
            var current = if (song.songId == first.songId) first else list(song.songId, 0) ?: return@inSession false
            while (true) {
                items += current.items
                page++
                if (page >= current.totalPages) break
                current = list(song.songId, page) ?: return@inSession false
            }
        }
        ensureAccountSession()
        _records.value = items
            .distinctBy { it.sessionId }
            .sortedByDescending { Instant.parse(it.startedAt).toEpochMilliseconds() }
            .map { it.toRecord() }
        return@inSession true
    }

    private suspend fun list(songId: String?, page: Int): RecordList? =
        measureLoad(PerfTarget.RECORD_PAGE, "${songId.orEmpty()}:$page") { loadPage(songId, page) }

    private suspend fun loadPage(songId: String?, page: Int): RecordList? {
        val response = client.sendOrNull {
            get("/api/users/me/practice-records") {
                songId?.let { parameter("songId", it) }
                parameter("sort", "NEWEST")
                parameter("page", page)
                parameter("size", PAGE_SIZE)
            }
        } ?: return null
        if (response.status != HttpStatusCode.OK) return null
        return response.apiResponse<RecordList>()?.result
    }

    override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String): PracticeRecord = member.inSession {
        val saved = putRecord(result.id, MemoBody(memo.ifBlank { null }))
        val record = saved.toRecord(songTitle).copy(
            metrics = result.metrics,
            errors = result.errors,
            eventErrors = result.eventErrors,
            notes = result.notes,
            scoreVersion = saved.scoreVersion ?: result.scoreVersion,
            recordedSeconds = saved.recordingDurationMs?.let { (it / 1000.0).roundToInt() } ?: result.recordedSeconds,
            recordingAvailable = saved.recordingDurationMs != null,
            isNew = true,
        )
        ensureAccountSession()
        _records.update { list -> listOf(record) + list.filterNot { it.id == record.id } }
        return@inSession record
    }

    override suspend fun updateMemo(recordId: String, memo: String) = member.inSession {
        val saved = putRecord(recordId, MemoBody(memo))
        ensureAccountSession()
        _records.update { list -> list.map { if (it.id == recordId) it.copy(memo = saved.memo.orEmpty()) else it } }
    }

    /** REC-01. 200이 아니거나 응답을 받지 못하면 예외를 던진다(결과 화면은 '저장하지 못함'으로 보인다). */
    private suspend fun putRecord(sessionId: String, body: MemoBody): SavedRecord {
        val response = client.sendOrNull {
            put("/api/practice-sessions/$sessionId/record") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } ?: error("기록 저장 응답 없음")
        if (response.status != HttpStatusCode.OK) error("기록 저장 응답: ${response.status}")
        return checkNotNull(response.apiResponse<SavedRecord>()?.result) { "기록 저장 형식이 다름" }
    }

    override suspend fun detail(recordId: String): PracticeRecord? = member.inSession {
        val response = client.sendOrNull { get("/api/practice-sessions/$recordId") } ?: error("기록 상세 응답 없음")
        if (response.status == HttpStatusCode.NotFound) {
            ensureAccountSession()
            _records.update { list -> list.filterNot { it.id == recordId } }
            return@inSession null
        }
        if (response.status != HttpStatusCode.OK) error("기록 상세 응답: ${response.status}")
        val session = checkNotNull(response.apiResponse<SessionDetail>()?.result) { "기록 상세 형식이 다름" }
        check(session.sessionId == recordId) { "기록 세션 식별자가 다름" }
        val base = records.value.firstOrNull { it.id == recordId }
        // 점수와 원본 판정만 받는다. 악보 조회 실패가 메모·녹음까지 숨기지 않게 한다.
        val evaluation = checkNotNull(evaluations.resultForScreen(recordId, initialSong = null)?.scores) { "기록 평가 없음" }
        check(evaluation.id == recordId && evaluation.songId == session.songId &&
            (session.scoreVersion == null || evaluation.scoreVersion == session.scoreVersion)) { "기록 평가 식별자·판본이 다름" }
        val startedMillis = session.startedAt?.let { Instant.parse(it).toEpochMilliseconds() }
            ?: base?.recordedAtMillis
        val record = PracticeRecord(
            id = recordId, songId = session.songId,
            songTitle = session.songTitle ?: base?.songTitle ?: "연습 기록",
            section = base?.section ?: if (base == null) "구간 정보 없음" else null,
            repeats = base?.repeats,
            recordedAt = startedMillis?.let(KoreanDateTime::fromEpochMillis) ?: evaluation.recordedAt,
            recordedAtMillis = startedMillis ?: ((evaluation.recordedAt.epochDay * 24 + evaluation.recordedAt.hour - 9) * 60 + evaluation.recordedAt.minute) * 60_000,
            recordedSeconds = evaluation.recordedSeconds,
            bpm = session.bpm ?: evaluation.bpm,
            score = evaluation.total, metrics = evaluation.metrics,
            errors = evaluation.errors, eventErrors = evaluation.eventErrors, notes = evaluation.notes,
            scoreVersion = session.scoreVersion ?: evaluation.scoreVersion ?: base?.scoreVersion,
            startNoteIndex = session.startNoteIdx, endNoteIndex = session.endNoteIdx,
            memo = session.memo.orEmpty(), recordingAvailable = session.recordingAvailable, isNew = false,
        )
        ensureAccountSession()
        _records.update { list -> list.map { if (it.id == recordId) it.copy(memo = record.memo, isNew = false) else it } }
        return@inSession record
    }

    override suspend fun playback(recordId: String): RecordPlayback = member.inSession {
        val response = client.sendOrNull { get("/api/practice-sessions/$recordId/recording") } ?: return@inSession RecordPlayback.Failed
        return@inSession when (response.status) {
            HttpStatusCode.OK -> response.apiResponse<Playback>()?.result
                ?.let { RecordPlayback.Ready(it.playbackUrl, Instant.parse(it.urlExpiresAt).toEpochMilliseconds(), it.durationMs) }
                ?: RecordPlayback.Failed
            HttpStatusCode.NotFound -> RecordPlayback.Unavailable
            HttpStatusCode.ServiceUnavailable -> RecordPlayback.Busy
            else -> RecordPlayback.Failed
        }
    }

    override suspend fun delete(recordId: String): RecordDeleteOutcome = member.inSession {
        val response = client.sendOrNull { delete("/api/practice-sessions/$recordId/record") } ?: return@inSession RecordDeleteOutcome.Failed
        return@inSession when (response.status) {
            // 다시 보낸 삭제의 404는 '이미 지워짐'이다(REC-04).
            HttpStatusCode.OK, HttpStatusCode.NotFound -> {
                ensureAccountSession()
            _records.update { list -> list.filterNot { it.id == recordId } }
                RecordDeleteOutcome.Deleted
            }
            HttpStatusCode.ServiceUnavailable -> RecordDeleteOutcome.Busy
            else -> RecordDeleteOutcome.Failed
        }
    }

    private fun RecordItem.toRecord(): PracticeRecord {
        val recordedAtMillis = Instant.parse(startedAt).toEpochMilliseconds()
        return PracticeRecord(
            id = sessionId,
            songId = songId,
            songTitle = title,
            section = section(isFullSong, startColumnIdx, endColumnIdx),
            repeats = null,
            recordedAt = KoreanDateTime.fromEpochMillis(recordedAtMillis),
            recordedAtMillis = recordedAtMillis,
            recordedSeconds = recordingDurationMs?.let { (it / 1000.0).roundToInt() } ?: 0,
            bpm = bpm,
            score = totalScore,
            metrics = emptyList(),
            memo = memo.orEmpty(),
            errors = emptyList(),
            recordingAvailable = recordingAvailable,
            isNew = isNew,
            scoreVersion = scoreVersion,
        )
    }

    private fun SavedRecord.toRecord(fallbackTitle: String): PracticeRecord {
        val recordedAtMillis = Instant.parse(startedAt).toEpochMilliseconds()
        return PracticeRecord(
            id = sessionId,
            songId = songId,
            songTitle = songTitle ?: fallbackTitle,
            scoreVersion = scoreVersion,
            section = section(isFullSong, startColumnIdx, endColumnIdx),
            repeats = null,
            recordedAt = KoreanDateTime.fromEpochMillis(recordedAtMillis),
            recordedAtMillis = recordedAtMillis,
            recordedSeconds = 0,
            bpm = bpm,
            score = totalScore,
            metrics = emptyList(),
            memo = memo.orEmpty(),
            errors = emptyList(),
        )
    }

    private companion object {
        const val PAGE_SIZE = 100

        /** 구간 이름: 전체 곡이면 null, 각 번호(0부터)가 있으면 ‘3–4각’, 악보 버전이 달라 각을 모르면 ‘일부’. */
        fun section(isFullSong: Boolean, start: Int?, end: Int?): String? = when {
            isFullSong -> null
            start == null || end == null -> "일부"
            start == end -> "${start + 1}각"
            else -> "${start + 1}–${end + 1}각"
        }
    }

    /** REC-01 본문. `memo`가 null이면 보내지 않아(공통 JSON `explicitNulls = false`) 저장만 한다. */
    @Serializable
    private class MemoBody(val memo: String? = null)

    @Serializable
    private class SavedRecord(
        val sessionId: String,
        val songId: String,
        val songTitle: String? = null,
        val scoreVersion: Int? = null,
        val memo: String? = null,
        val isFullSong: Boolean = true,
        val startColumnIdx: Int? = null,
        val endColumnIdx: Int? = null,
        val startedAt: String,
        val recordingDurationMs: Long? = null,
        val bpm: Int,
        val totalScore: Int,
    )

    @Serializable
    private class RecordList(
        val songs: List<SongGroup> = emptyList(),
        val songId: String? = null,
        val items: List<RecordItem> = emptyList(),
        val totalPages: Int = 0,
    )

    @Serializable
    private class SongGroup(val songId: String)

    @Serializable
    private class RecordItem(
        val sessionId: String,
        val songId: String,
        val title: String,
        val scoreVersion: Int? = null,
        val isFullSong: Boolean = true,
        val startColumnIdx: Int? = null,
        val endColumnIdx: Int? = null,
        val recordingDurationMs: Long? = null,
        val bpm: Int,
        val totalScore: Int,
        val startedAt: String,
        val recordingAvailable: Boolean = false,
        val memo: String? = null,
        val isNew: Boolean = false,
    )

    @Serializable
    private class SessionDetail(
        val sessionId: String,
        val songId: String,
        val songTitle: String? = null,
        val scoreVersion: Int? = null,
        val bpm: Int? = null,
        val startedAt: String? = null,
        val startNoteIdx: Int? = null,
        val endNoteIdx: Int? = null,
        val recordingAvailable: Boolean = false,
        val memo: String? = null,
    )

    @Serializable
    private class Playback(val playbackUrl: String, val urlExpiresAt: String, val durationMs: Long? = null)
}
