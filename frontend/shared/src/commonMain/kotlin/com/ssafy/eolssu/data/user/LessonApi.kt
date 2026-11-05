package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * 기초부터 배우기 단계·진도 API(LRN-01·02·03, `docs/specs/learning-progress-apis.md`). 응답을 받지 못했거나 실패 응답이면 null.
 * 트랙은 학습 화면 악기 식별자(`GuideInstrument.id`)로 받아 서버 코드(`DANSO` 등)로 바꿔 보낸다.
 */
interface LessonApi {
    /** LRN-01 트랙의 공개 단계(순서대로)와 본인 완료 표시. */
    suspend fun track(trackId: String): LessonTrack?

    /** LRN-02 단계 상세(공통 문구 + 트랙별 내용). */
    suspend fun lesson(lessonId: String): LessonDetail?

    /** LRN-03 완료 표시(true)·해제(false). 성공하면 서버에 남은 표시를 돌려준다. */
    suspend fun setCompleted(lessonId: String, completed: Boolean): Boolean?
}

/** 학습 화면 악기 식별자 → 서버 트랙 코드(danso → DANSO, daegeum-jeongak → DAEGEUM_JEONGAK). */
fun lessonTrackCode(trackId: String): String = trackId.uppercase().replace('-', '_')

/** LRN-01 결과. [completedCount]는 서버가 센 필수 단계 완료 수다. */
data class LessonTrack(val items: List<LessonItem>, val completedCount: Int, val requiredCount: Int)

/** 단계 목록의 한 단계. [judgeType]은 READ·PITCH·BEAT·LISTEN. */
data class LessonItem(
    val lessonId: String,
    val seq: Int,
    val title: String,
    val judgeType: String,
    val optional: Boolean,
    val completed: Boolean,
)

/**
 * LRN-02 단계 상세. 해당하지 않는 목록은 비어 있고 [pass]·[pitchNote]는 null이다.
 *
 * @property pitchNames 율명 한자. 3단계는 처음 낼 음, 4단계는 익힐 순서, 5·8단계는 기본 5음(낮은 음부터).
 * @property parts 1단계 악기 부분 이름.
 * @property ranges 6단계 새로 내는 음역.
 * @property songs 8단계 연습곡 후보.
 */
data class LessonDetail(
    val lessonId: String,
    val seq: Int,
    val title: String,
    val judgeType: String,
    val optional: Boolean,
    val goal: String,
    val why: String,
    val source: String,
    val tasks: List<LessonText>,
    val pass: LessonText?,
    val pitchNames: List<String>,
    val pitchNote: LessonPitchNote?,
    val parts: List<String>,
    val ranges: List<LessonRange>,
    val songs: List<LessonSong>,
)

/** 제목과 설명(없으면 null). 할 일과 통과 기준이 쓴다. */
data class LessonText(val title: String, val description: String?)

/** 4단계 익힐 순서 설명. [caution] 앞 '※ '는 앱이 붙인다. */
data class LessonPitchNote(val text: String, val caution: String?)

data class LessonRange(val blow: Blow, val label: String, val description: String, val firstPitch: String)

data class LessonSong(val title: String, val meta: String)

/** 앱 공통 HTTP 클라이언트로 부른다. */
class HttpLessonApi(private val client: HttpClient, private val member: SignedInMember? = null) : LessonApi {
    override suspend fun track(trackId: String): LessonTrack? = member.inSession {
        val response = client.sendOrNull { get("/api/lessons") { parameter("track", lessonTrackCode(trackId)) } } ?: return@inSession null
        if (!response.status.isSuccess()) return@inSession null
        response.apiResponse<TrackDto>()?.result?.toModel()
    }

    override suspend fun lesson(lessonId: String): LessonDetail? = member.inSession {
        val response = client.sendOrNull { get("/api/lessons/$lessonId") } ?: return@inSession null
        if (!response.status.isSuccess()) return@inSession null
        response.apiResponse<DetailDto>()?.result?.toModel()
    }

    override suspend fun setCompleted(lessonId: String, completed: Boolean): Boolean? = member.inSession {
        val response = client.sendOrNull {
            put("/api/lessons/$lessonId/completion") {
                contentType(ContentType.Application.Json)
                setBody(CompletionBody(completed))
            }
        } ?: return@inSession null
        if (!response.status.isSuccess()) return@inSession null
        response.apiResponse<CompletionDto>()?.result?.completed
    }

    @Serializable
    private class CompletionBody(val completed: Boolean)

    @Serializable
    private class CompletionDto(val completed: Boolean)

    @Serializable
    private class TrackDto(val completedCount: Int = 0, val requiredCount: Int = 0, val items: List<ItemDto> = emptyList()) {
        fun toModel() = LessonTrack(items.map { it.toModel() }.sortedBy { it.seq }, completedCount, requiredCount)
    }

    @Serializable
    private class ItemDto(
        val lessonId: String,
        val seq: Int,
        val title: String,
        val judgeType: String,
        val optional: Boolean = false,
        val completedAt: String? = null,
    ) {
        fun toModel() = LessonItem(lessonId, seq, title, judgeType, optional, completedAt != null)
    }

    @Serializable
    private class DetailDto(
        val lessonId: String,
        val seq: Int,
        val title: String,
        val judgeType: String,
        val optional: Boolean = false,
        val goal: String = "",
        val why: String = "",
        val source: String = "",
        val tasks: List<TextDto> = emptyList(),
        val passCriteria: TextDto? = null,
        val pitchNames: List<String> = emptyList(),
        val pitchNote: PitchNoteDto? = null,
        val parts: List<String> = emptyList(),
        val ranges: List<RangeDto> = emptyList(),
        val songs: List<SongDto> = emptyList(),
    ) {
        fun toModel() = LessonDetail(
            lessonId, seq, title, judgeType, optional, goal, why, source,
            tasks = tasks.map { it.toModel() },
            pass = passCriteria?.toModel(),
            pitchNames = pitchNames,
            pitchNote = pitchNote?.let { LessonPitchNote(it.text, it.caution) },
            parts = parts,
            ranges = ranges.mapNotNull { it.toModel() },
            songs = songs.map { LessonSong(it.title, it.meta) },
        )
    }

    @Serializable
    private class TextDto(val title: String, val description: String? = null) {
        fun toModel() = LessonText(title, description)
    }

    @Serializable
    private class PitchNoteDto(val text: String, val caution: String? = null)

    /** 모르는 취법은 뺀다. */
    @Serializable
    private class RangeDto(val blow: String, val label: String, val description: String, val firstPitch: String) {
        fun toModel(): LessonRange? {
            val blow = when (blow) {
                "HIGH" -> Blow.High
                "LOW" -> Blow.Low
                else -> return null
            }
            return LessonRange(blow, label, description, firstPitch)
        }
    }

    @Serializable
    private class SongDto(val title: String, val meta: String = "")
}
