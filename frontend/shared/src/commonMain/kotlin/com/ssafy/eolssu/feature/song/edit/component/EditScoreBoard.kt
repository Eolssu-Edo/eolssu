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
import com.ssafy.eolssu.core.ui.score.readingScoreFormat
import com.ssafy.eolssu.core.ui.score.rememberReadingScoreFormat
import com.ssafy.eolssu.feature.song.edit.ImportCheck
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.StaffNote
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_editor_correction_description
import org.jetbrains.compose.resources.stringResource

/** EDIT-03 아래 '같은 위치의 정간보'처럼 작게 그리는 크기(율명 15 · 두 음 10). */
internal val CompactEditScoreMetrics = JeongganboMetrics(
    columnWidth = 58.dp, cellHeight = 34.dp, yulSize = 15.sp, auxSize = 8.sp, headHeight = 22.dp, splitScaleTwo = 10f / 15f,
)

/**
 * 편집 화면 정간보 모양: 다른 화면과 같은 공통 모양([JeongganboLook.Reading])에 가사를 넣을 곁줄을 늘 두고,
 * 시김새는 율명이 주가 되도록 연습 화면(0.7)보다 조금 작게(율명의 0.6) 그린다.
 */
internal val EditScoreLook: JeongganboLook = JeongganboLook.Reading.copy(lyricLane = 25.dp, sigimsaeScale = 0.6f)

/**
 * 편집·인식 확인 정간보. 기본 크기·칸 높이는 다른 화면과 같은 공통 형식([readingScoreFormat])이다.
 * 공통 렌더러([JeongganboView])로 그리고, [marks]로 고른 정간·빈 정간을 덧그린다.
 * [focusColumn]이 화면 밖이면 그 각이 보이도록 가로로 옮긴다. [scrollVertically]면 세로로 넘칠 때 악보 안에서 스크롤된다
 * (이미 세로로 스크롤되는 화면 안에 둘 때는 false). [focusCell]은 실제 정간 위치로 양방향 이동하며
 * [focusRequestKey]를 증가시키면 같은 정간으로 다시 이동한다.
 */
@Composable
internal fun EditScoreBoard(
    song: Song,
    modifier: Modifier = Modifier,
    marks: Map<Pair<Int, Int>, ScoreCellMark> = emptyMap(),
    annotations: Map<ScoreUnitKey, List<String>> = emptyMap(),
    notation: ScoreNotation = ScoreNotation(),
    /** 정간보 크기. null이면 공통 형식([readingScoreFormat])의 크기와 칸 높이(나뉜 소박 여유 · 각 끝 맞춤)를 쓴다. */
    metrics: JeongganboMetrics? = null,
    look: JeongganboLook = EditScoreLook,
    focusColumn: Int? = null,
    focusCell: Pair<Int, Int>? = null,
    focusRequestKey: Int = 0,
    scrollVertically: Boolean = true,
    onCellClick: ((column: Int, row: Int) -> Unit)? = null,
    unitMarks: Map<ScoreUnitKey, ScoreCellMark> = emptyMap(),
    /** 소박까지 알려 주는 누르기. [unitRow]·[unitCol]은 정간 안 표시 자리다. 있으면 [onCellClick] 대신 쓴다. */
    onUnitClick: ((column: Int, row: Int, unitRow: Int, unitCol: Int) -> Unit)? = null,
) {
    val format = rememberReadingScoreFormat(song)
    val sized = metrics ?: format.metrics
    val cellHeights = if (metrics == null) format.cellHeights else emptyMap()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val rendered = sized.forFontScale(density.fontScale)
    LaunchedEffect(focusColumn, focusCell, scrollState.viewportSize) {
        if (focusCell != null) return@LaunchedEffect
        val column = focusColumn ?: return@LaunchedEffect
        if (scrollState.viewportSize == 0) return@LaunchedEffect
        with(density) {
            // 각 0이 오른쪽 끝(스크롤 0)이고 다음 각이 왼쪽에 놓인다. 렌더러의 줄 바깥 여백은 2dp다.
            val right = 2.dp.toPx() + column * (rendered.columnWidth + sized.columnGap).toPx()
            val width = rendered.columnWidth.toPx()
            val viewport = scrollState.viewportSize
            val visible = right >= scrollState.value && right + width <= scrollState.value + viewport
            if (!visible) scrollState.animateScrollTo((right - (viewport - width) / 2).toInt().coerceIn(0, scrollState.maxValue))
        }
    }
    JeongganboView(
        song = song,
        modifier = if (scrollVertically) modifier.verticalScroll(rememberScrollState()) else modifier,
        metrics = sized,
        notation = notation,
        scrollState = scrollState,
        onUnitClick = onUnitClick?.let { click -> { unit -> click(unit.column, unit.row, unit.unitRow, unit.unitCol) } }
            ?: onCellClick?.let { click -> { unit -> click(unit.column, unit.row) } },
        annotations = annotations,
        look = look,
        cellHeights = cellHeights,
        cellMarks = marks,
        unitMarks = unitMarks,
        focusCell = focusCell,
        focusRequestKey = focusRequestKey,
    )
}

/**
 * 수정할 정간은 붉은 바탕·점선·!로 표시하고 선택해도 오류 바탕과 태그를 유지한다.
 * 정상 정간·소박의 선택은 기존 연두 표시를 쓴다.
 */
@Composable
internal fun editorMarks(draft: SheetDraft, selected: Int, sub: Int? = null, correctionCells: Set<Int> = emptySet()): Map<Pair<Int, Int>, ScoreCellMark> {
    val colors = EolssuTheme.colors
    val correctionDescription = stringResource(Res.string.sheet_editor_correction_description)
    val empty = ScoreCellMark(fill = colors.background, ring = colors.line, ringWidth = 1.dp, dashed = true)
    return buildMap {
        draft.emptyCells.forEach { put(draft.columnOf(it) to draft.rowOf(it), empty) }
        correctionCells.filter { it in draft.cells.indices }.forEach {
            put(draft.columnOf(it) to draft.rowOf(it), ScoreCellMark(
                fill = colors.recSoft, ring = colors.rec, ringWidth = 1.5.dp, dashed = true, tag = "!",
                description = correctionDescription, selected = it == selected && sub == null,
            ))
        }
        if (selected in draft.cells.indices) {
            val key = draft.columnOf(selected) to draft.rowOf(selected)
            val correction = this[key]?.takeIf { selected in correctionCells }
            val fill = if (sub == null) colors.primary else colors.accentTint
            put(key, correction?.copy(ring = colors.text, ringWidth = 2.5.dp, dashed = false)
                ?: ScoreCellMark(fill = fill, ring = colors.text, ringWidth = 2.5.dp, selected = sub == null))
        }
    }
}

/** 고른 소박만 연두 바탕과 먹색 테두리(1.5)로 칠한다. */
@Composable
internal fun editorUnitMarks(draft: SheetDraft, selected: Int, sub: Int?, correctionCells: Set<Int> = emptySet()): Map<ScoreUnitKey, ScoreCellMark> {
    if (sub == null || selected !in draft.cells.indices) return emptyMap()
    val key = draft.unitKeyOf(selected, sub) ?: return emptyMap()
    val colors = EolssuTheme.colors
    return mapOf(key to ScoreCellMark(fill = if (selected in correctionCells) colors.recSoft else colors.primary,
        ring = colors.text, ringWidth = 1.5.dp, selected = true))
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
