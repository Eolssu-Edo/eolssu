package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import com.ssafy.eolssu.data.song.SongRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * 2차 취약 구간 연습(RESULT-03 · PRACTICE-02~03 · RESULT-04)과 이전 연주 비교(COMPARE-01)의 데이터.
 * 바탕은 연습 세션이다(평가 결과의 평가 ID와 기록의 기록 ID가 모두 연습 세션 ID다). 계약은 `docs/specs/evaluation-detail-apis.md`.
 */
interface WeakSectionRepository {
    /** 세션 [sourceId]의 곡과 평가 결과(틀린 정간, EVA-01). 없거나 받지 못하면 null. */
    suspend fun source(sourceId: String): WeakSectionSource?

    /** 새 구간 녹음 전에 현재 공개 판본과 대조한다. null은 조회 실패다. */
    suspend fun isCurrent(source: WeakSectionSource): Boolean? = true

    /** 세션 [sessionId]의 추천 취약 구간(EVA-03, 추천 순서). 추천할 구간이 없으면 빈 목록, 받지 못하면 null. */
    suspend fun weakSections(sessionId: String): List<WeakSectionPick>?

    /** 구간 재평가 세션 [sessionId]의 결과(EVA-01)와 바탕 세션과의 비교(EVA-04). 결과를 받지 못하면 null. */
    suspend fun sectionResult(sessionId: String): SectionEvaluation?

    /** 기록 [recordId]와, 그보다 먼저 같은 곡을 처음부터 끝까지 연주한 기록 전부(REC-02). 받지 못하면 null. */
    suspend fun comparison(recordId: String): RecordComparison?

    /** 이번 기록 [currentId]와 견줄 이전 기록 [previousId]의 틀린 정간과 비교 결과(EVA-04). 기록을 받지 못하면 null. */
    suspend fun previous(currentId: String, previousId: String): PreviousRecord?
}

/** 취약 구간 연습의 바탕: 곡과 그 곡의 평가 결과. */
data class WeakSectionSource(val song: Song, val evaluation: EvaluationResult)

/** EVA-03 추천 구간 하나. 각 번호는 0부터이고 양 끝을 포함한다. [errorCount]는 그 각들 안의 틀린 정간 수다. */
data class WeakSectionPick(val startColumn: Int, val endColumn: Int, val errorCount: Int)

/**
 * EVA-04 비교 결과. 오류 수와 점수의 비교 가능 여부는 서로 독립적이며 점수 차이는 서버 값이다.
 *
 * @property comparable 악보 판본이 같아 오류 수를 견줄 수 있는지(errorComparable). 모델·BPM 차이는 오류 비교를 막지 않는다.
 * @property beforeErrors 비교 구간의 이전 틀린 음표 수. 이전 결과의 악보 판본이 다르면 null.
 * @property afterErrors 이번 틀린 음표 수.
 * @property deltaErrors 이번 − 이전. 견줄 수 없으면 null.
 * @property correctedCount 이전에 틀렸다가 이번에 맞은 음표 수.
 * @property startColumn·endColumn 이번 세션 구간의 각(0부터).
 */
data class SessionCheck(
    val comparable: Boolean,
    val reason: String?,
    val beforeErrors: Int?,
    val afterErrors: Int,
    val deltaErrors: Int?,
    val correctedCount: Int,
    val startColumn: Int?,
    val endColumn: Int?,
    val scoreComparable: Boolean = false,
    val totalScoreDelta: Int? = null,
    val notComparableReasons: List<String> = emptyList(),
    val bpmChanged: Boolean = false,
)

/** 비교 불러오기 결과. */
sealed interface CheckLoad {
    data class Loaded(val check: SessionCheck) : CheckLoad

    /** 서버가 이 두 연주는 비교할 수 없다고 했다(400: 다른 곡·이번 구간을 담지 않은 이전 연주 등). */
    data object Rejected : CheckLoad

    data object Failed : CheckLoad
}

/**
 * 구간 재평가 결과(RESULT-04). 점수는 서버 값을 그대로 쓰고 화면에서 다시 계산하지 않는다.
 *
 * @property check 바탕 세션과의 오류 수 비교.
 */
data class SectionEvaluation(
    val song: Song,
    val sessionId: String,
    val bpm: Int,
    val total: Int,
    val metrics: List<MetricScore>,
    val check: CheckLoad,
)

/**
 * 이전 연주 비교(COMPARE-01). [previous]는 이번 기록보다 먼저 연주한 같은 곡 전체 연주 기록 전부로, 최신순이다.
 *
 * @property today 이번 기록의 날.
 */
data class RecordComparison(
    val song: Song,
    val current: PracticeRecord,
    val previous: List<PracticeRecord>,
    val today: KoreanDateTime,
)

/** 견줄 이전 기록(틀린 정간 포함)과 서버 비교 결과. */
data class PreviousRecord(val record: PracticeRecord, val check: CheckLoad)

