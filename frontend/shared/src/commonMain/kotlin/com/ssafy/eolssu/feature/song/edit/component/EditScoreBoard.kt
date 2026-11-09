package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreCellMark
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.feature.song.edit.ImportCheck
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.StaffNote

/**
 * 연습 화면과 같은 정간보 크기(`.practice-score .ms`: 각 78 · 정간 44 · 율명 21). 한 각이 11정간 이상이면 정간을 줄인다.
 * 위아래로 나눈 정간이 있으면 소박 한 줄마다 [SplitRowHeight] 이상이 되도록 모든 정간을 길게 한다(줄어든 한자가 잘리지 않게).
 */
internal fun editScoreMetrics(gak: Int, splitRows: Int = 1): JeongganboMetrics =
    JeongganboMetrics.practiceBoard(big = false, longestStrip = gak).let { base ->
        if (splitRows <= 1) base else base.copy(cellHeight = maxOf(base.cellHeight, SplitRowHeight * splitRows))
    }

/** 나눈 정간 소박 한 줄의 최소 높이. 두 음 14sp · 세 음 13sp 율명과 청·탁 변이 위아래로 잘리지 않는 높이다. */
private val SplitRowHeight = 28.dp

/** 악보에서 한 정간을 위아래로 가장 많이 나눈 줄 수. */
internal fun Song.maxSplitRows(): Int = score.columns.flatten().maxOfOrNull { cell ->
    cell.units.map { it.row }.distinct().size.takeIf { it > 0 } ?: cell.notes.size
} ?: 1

/** EDIT-03 아래 '같은 위치의 정간보'처럼 작게 그리는 크기(율명 15 · 두 음 10). */
internal val CompactEditScoreMetrics = JeongganboMetrics(
    columnWidth = 58.dp, cellHeight = 34.dp, yulSize = 15.sp, auxSize = 8.sp, headHeight = 22.dp, splitScaleTwo = 10f / 15f,
)

/** 편집 화면 정간보 모양: 연습 화면과 같고, 시김새는 율명이 주가 되도록 연습 화면(0.7)보다 조금 작게(율명의 0.6) 그린다. */
internal val EditScoreLook: JeongganboLook = JeongganboLook.practice(big = false).copy(sigimsaeScale = 0.6f)

/**
 * 편집·인식 확인·미리보기 정간보. 공통 렌더러([JeongganboView])를 연습 화면 모양으로 그리고, [marks]로 고른 정간·빈 정간을 덧그린다.
 * [focusColumn]이 화면 밖이면 그 각이 보이도록 가로로 옮긴다. [scrollVertically]면 세로로 넘칠 때 악보 안에서 스크롤된다
 * (이미 세로로 스크롤되는 화면 안에 둘 때는 false).
 */
