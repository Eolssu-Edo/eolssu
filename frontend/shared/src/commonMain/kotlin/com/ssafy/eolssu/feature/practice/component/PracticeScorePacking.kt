package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.columnSplitAt
import com.ssafy.eolssu.core.ui.score.scoreStrips

internal val PracticeStackGap = 6.dp
private val PracticeStackRowPadding = 2.dp

/**
 * 한 각도 제 크기로 들어가지 않을 때 정간과 글자를 함께 줄이는 하한. 연주 중 세로 스크롤을 두지 않고 악보가 잘려서도 안 되므로
 * (사용자 결정) 글자가 작아지더라도 남은 높이에 끝까지 맞춘다. 0 이하의 크기만 막는다.
 */
private val PracticeMinFallbackCell = 1.dp

/**
 * 짧은 각을 글자를 줄여서라도 두 각씩 쌓을 때 허용하는 정간 하한. 이보다 작아지면 한 각씩 세로줄에 둔다.
 * S15P31E102-560: 14dp까지 줄이면 정간·글자가 너무 작아 24dp(기본 최소 정간 32dp의 75%)로 올렸다.
 * 이전 동작으로 되돌리려면 14.dp로 바꾼다.
 */
private val PracticeMinStackCell = 24.dp

/**
 * 휴대폰 연습 악보에서 위아래로 나뉜 정간(2·3줄)의 율명을 한 줄 높이의 이 비율까지 키운다([JeongganboMetrics.splitLineFill]).
 * 한 줄은 정간 높이의 절반이다(3줄 정간은 정간을 1.5배로 키운다). S15P31E102-560. 끄려면 0f로 바꾼다(나뉜 수로 정한 고정 크기).
 */
internal const val PracticeSplitLineFill = 0.8f

/**
 * 나뉜 정간이 있는 곡을 두 각 이상 쌓으려면 나뉜 정간의 율명이 이 크기(sp, 글꼴 배율 전) 이상이어야 한다. 못 미치면 한 각씩 둔다.
 * [PracticeSplitLineFill] 0.8에서는 정간 40dp 이상과 같다. S15P31E102-560. 이 조건을 끄려면 0f로 바꾼다.
 */
private const val PracticeMinSplitGlyph = 16f

/** 글자를 줄여서라도 두 각을 쌓는 짧은 각(아리랑·도라지타령의 세마치 3정간 등)의 최대 정간 수. */
private const val PracticeShortColumnCells = 4

/**
 * 한 각이 최소 정간 크기로 한 세로줄에 들어가지 않을 때 두 줄로 나눌 수 있는 각의 최소 정간 수(줄마다 3정간 이상). 짧은 각은 [PracticeShortColumnCells] 묶음 규칙을 따른다.
 * S15P31E102-654: 단소 아리랑(12정간 중 8개가 3소박)이 한 줄에 들어가려고 정간이 15dp 안팎까지 줄던 문제.
 */
private const val PracticeSplitColumnCells = 6

/** 이미 여러 표시 줄로 나눈 각은 기존 렌더링을 보존한다. 정간 수로 짧은 각을 단정하지 않는다. */
internal fun Score.supportsPracticeColumnPacking(jangdan: Jangdan? = null): Boolean =
    columns.isNotEmpty() && columns.all { it.isNotEmpty() } && scoreStrips(this, jangdan).all { it.strip == 0 }

internal data class PracticeScorePacking(
    val columnsPerStack: Int,
    val metrics: JeongganboMetrics,
    val cellHeights: Map<Pair<Int, Int>, Dp>,
    /** 연주 순서. 화면에서는 이 묶음 목록만 오른쪽부터 배치한다. */
    val columnGroups: List<List<Int>>,
    val height: Dp,
    /** 같은 가로줄에 놓이는 각들이 공유하는 세로 공간. */
    val stackRowHeights: List<Dp>,
    /** 한 각을 대강 경계에서 두 줄로 나눠 그린다([com.ssafy.eolssu.core.ui.score.JeongganboView]의 splitColumns). 이때 [columnsPerStack]은 1이다. */
    val splitColumns: Boolean = false,
)