/** 서버 API 구현. 곡·평가·기록은 이미 쓰는 저장소를 거친다. */
class ApiWeakSectionRepository(
    private val client: HttpClient,
    private val evaluations: EvaluationRepository,
    private val songs: SongRepository,
    private val records: PracticeRecordRepository,
    private val member: SignedInMember? = null,
) : WeakSectionRepository {
    override suspend fun source(sourceId: String): WeakSectionSource? = guarded { loadSource(sourceId) }

    override suspend fun isCurrent(source: WeakSectionSource): Boolean? = guarded {
        val current = songs.getPublishedScoreVersion(source.song.id) ?: return@guarded null
        current == source.song.scoreVersion
    }

    override suspend fun weakSections(sessionId: String): List<WeakSectionPick>? = member.inSession {
        val response = client.sendOrNull { get("/api/practice-sessions/$sessionId/weak-sections") { parameter("limit", 1) } } ?: return@inSession null
        if (response.status != HttpStatusCode.OK) return@inSession null
        response.apiResponse<WeakSectionsDto>()?.result?.items?.map { WeakSectionPick(it.startColumnIdx, it.endColumnIdx, it.errorCount) }
    }

    override suspend fun sectionResult(sessionId: String): SectionEvaluation? = guarded {
        val (song, evaluation) = loadSource(sessionId) ?: return@guarded null
        SectionEvaluation(song, sessionId, evaluation.bpm, evaluation.total, evaluation.metrics, check(sessionId, baseline = null))
    }

    private suspend fun loadSource(sessionId: String): WeakSectionSource? {
        val screen = evaluations.resultForScreen(sessionId, initialSong = null) ?: return null
        val (evaluation, loadedSong) = screen.loadDetails() ?: return null
        // 악보를 함께 반환하지 않는 저장소에서는 평가 응답이 가리키는 판본을 직접 구한다.
        val song = loadedSong ?: run {
            val version = evaluation.scoreVersion
            if (version == null) songs.getSong(evaluation.songId) else songs.getSong(evaluation.songId, version)
        }
        return song?.let { WeakSectionSource(it, evaluation) }
    }

    override suspend fun comparison(recordId: String): RecordComparison? = guarded {
        if (!records.refresh()) return@guarded null
        val current = records.detail(recordId) ?: return@guarded null
        val song = (if (current.scoreVersion == null) songs.getSong(current.songId) else songs.getSong(current.songId, current.scoreVersion)) ?: return@guarded null
        // 기간 제한 없이 이번 기록 이전의 같은 곡 전체 연주를 모두 고를 수 있다. 이후 기록은 서버 비교가 거절한다(400).
        val previous = records.records.value
            .filter { it.id != current.id && it.songId == current.songId && it.section == null }
            .filter { it.recordedAtMillis < current.recordedAtMillis }
            .sortedByDescending { it.recordedAtMillis }
        RecordComparison(song, current.withScore(song), previous, current.recordedAt)
    }

    override suspend fun previous(currentId: String, previousId: String): PreviousRecord? = guarded {
        val record = records.detail(previousId) ?: return@guarded null
        val song = (if (record.scoreVersion == null) songs.getSong(record.songId) else songs.getSong(record.songId, record.scoreVersion)) ?: return@guarded null
        PreviousRecord(record.withScore(song), check(currentId, baseline = previousId))
    }

    /** EVA-04. [baseline]이 없으면 서버는 세션을 만들 때 넘긴 기준 세션과 견준다. */
    private suspend fun check(sessionId: String, baseline: String?): CheckLoad = member.inSession {
        val response = client.sendOrNull {
            get("/api/practice-sessions/$sessionId/comparison") { baseline?.let { parameter("baselineSessionId", it) } }
        } ?: return@inSession CheckLoad.Failed
        when (response.status) {
            HttpStatusCode.OK -> response.apiResponse<ComparisonDto>()?.result?.toModel()?.let(CheckLoad::Loaded) ?: CheckLoad.Failed
            HttpStatusCode.BadRequest -> CheckLoad.Rejected
            else -> CheckLoad.Failed
        }
    }

    /** 다른 저장소가 연결 오류를 예외로 던지면 받지 못한 것으로 본다. */
    private suspend fun <T> guarded(block: suspend () -> T?): T? = try {
        member.inSession(block)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    @Serializable
    private class WeakSectionsDto(val items: List<WeakItemDto> = emptyList())

    @Serializable
    private class WeakItemDto(val startColumnIdx: Int, val endColumnIdx: Int, val errorCount: Int = 0)

    @Serializable
    private class ComparisonDto(
        val comparable: Boolean = false,
        val reason: String? = null,
        val errorComparable: Boolean? = null,
        val scoreComparable: Boolean = false,
        val notComparableReasons: List<String> = emptyList(),
        val bpmChanged: Boolean = false,
        val startColumnIdx: Int? = null,
        val endColumnIdx: Int? = null,
        val before: CountDto? = null,
        val after: CountDto = CountDto(),
        val delta: CountDto? = null,
        val correctedNoteIndices: List<Int> = emptyList(),
    ) {
        fun toModel(): SessionCheck? {
            if (after.errorCount == null || after.errorCount < 0) return null
            return SessionCheck(
            comparable = errorComparable ?: comparable,
            reason = reason ?: notComparableReasons.takeIf { errorComparable == false }?.joinToString(),
            beforeErrors = before?.errorCount,
            afterErrors = after.errorCount,
            deltaErrors = delta?.errorCount,
            correctedCount = correctedNoteIndices.size,
            startColumn = startColumnIdx,
            endColumn = endColumnIdx,
            scoreComparable = scoreComparable,
            totalScoreDelta = delta?.totalScore.takeIf { scoreComparable },
            notComparableReasons = notComparableReasons,
            bpmChanged = bpmChanged,
        )
        }
    }

    @Serializable
    private class CountDto(val errorCount: Int? = null, val totalScore: Int? = null)
}
