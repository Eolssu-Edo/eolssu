package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.song.SampleSongs

/**
 * 2차 취약 구간 연습(RESULT-03 · PRACTICE-02~03 · RESULT-04)과 이전 연주 비교(COMPARE-01)의 데이터.
 * 서버 API가 아직 없어 [SampleWeakSectionRepository]의 예시만 있다(S15P31E102-455). API를 붙일 때 같은 인터페이스로 구현을 더한다.
 */
interface WeakSectionRepository {
    /** 평가·기록 [sourceId]의 곡과 평가 결과(틀린 정간). 없으면 null. */
    suspend fun source(sourceId: String): WeakSectionSource?

    /** [section]을 [bpm]으로 다시 연주한 재평가 결과. 없으면 null. */
    suspend fun evaluateSection(sourceId: String, section: ScoreSection, bpm: Int): SectionEvaluation?

    /** 기록 [recordId]와 같은 곡·같은 악기의 최근 2주 이전 기록. 없으면 null. */
    suspend fun comparison(recordId: String): RecordComparison?
}

/** 취약 구간 연습의 바탕: 곡과 그 곡의 평가 결과. */
data class WeakSectionSource(val song: Song, val evaluation: EvaluationResult)

/**
 * 구간 재평가 결과(RESULT-04). 점수는 서버 값을 그대로 쓰고 화면에서 다시 계산하지 않는다.
 *
 * @property previousTotal 이전에 이 구간을 연주했을 때 점수. 처음이면 null.
 * @property sample 예시 결과인지.
 */
data class SectionEvaluation(
    val song: Song,
    val section: ScoreSection,
    val bpm: Int,
    val total: Int,
    val previousTotal: Int?,
    val metrics: List<SectionMetricChange>,
    val sample: Boolean,
)

/** 항목 점수의 이전 → 이번. */
data class SectionMetricChange(val metric: EvaluationMetric, val before: Int?, val after: Int)

/**
 * 이전 연주 비교(COMPARE-01). [previous]는 최근 2주 같은 곡 기록으로, 최신순이다.
 *
 * @property today 달력의 마지막 날(이번 기록의 날).
 */
data class RecordComparison(
    val song: Song,
    val current: PracticeRecord,
    val previous: List<PracticeRecord>,
    val today: KoreanDateTime,
    val sample: Boolean,
)

/**
 * 와이어프레임 예시(아리랑 84점, 틀린 정간 14개 · 3–4각에 9개). [sourceId]·[recordId]와 관계없이 같은 예시를 준다.
 * 재평가는 고른 구간·속도만 반영하고 점수는 예시 값이다.
 */
class SampleWeakSectionRepository : WeakSectionRepository {
    private val song: Song get() = SampleSongs.first { it.id == "arirang" }

    override suspend fun source(sourceId: String): WeakSectionSource = WeakSectionSource(
        song = song,
        evaluation = EvaluationResult(
            id = sourceId.ifEmpty { "sample-weak" },
            songId = song.id,
            total = 84,
            feedback = "",
            metrics = listOf(MetricScore(EvaluationMetric.Pitch, 88), MetricScore(EvaluationMetric.Rhythm, 80), MetricScore(EvaluationMetric.Length, 84)),
            errors = SampleErrors,
            bpm = 60,
            recordedSeconds = 48,
            recordedAt = SampleToday,
            sample = true,
        ),
    )

    override suspend fun evaluateSection(sourceId: String, section: ScoreSection, bpm: Int): SectionEvaluation = SectionEvaluation(
        song = song,
        section = section,
        bpm = bpm,
        total = 85,
        previousTotal = 68,
        metrics = listOf(
            SectionMetricChange(EvaluationMetric.Pitch, 70, 86),
            SectionMetricChange(EvaluationMetric.Rhythm, 62, 84),
            SectionMetricChange(EvaluationMetric.Length, 72, 85),
        ),
        sample = true,
    )

    override suspend fun comparison(recordId: String): RecordComparison = RecordComparison(
        song = song,
        current = sampleRecord(recordId.ifEmpty { "sample-now" }, SampleToday, score = 84, bpm = 60, cells = listOf(20, 22, 29, 30, 37, 44)),
        previous = listOf(
            sampleRecord("sample-prev-1", KoreanDateTime(2026, 10, 13, 19, 5), 68, 48, listOf(3, 11, 20, 22, 24, 25, 29, 30, 31, 33, 40, 47)),
            sampleRecord("sample-prev-2", KoreanDateTime(2026, 10, 13, 8, 20), 63, 48, listOf(3, 5, 11, 20, 22, 24, 25, 29, 30, 31, 33, 40, 47)),
            sampleRecord("sample-prev-3", KoreanDateTime(2026, 10, 10, 21, 10), 61, 48, listOf(3, 5, 11, 13, 20, 22, 24, 25, 29, 30, 31, 33, 38, 40, 47, 50)),
            sampleRecord(
                "sample-prev-4", KoreanDateTime(2026, 10, 8, 18, 12), 55, 40,
                listOf(1, 3, 5, 11, 13, 15, 20, 22, 24, 25, 29, 30, 31, 33, 38, 40, 44, 47, 50),
            ),
        ),
        today = SampleToday,
        sample = true,
    )

    private fun sampleRecord(id: String, at: KoreanDateTime, score: Int, bpm: Int, cells: List<Int>) = PracticeRecord(
        id = id, songId = song.id, songTitle = song.title, section = null, repeats = null, recordedAt = at,
        recordedSeconds = 48, bpm = bpm, score = score, metrics = emptyList(), memo = "",
        // 9정간 각 기준 칸 번호. 비교 화면은 종류를 나누지 않으므로 음정으로 둔다.
        errors = cells.map { ScoreError(column = it / 9, row = it % 9, metric = EvaluationMetric.Pitch) },
    )

    private companion object {
        /** 예시의 이번 기록 시각(10월 20일 화요일 오후 3:20). */
        val SampleToday = KoreanDateTime(2026, 10, 20, 15, 20)
    }
}