/** 정간을 위아래로 나눈 줄 수. 소박 정보가 없으면 음 수로 본다. */
private fun practiceCellParts(cell: ScoreCell): Int =
    if (cell.units.isEmpty()) cell.notes.size else cell.units.map { it.row }.distinct().size

/**
 * 각의 실제 높이에 맞춰 위→아래로 쌓고, 들어가지 않는 다음 각은 왼쪽 세로줄로 넘긴다.
 *
 * [keepBaseCell]이면(휴대폰 연습 악보) 3소박 정간이 있거나 긴 각([PracticeSplitColumnCells] 이상)이 있는 곡은 기본 정간(보통 44dp·율명 21sp)을 지키고,
 * 3소박 정간은 그 × 1.5(66dp)·율명 17.6sp로 그린다(S15P31E102-1022, 3소박 없는 긴 각 곡까지 넓힌 것은 S15P31E102-1028).
 * 그 크기로 한 세로줄에 들어가지 않는 긴 각은 정간을 줄이는 대신 두 세로줄로 나누고, 나눠도 모자랄 때만 줄인다.
 * 같은 가로줄에 맞춰 칸을 늘려도 3소박 율명은 커지지 않는다([JeongganboMetrics.threeSplitGlyphCell]).
 */
internal fun practiceScorePacking(
    score: Score,
    metrics: JeongganboMetrics,
    availableHeight: Dp,
    fontScale: Float = 1f,
    jangdan: Jangdan? = null,
    keepBaseCell: Boolean = false,
): PracticeScorePacking {
    val scale = fontScale.coerceAtLeast(1f)
    val parts = ::practiceCellParts
    val expanded = score.columns.flatMapIndexed { column, cells ->
        cells.mapIndexedNotNull { row, cell -> (column to row).takeIf { parts(cell) >= 3 } }
    }.toSet()
    // 짧은 각만 있고 3소박도 없는 곡(단소 도라지타령 등)은 기존 묶음을 그대로 둔다. 3소박 곡과 긴 각 곡만 기본 정간을 지킨다.
    val keep = keepBaseCell && (expanded.isNotEmpty() || score.columns.any { it.size >= PracticeSplitColumnCells })
    // 위아래로 나뉜 정간이 있으면 한 줄이 정간의 절반이 되므로, 두 각 이상 쌓을 정간 높이를 그 줄의 율명 크기로 정한다.
    val split = score.columns.any { cells -> cells.any { parts(it) >= 2 } }
    val splitStackCell = run {
        val fill = metrics.splitLineFill
        val fixed = minOf(metrics.yulSizeFor(2).value, metrics.yulSizeFor(3).value)
        if (!split || fill <= 0f || fixed >= PracticeMinSplitGlyph) 0.dp else (PracticeMinSplitGlyph * 2 / fill).dp
    }
    val weights = score.columns.mapIndexed { column, cells ->
        cells.indices.sumOf { row -> if ((column to row) in expanded) 1.5 else 1.0 }.toFloat()
    }
    // 같은 가로줄의 최대 높이를 공유해야 1·4·7각 / 2·5·8각 / 3·6·9각의 시작점이 일치한다.
    fun rowWeights(count: Int): List<Float> = List(count) { row ->
        weights.filterIndexed { column, _ -> column % count == row }.maxOrNull() ?: 0f
    }

    var count = 1
    var fitted = metrics.cellHeight
    var textRatio = 1f
    var splitInTwo = false
    // 두 줄로 나눈 각의 줄별 무게(정간 수, 3줄 정간은 1.5). 각 순서대로 첫 줄·둘째 줄.
    val splitWeights = score.columns.mapIndexed { column, cells ->
        val at = columnSplitAt(cells.size, jangdan)
        listOf(0 until at, at until cells.size).map { rows ->
            rows.sumOf { row -> if ((column to row) in expanded) 1.5 else 1.0 }.toFloat()
        }
    }
    val packable = score.supportsPracticeColumnPacking(jangdan)
    if (packable && availableHeight.value.isFinite()) {
        // 글꼴 확대에서도 묶음을 유지한다. 글자도 같은 배율로 커지므로 정간 최소 크기는 배율 전 값으로 본다.
        // 정간 크기를 지키는 악보는 기본 정간보다 작게 줄이지 않는다(3소박 = 기본 정간 × 1.5).
        val minimum = if (keep) metrics.cellHeight else minOf(metrics.cellHeight, if (metrics.cellHeight > 44.dp) 44.dp else 32.dp)
        fun allowedFor(candidate: Int): Dp {
            val sharedWeight = rowWeights(candidate).sum()
            // 가로줄별 픽셀 반올림 여유와 악보 바깥 여백까지 포함해 맞춘다.
            val remaining = availableHeight - 14.dp - PracticeStackGap * (candidate - 1) -
                (metrics.headHeight + PracticeStackRowPadding) * candidate * scale
            return remaining / (sharedWeight * scale)
        }
        // 가장 짧은 각만 들어간다고 가정한 상한에서 시작해 전체 각·공유 가로줄 높이로 실제 배치를 결정한다.
        val minimumColumnHeight = (metrics.headHeight + PracticeStackRowPadding + minimum * weights.min()) * scale + PracticeStackGap
        val limit = minOf(score.columns.size, ((availableHeight - 14.dp + PracticeStackGap) / minimumColumnHeight).toInt())
        // 한 각만 둘 때는 기존 최소 정간을 쓰고, 두 각 이상 쌓을 때만 나뉜 정간의 글자 조건을 더한다.
        val found = (limit downTo 1).firstOrNull { allowedFor(it) >= if (it >= 2) maxOf(minimum, splitStackCell) else minimum }
        val shortColumns = score.columns.size > 1 && score.columns.maxOf { it.size } <= PracticeShortColumnCells
        val stackReadable = allowedFor(2).let { cell -> cell >= PracticeMinStackCell && cell >= splitStackCell }
        if ((found == null || found == 1) && shortColumns && stackReadable && !keep) {
            // 짧은 각은 한 줄로 늘어놓지 않고 1각 아래 2각을 둔다. 낮은 화면에서는 정간과 글자를 같은 비율로 줄인다.
            count = 2
            fitted = minOf(minimum, allowedFor(2))
            textRatio = fitted / minimum
        } else if (found != null) {
            count = found
            fitted = minOf(metrics.cellHeight, allowedFor(found))
        } else if (allowedFor(1) >= PracticeMinStackCell && !(keep && score.columns.maxOf { it.size } >= PracticeSplitColumnCells)) {
            // 최소 정간으로는 안 들어가도 읽을 수 있는 하한까지 줄여 들어가면 한 각을 한 줄로 둔다(S15P31E102-922 한오백년 12정간).
            // 정간과 글자를 같은 비율로 줄인다. 이보다 낮은 화면만 아래에서 두 줄로 나눈다.
            // 정간 크기를 지키는 악보는 줄이지 않고 아래에서 두 줄로 나눈다(S15P31E102-1022·1028).
            fitted = allowedFor(1)
            textRatio = fitted / minimum
        } else if (score.columns.maxOf { it.size } >= PracticeSplitColumnCells) {
            // 한 각도 최소 크기로 들어가지 않는 높이: 각을 대강 경계에서 두 줄로 나누고 각 번호는 첫 줄에만 둔다(S15P31E102-654).
            // 나눠도 모자라면 정간과 글자를 같은 비율로 줄인다.
            splitInTwo = true
            val longest = splitWeights.flatten().max()
            val allowed = (availableHeight - 14.dp - (metrics.headHeight + PracticeStackRowPadding) * scale) / (longest * scale)
            fitted = minOf(metrics.cellHeight, allowed).coerceAtLeast(PracticeMinFallbackCell)
            if (fitted < minimum) textRatio = fitted / minimum
        } else {
            // 한 각도 최소 크기로 들어가지 않는 낮은 화면: 세로 스크롤 대신 정간과 글자를 같은 비율로 줄인다.
            fitted = allowedFor(1).coerceIn(PracticeMinFallbackCell, minimum)
            textRatio = fitted / minimum
        }
    } else if (availableHeight.value.isFinite()) {
        // 원본 장단에 따라 한 각을 여러 세로줄로 나눈 악보: 줄 배치는 그대로 두고 가장 긴 줄이 남은 높이에 들어가도록 정간과 글자를 줄인다.
        val longest = scoreStrips(score, jangdan).maxOfOrNull { strip ->
            strip.rows.sumOf { row -> if ((strip.column to row) in expanded) 1.5 else 1.0 }.toFloat()
        } ?: 1f
        val allowed = (availableHeight - 14.dp - (metrics.headHeight + PracticeStackRowPadding) * scale) / (longest * scale)
        if (allowed < metrics.cellHeight) {
            fitted = allowed.coerceAtLeast(PracticeMinFallbackCell)
            textRatio = fitted / metrics.cellHeight
        }
    }
    val groups = score.columns.indices.toList().chunked(count)
    val packedMetrics = metrics.copy(
        cellHeight = fitted, yulSize = metrics.yulSize * textRatio, auxSize = metrics.auxSize * textRatio,
        threeSplitGlyphCell = if (keep && expanded.isNotEmpty()) fitted * 1.5f else null,
    )
    if (splitInTwo) {
        // 줄마다 가장 긴 줄의 높이에 맞춰 정간을 비례 확대해 모든 줄의 아래 끝을 맞춘다(한 각씩 둘 때와 같은 방식).
        val longest = splitWeights.flatten().max()
        val rowHeight = metrics.headHeight + fitted * longest + PracticeStackRowPadding
        return PracticeScorePacking(
            columnsPerStack = 1,
            metrics = packedMetrics,
            cellHeights = buildMap {
                score.columns.forEachIndexed { column, cells ->
                    val at = columnSplitAt(cells.size, jangdan)
                    cells.indices.forEach { row ->
                        val weight = splitWeights[column][if (row < at) 0 else 1]
                        put(column to row, fitted * (longest / weight) * if ((column to row) in expanded) 1.5f else 1f)
                    }
                }
            },
            columnGroups = groups,
            height = rowHeight * scale + 12.dp,
            stackRowHeights = listOf(rowHeight),
            splitColumns = true,
        )
    }
    val sharedWeights = if (groups.isEmpty()) emptyList() else rowWeights(count)
    val rowHeights = sharedWeights.map { weight ->
        metrics.headHeight + fitted * weight + PracticeStackRowPadding
    }
    // 같은 가로줄의 가장 긴 각에 맞춰 정간을 비례 확대하고 3소박 높이 비율을 유지한다.
    val cellHeights = if (packable) buildMap {
        score.columns.forEachIndexed { column, cells ->
            val stretched = fitted * (sharedWeights[column % count] / weights[column])
            cells.indices.forEach { row ->
                put(column to row, stretched * if ((column to row) in expanded) 1.5f else 1f)
            }
        }
    } else expanded.associateWith { fitted * 1.5f }
    return PracticeScorePacking(
        columnsPerStack = count,
        metrics = packedMetrics,
        cellHeights = cellHeights,
        columnGroups = groups,
        height = rowHeights.fold(0.dp) { total, height -> total + height } * scale +
            PracticeStackGap * (rowHeights.size - 1).coerceAtLeast(0) + 12.dp,
        stackRowHeights = rowHeights,
    )
}