@Composable
internal fun EditScoreBoard(
    song: Song,
    modifier: Modifier = Modifier,
    marks: Map<Pair<Int, Int>, ScoreCellMark> = emptyMap(),
    annotations: Map<ScoreUnitKey, List<String>> = emptyMap(),
    notation: ScoreNotation = ScoreNotation(),
    metrics: JeongganboMetrics = editScoreMetrics(song.score.columns.maxOfOrNull { it.size } ?: 9, song.maxSplitRows()),
    look: JeongganboLook = EditScoreLook,
    focusColumn: Int? = null,
    scrollVertically: Boolean = true,
    onCellClick: ((column: Int, row: Int) -> Unit)? = null,
    unitMarks: Map<ScoreUnitKey, ScoreCellMark> = emptyMap(),
    /** 소박까지 알려 주는 누르기. [unitRow]·[unitCol]은 정간 안 표시 자리다. 있으면 [onCellClick] 대신 쓴다. */
    onUnitClick: ((column: Int, row: Int, unitRow: Int, unitCol: Int) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val rendered = metrics.forFontScale(density.fontScale)
    LaunchedEffect(focusColumn, scrollState.viewportSize) {
        val column = focusColumn ?: return@LaunchedEffect
        if (scrollState.viewportSize == 0) return@LaunchedEffect
        with(density) {
            // 각 0이 오른쪽 끝(스크롤 0)이고 다음 각이 왼쪽에 놓인다. 렌더러의 줄 바깥 여백은 2dp다.
            val right = 2.dp.toPx() + column * (rendered.columnWidth + metrics.columnGap).toPx()
            val width = rendered.columnWidth.toPx()
            val viewport = scrollState.viewportSize
            val visible = right >= scrollState.value && right + width <= scrollState.value + viewport
            if (!visible) scrollState.animateScrollTo((right - (viewport - width) / 2).toInt().coerceIn(0, scrollState.maxValue))
        }
    }
    JeongganboView(
        song = song,
        modifier = if (scrollVertically) modifier.verticalScroll(rememberScrollState()) else modifier,
        metrics = metrics,
        notation = notation,
        scrollState = scrollState,
        onUnitClick = onUnitClick?.let { click -> { unit -> click(unit.column, unit.row, unit.unitRow, unit.unitCol) } }
            ?: onCellClick?.let { click -> { unit -> click(unit.column, unit.row) } },
        annotations = annotations,
        look = look,
        cellMarks = marks,
        unitMarks = unitMarks,
    )
}

/**
 * EDIT-02 표시: 빈 정간은 옅은 바탕과 점선, 고른 정간은 연두 바탕에 먹색 안쪽 테두리(2.5).
 * 나눈 정간의 소박 하나를 골랐으면([sub]) 정간은 옅은 연두 바탕에 테두리만 두고, 그 소박을 [editorUnitMarks]로 칠한다.
 */
@Composable
internal fun editorMarks(draft: SheetDraft, selected: Int, sub: Int? = null): Map<Pair<Int, Int>, ScoreCellMark> {
    val colors = EolssuTheme.colors
    val empty = ScoreCellMark(fill = colors.background, ring = colors.line, ringWidth = 1.dp, dashed = true)
    return buildMap {
        draft.emptyCells.forEach { put(draft.columnOf(it) to draft.rowOf(it), empty) }
        if (selected in draft.cells.indices) {
            val fill = if (sub == null) colors.primary else colors.accentTint
            put(draft.columnOf(selected) to draft.rowOf(selected), ScoreCellMark(fill = fill, ring = colors.text, ringWidth = 2.5.dp))
        }
    }
}

/** 고른 소박만 연두 바탕과 먹색 테두리(1.5)로 칠한다. */
@Composable
internal fun editorUnitMarks(draft: SheetDraft, selected: Int, sub: Int?): Map<ScoreUnitKey, ScoreCellMark> {
    if (sub == null || selected !in draft.cells.indices) return emptyMap()
    val key = draft.unitKeyOf(selected, sub) ?: return emptyMap()
    val colors = EolssuTheme.colors
    return mapOf(key to ScoreCellMark(fill = colors.primary, ring = colors.text, ringWidth = 1.5.dp))
}

/** EDIT-04 표시: 저장하려면 채워야 하는 빈 정간을 옅은 붉은 바탕과 붉은 점선으로 알린다. */
@Composable
internal fun previewMarks(draft: SheetDraft): Map<Pair<Int, Int>, ScoreCellMark> {
    val colors = EolssuTheme.colors
    val mark = ScoreCellMark(fill = colors.recSoft, ring = colors.rec, ringWidth = 1.5.dp, dashed = true)
    return draft.emptyCells.associate { (draft.columnOf(it) to draft.rowOf(it)) to mark }
}

/** EDIT-06 표시: 확인할 정간은 먹색 점선과 '?', 고친 정간은 연두 바탕과 '✓', 보고 있는 정간은 연두 안쪽 테두리. */
@Composable
internal fun importMarks(draft: SheetDraft, checks: List<ImportCheck>, fixed: Map<Int, String>, selected: Int?): Map<Pair<Int, Int>, ScoreCellMark> {
    val colors = EolssuTheme.colors
    return checks.associate { check ->
        val done = check.index in fixed
        val mark = when {
            check.index == selected -> ScoreCellMark(fill = colors.accentTint, ring = colors.primary, tag = if (done) "✓" else "?")
            done -> ScoreCellMark(fill = colors.accentTint, tag = "✓")
            else -> ScoreCellMark(ring = colors.text, ringWidth = 1.5.dp, dashed = true, tag = "?")
        }
        (draft.columnOf(check.index) to draft.rowOf(check.index)) to mark
    }
}

/** EDIT-03 아래 정간보: 고른 음표가 걸친 정간을 옅은 연두 바탕과 연두 안쪽 테두리로 표시한다. */
@Composable
internal fun staffMarks(draft: SheetDraft, note: StaffNote?): Map<Pair<Int, Int>, ScoreCellMark> {
    if (note == null) return emptyMap()
    val colors = EolssuTheme.colors
    val mark = ScoreCellMark(fill = colors.accentTint, ring = colors.primary)
    return (note.key.start until (note.key.start + note.span).coerceAtMost(draft.cells.size))
        .associate { (draft.columnOf(it) to draft.rowOf(it)) to mark }
}
