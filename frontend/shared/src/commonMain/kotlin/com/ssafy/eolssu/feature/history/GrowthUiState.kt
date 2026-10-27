package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.PracticeRecord

/** 연습 기록지 기간. */
enum class GrowthRange { Week, Month }

/** 요약 칸 하나(연습 시간·횟수·평균 점수). */
data class GrowthTile(val label: String, val value: String, val sub: String)

/** 막대 하나(요일·주 단위 연습 시간, 분). */
data class GrowthBar(val label: String, val minutes: Int)

/** 연습 통계. 통계 API(`GET /me/stats`)가 생기기 전까지 와이어프레임 예시 값을 쓴다. */
data class GrowthStats(
    val period: String,
    val tiles: List<GrowthTile>,
    val bars: List<GrowthBar>,
    val highlightBar: Int,
)

internal val SampleWeekStats = GrowthStats(
    period = "10월 3주차",
    tiles = listOf(GrowthTile("연습 시간", "98분", "지난주보다 +23"), GrowthTile("연습 횟수", "7회", "2일 연속"), GrowthTile("평균 점수", "79점", "+6점")),
    bars = listOf(GrowthBar("월", 12), GrowthBar("화", 20), GrowthBar("수", 0), GrowthBar("목", 15), GrowthBar("금", 8), GrowthBar("토", 25), GrowthBar("일", 18)),
    highlightBar = 1,
)

internal val SampleMonthStats = GrowthStats(
    period = "10월",
    tiles = listOf(GrowthTile("연습 시간", "193분", "지난달보다 +41"), GrowthTile("연습 횟수", "14회", "주 3.5회"), GrowthTile("평균 점수", "76점", "+5점")),
    bars = listOf(GrowthBar("1주", 40), GrowthBar("2주", 55), GrowthBar("3주", 98), GrowthBar("4주", 0)),
    highlightBar = 2,
)

/**
 * GROWTH-01 상태. 기간별 통계는 예시 값이고, 점수 변화·항목 성장·곡별 최고 점수는 저장한 기록에서 계산한다.
 *
 * @property records 최신순 기록.
 */
data class GrowthUiState(
    val range: GrowthRange = GrowthRange.Week,
    val records: List<PracticeRecord> = emptyList(),
) {
    val stats: GrowthStats get() = if (range == GrowthRange.Week) SampleWeekStats else SampleMonthStats

    /** 최근 5회(오래된 것부터) 점수. */
    val trend: List<PracticeRecord> get() = records.take(5).reversed()

    /** 항목별 첫 기록 → 최근 기록 점수. 기록이 없으면 빈 목록. */
    val metricGrowth: List<Triple<EvaluationMetric, Int, Int>>
        get() {
            val first = records.lastOrNull()?.metrics?.associate { it.metric to it.score } ?: return emptyList()
            val last = records.first().metrics.associate { it.metric to it.score }
            return EvaluationMetric.entries.mapNotNull { m -> val a = first[m]; val b = last[m]; if (a != null && b != null) Triple(m, a, b) else null }
        }

    /** 곡별 최고 점수(최근에 연습한 곡 먼저). */
    val bestBySong: List<Pair<String, Int>>
        get() = records.groupBy { it.songTitle }.map { (title, list) -> title to list.maxOf { it.score } }
}
