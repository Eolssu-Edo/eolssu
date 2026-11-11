package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.PracticeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 연습 기록. 서버 기록 API(REC-01~04, `docs/specs/practice-records.md`)는 [HttpPracticeRecordRepository]가 쓰고,
 * [UnavailablePracticeRecordRepository]는 기록 API가 없는 환경을, [SamplePracticeRecordRepository]는 격리된 테스트를 위한 것이다.
 */
interface PracticeRecordRepository {
    /** 기록 조회 구현을 사용할 수 있는지. 기록 수나 개별 네트워크 요청의 성공 여부가 아니다. */
    val available: Boolean get() = true

    /** 최신순 기록. */
    val records: StateFlow<List<PracticeRecord>>

    /** 평가 결과를 전체 연주 기록으로 저장한다. 같은 결과는 한 번만 저장된다. */
    suspend fun saveEvaluation(result: EvaluationResult, songTitle: String): PracticeRecord

    suspend fun updateMemo(recordId: String, memo: String)

    /** 서버에서 기록 목록을 다시 받는다. 받지 못하면 false이고 [records]는 그대로다. */
    suspend fun refresh(): Boolean = true

    /**
     * 기록 상세: 항목 점수·원본 음 판정·메모·다시 듣기 여부. 오류 위치는 당시 악보를 받은 뒤 [PracticeRecord.withScore]로 채운다. 없거나 지운 기록이면 null.
     * 응답을 받지 못하면 예외를 던진다.
     */
    suspend fun detail(recordId: String): PracticeRecord? = records.value.firstOrNull { it.id == recordId }

    /** 녹음을 다시 듣는 임시 주소(REC-03). */
    suspend fun playback(recordId: String): RecordPlayback = RecordPlayback.Unavailable

    /** 기록과 녹음을 지운다(REC-04). 이미 지운 기록(404)도 [RecordDeleteOutcome.Deleted]다. */
    suspend fun delete(recordId: String): RecordDeleteOutcome = RecordDeleteOutcome.Failed
}

/** 녹음 다시 듣기 주소. */
sealed interface RecordPlayback {
    /** [url]은 [expiresAtMillis](epoch ms)까지 쓸 수 있다. 지나면 다시 받는다. */
    data class Ready(val url: String, val expiresAtMillis: Long, val durationMs: Long?) : RecordPlayback

    /** 녹음이 없거나 지운 기록(404). */
    data object Unavailable : RecordPlayback

    /** 서버가 지금 녹음을 내줄 수 없음(503, 로컬 녹음 저장소 모드 포함). */
    data object Busy : RecordPlayback

    /** 응답을 받지 못했거나 예상하지 못한 응답. */
    data object Failed : RecordPlayback
}

enum class RecordDeleteOutcome {
    Deleted,

    /** 녹음 저장소 장애(503). 아무것도 지우지 않았으니 다시 시도한다. */
    Busy,
    Failed,
}

/** 와이어프레임 예시 기록 4개로 시작한다. 새로 저장한 기록이 맨 위에 온다. */
class SamplePracticeRecordRepository : PracticeRecordRepository {
    private val _records = MutableStateFlow(SampleRecords)
    override val records: StateFlow<List<PracticeRecord>> = _records.asStateFlow()

    override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String): PracticeRecord {
        val id = "record-${result.id}"
        _records.value.firstOrNull { it.id == id }?.let { return it }
        val record = PracticeRecord(
            id = id,
            songId = result.songId,
            songTitle = songTitle,
            section = null,
            repeats = null,
            recordedAt = result.recordedAt,
            recordedSeconds = result.recordedSeconds,
            bpm = result.bpm,
            score = result.total,
            metrics = result.metrics,
            memo = "",
            errors = result.errors,
            eventErrors = result.eventErrors,
        )
        _records.update { listOf(record) + it }
        return record
    }

    override suspend fun updateMemo(recordId: String, memo: String) {
        _records.update { list -> list.map { if (it.id == recordId) it.copy(memo = memo) else it } }
    }
}

private val SampleRecords = listOf(
    PracticeRecord(
        "sample-1", "arirang", "아리랑", section = "3–4각", repeats = 4, recordedAt = KoreanDateTime(2026, 10, 18, 20, 5),
        recordedSeconds = 6, bpm = 48, score = 79, metrics = metrics(82, 76, 80), memo = "", errors = emptyList(),
    ),
    PracticeRecord(
        "sample-2", "arirang", "아리랑", section = null, repeats = null, recordedAt = KoreanDateTime(2026, 10, 18, 19, 52),
        recordedSeconds = 48, bpm = 60, score = 76, metrics = metrics(84, 70, 78), memo = "", errors = SampleErrors,
    ),
    PracticeRecord(
        "sample-3", "doraji", "도라지타령", section = null, repeats = null, recordedAt = KoreanDateTime(2026, 10, 15, 21, 10),
        recordedSeconds = 22, bpm = 66, score = 71, metrics = metrics(68, 74, 72), memo = "청황 음이 낮게 나옴", errors = emptyList(),
    ),
    PracticeRecord(
        "sample-4", "arirang", "아리랑", section = null, repeats = null, recordedAt = KoreanDateTime(2026, 10, 12, 18, 40),
        recordedSeconds = 48, bpm = 54, score = 68, metrics = metrics(80, 70, 78), memo = "", errors = SampleErrors,
    ),
)

private fun metrics(pitch: Int, rhythm: Int, length: Int) = listOf(
    MetricScore(EvaluationMetric.Pitch, pitch),
    MetricScore(EvaluationMetric.Rhythm, rhythm),
    MetricScore(EvaluationMetric.Length, length),
)
