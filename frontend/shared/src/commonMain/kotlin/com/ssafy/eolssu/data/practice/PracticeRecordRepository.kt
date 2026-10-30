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
 * 연습 기록. 와이어프레임 API 정리의 `POST /records`·`GET /records`·`PATCH /records/{id}`에 대응한다.
 * 기록 API 연결 전에는 운영 DI가 [UnavailablePracticeRecordRepository]를 사용한다.
 * [SamplePracticeRecordRepository]는 격리된 테스트용이며 사용자 계정의 기록으로 표시하지 않는다.
 */
interface PracticeRecordRepository {
    /** 기록 조회 구현을 사용할 수 있는지. 기록 수나 개별 네트워크 요청의 성공 여부가 아니다. */
    val available: Boolean get() = true

    /** 최신순 기록. */
    val records: StateFlow<List<PracticeRecord>>

    /** 평가 결과를 전체 연주 기록으로 저장한다. 같은 결과는 한 번만 저장된다. */
    suspend fun saveEvaluation(result: EvaluationResult, songTitle: String): PracticeRecord

    suspend fun updateMemo(recordId: String, memo: String)
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
