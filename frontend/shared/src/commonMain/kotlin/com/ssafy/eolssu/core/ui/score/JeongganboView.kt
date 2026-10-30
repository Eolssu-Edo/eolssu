package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.score_column_start
import eolssu.shared.generated.resources.score_hold
import eolssu.shared.generated.resources.score_rest
import eolssu.shared.generated.resources.score_rest_hangul
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeMark

/**
 * 율명 옆에 작게 붙이는 보조 표기. 율명은 항상 가장 크게 그린다.
 *
 * @property showSolfege 곡에 계이름 데이터([Song.solfege])가 있을 때만 표시된다.
 */
@Immutable
data class ScoreNotation(val showHangul: Boolean = false, val showSolfege: Boolean = false)

/** 현재 위치 강조 색. 평소·모범 연주는 [Position](노랑·파랑), 녹음 중은 [Recording](빨강)이다. */
enum class ScoreHighlight { Position, Recording }

/**
 * 재생·연주 중 진행. 현재 음([currentIndex])이 [startedAt]부터 [durationMs] 동안 진행되고,
 * 멈춰 있으면([startedAt]이 null) [pausedFraction]만큼 지난 모습으로 그린다.
 * 지나간 정간은 회색 바탕, 현재 정간은 지난 만큼 위에서부터 채우고 진행 선을 긋는다.
 */
@Immutable
data class ScorePlayback(val startedAt: TimeMark?, val durationMs: Double, val pausedFraction: Float = 0f)

/**
 * 정간보. 각 0을 가장 오른쪽에 두고 다음 각을 왼쪽에 놓아, 위 → 아래, 오른쪽 → 왼쪽으로 읽는다.
 * 원본의 가변 각 길이를 유지하고, 긴 각은 여러 줄로 나눈다. 기존 샘플에만 장단의 대강 선을 적용한다.
 *
 * @param scrollState 가로 스크롤 상태. 처음에는 오른쪽 끝(시작 각)이 보인다. null이면 스크롤 없이 넘치는 왼쪽을 자른다(미리보기).
 * @param currentIndex 현재 위치의 연주 순서([ScoreEvent.index]). 그 정간을 강조하고 앞의 정간을 흐리게 한다.
 * @param playback 재생·연주 중이면 진행 모습([ScorePlayback])으로 그린다.
 * @param follow 현재 위치가 화면 밖으로 나가면 가로로 따라간다. 사용자가 악보를 끌면 [onUserScroll]을 부른다.
 * @param errors 틀린 정간(각, 정간) → 틀린 항목. 정간을 색칠하고 오른쪽 위에 종류 글자(음·박·길)를 붙인다.
 * @param selectedIndex 사용자가 고른 음의 연주 순서. 정간에 검은 테두리를 그린다.
 * @param onEventClick 있으면 음을 누를 수 있다.
 * @param maxColumns 앞에서부터 이 수의 각만 그린다.
 * @param maxRows 각마다 첫 정간부터 이 수만 한 줄로 그린다.
 * @param columns 이 범위의 각만 그린다. 악보를 여러 단으로 나눠 그릴 때 쓴다.
 * @param dimUnmarked [errors]에 없는 정간을 흐리게 해 틀린 정간을 돋보이게 한다.
 * @param highlightColumns 이 범위의 각을 노란 테두리로 강조한다(틀린 칸이 몰린 각).
 * @param selectedCell 결과에서 선택한 표시 정간. 같은 이벤트를 잇는 다른 정간까지 선택하지 않는다.
 * @param onUnitClick 표시 자리를 선택할 때 쓴다. 연습의 [onEventClick]은 원본 이벤트를 그대로 전달한다.
 */
@Composable
fun JeongganboView(
    song: Song,
    modifier: Modifier = Modifier,
    metrics: JeongganboMetrics = JeongganboMetrics.Phone,
    notation: ScoreNotation = ScoreNotation(),
    scrollState: ScrollState? = rememberScrollState(),
    currentIndex: Int? = null,
    highlight: ScoreHighlight = ScoreHighlight.Position,
    playback: ScorePlayback? = null,
    follow: Boolean = false,
    onUserScroll: () -> Unit = {},
    errors: Map<Pair<Int, Int>, ScoreErrorKind> = emptyMap(),
    selectedIndex: Int? = null,
    onEventClick: ((ScoreEvent) -> Unit)? = null,
    maxColumns: Int? = null,
    maxRows: Int? = null,
    columns: IntRange? = null,
    dimUnmarked: Boolean = false,
    highlightColumns: IntRange? = null,
    selectedCell: Pair<Int, Int>? = null,
    onUnitClick: ((ScoreDisplayUnit) -> Unit)? = null,
) {
    // sp만 커져 분할 음·각 머리글이 잘리지 않도록 해당 악보의 기하도 함께 늘린다.
    // 화면 전체를 확대하지 않으며 기존 가로/세로 스크롤과 원본 줄바꿈을 유지한다.
    val renderMetrics = metrics.forFontScale(LocalDensity.current.fontScale)
    val strips = remember(song, maxColumns, maxRows, columns) { scoreStrips(song.score, song.jangdan, maxColumns, maxRows, columns) }
    val unitsByCell = remember(song) { song.displayUnits.groupBy { it.column to it.row } }
    // 원본에는 대강 경계가 없다. 예전 장단의 고정 분할을 새 악보에 덧씌우지 않는다.
    val daegangStarts = if (song.score.timing == null) song.jangdan.daegangStarts else emptyList()
    val solfege = song.solfege.takeIf { notation.showSolfege }

    // 채우기는 그리기 단계에서, 따라가기는 표시 자리 경계를 넘을 때만 갱신한다.
    val fraction = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback) {
        val p = playback ?: run { fraction.floatValue = 0f; return@LaunchedEffect }
        val started = p.startedAt
        if (started == null) {
            fraction.floatValue = p.pausedFraction
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos {
                fraction.floatValue = (started.elapsedNow().inWholeMicroseconds / 1000.0 / p.durationMs).toFloat().coerceIn(0f, 1f)
            }
        }
    }

    val currentUnit by remember(song, currentIndex) {
        derivedStateOf { currentIndex?.let { song.displayUnitAt(it, fraction.floatValue) } }
    }

    if (scrollState != null) {
        FollowCurrentStrip(scrollState, strips, currentUnit, follow, renderMetrics, onUserScroll)
    }

    val rowModifier = if (scrollState != null) {
        Modifier.horizontalScroll(scrollState, reverseScrolling = true)
    } else {
        Modifier.wrapContentWidth(align = Alignment.End, unbounded = true)
    }
    Box(modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopEnd) {
        Row(
            modifier = rowModifier.semantics { isTraversalGroup = true }
                .padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
            verticalAlignment = Alignment.Top,
        ) {
            for ((readingIndex, strip) in strips.withIndex().reversed()) {
                ScoreStripColumn(strip, renderMetrics, readingIndex, highlighted = highlightColumns != null && strip.column in highlightColumns) { row ->
                    ScoreCellBox(
                        units = unitsByCell[strip.column to row].orEmpty(),
                        metrics = renderMetrics,
                        drawTopLine = !(strip.strip > 0 && row == strip.rows.first),
                        isDaegangStart = row in daegangStarts,
                        notation = notation,
                        solfege = solfege,
                        currentIndex = currentIndex,
                        currentEvent = currentIndex?.let { song.timeline.getOrNull(it) },
                        highlight = highlight,
                        live = playback != null,
                        fraction = { fraction.floatValue },
                        error = errors[strip.column to row],
                        dimmed = dimUnmarked && (strip.column to row) !in errors,
                        selectedIndex = selectedIndex,
                        selectedCell = selectedCell?.let { it == (strip.column to row) },
                        onEventClick = onEventClick,
                        onUnitClick = onUnitClick,
                    )
                }
            }
        }
    }
}

/** 현재 음이 든 줄이 화면 밖이면 가운데로 옮긴다. 사용자가 끌면 따라가기를 멈추도록 알린다. */
@Composable
private fun FollowCurrentStrip(
    scrollState: ScrollState,
    strips: List<ScoreStrip>,
    currentUnit: ScoreDisplayUnit?,
    follow: Boolean,
    metrics: JeongganboMetrics,
    onUserScroll: () -> Unit,
) {
    val density = LocalDensity.current
    val latestOnUserScroll by rememberUpdatedState(onUserScroll)
    LaunchedEffect(scrollState) {
        scrollState.interactionSource.interactions.collect { if (it is DragInteraction.Start) latestOnUserScroll() }
    }
    val stripIndex = currentUnit?.let { e -> strips.indexOfFirst { it.column == e.column && e.row in it.rows } } ?: -1
    LaunchedEffect(stripIndex, follow, scrollState.viewportSize) {
        if (!follow || stripIndex < 0 || scrollState.viewportSize == 0) return@LaunchedEffect
        with(density) {
            val step = (metrics.columnWidth + metrics.columnGap).toPx()
            val right = 2.dp.toPx() + stripIndex * step
            val width = metrics.columnWidth.toPx()
            val viewport = scrollState.viewportSize
            val visible = right >= scrollState.value && right + width <= scrollState.value + viewport
            if (!visible) {
                val target = (right - (viewport - width) / 2).toInt().coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(target)
            }
        }
    }
}

/** 세로 줄 하나. 각의 첫 줄에는 각 번호 머리 칸을 두고, 이어지는 줄은 머리 칸 높이만큼 내려 정간 높이를 맞춘다. */
@Composable
private fun ScoreStripColumn(
    strip: ScoreStrip,
    metrics: JeongganboMetrics,
    readingIndex: Int,
    highlighted: Boolean = false,
    cell: @Composable (row: Int) -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(6.dp)
    // 강조한 각은 바깥에 연한 노란 띠를 두르되, 각 너비와 정간 위치는 그대로 둔다.
    val ring = if (highlighted) {
        Modifier.drawBehind {
            val spread = 3.dp.toPx()
            drawRoundRect(
                colors.accentSoft,
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = CornerRadius(9.dp.toPx()),
            )
        }
    } else {
        Modifier
    }
    Column(
        Modifier
            .semantics {
                isTraversalGroup = true
                traversalIndex = readingIndex.toFloat()
            }
            .padding(top = if (strip.strip > 0) metrics.headHeight else 0.dp)
            .width(metrics.columnWidth)
            .then(ring)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, if (highlighted) colors.accent else colors.line, shape),
    ) {
        if (strip.strip == 0) ColumnHead(strip.column, metrics, highlighted)
        for (row in strip.rows) cell(row)
    }
}

@Composable
private fun ColumnHead(column: Int, metrics: JeongganboMetrics, highlighted: Boolean = false) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp)
    Row(
        Modifier.fillMaxWidth().height(metrics.headHeight).background(if (highlighted) colors.accentSoft else colors.surface),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column == 0) {
            Text(stringResource(Res.string.score_column_start), style = style, color = colors.accentText, fontWeight = FontWeight.Bold)
        }
        Text("${column + 1}", style = style, color = if (highlighted) colors.text else colors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScoreCellBox(
    units: List<ScoreDisplayUnit>,
    metrics: JeongganboMetrics,
    drawTopLine: Boolean,
    isDaegangStart: Boolean,
    notation: ScoreNotation,
    solfege: Map<Yul, String>?,
    currentIndex: Int?,
    currentEvent: ScoreEvent?,
    highlight: ScoreHighlight,
    live: Boolean,
    fraction: () -> Float,
    error: ScoreErrorKind?,
    selectedIndex: Int?,
    selectedCell: Boolean?,
    onEventClick: ((ScoreEvent) -> Unit)?,
    onUnitClick: ((ScoreDisplayUnit) -> Unit)?,
    dimmed: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val errorStyle = error?.let { errorStyle(it) }
    val cellStart = units.minOfOrNull { it.unitStart } ?: 0.0
    val currentFraction by rememberUpdatedState(fraction)
    val progress by remember(units, currentEvent, live) {
        derivedStateOf {
            val position = currentEvent?.let { it.unitStart + if (live) it.unitDuration * currentFraction() else 0.0 }
            cellProgressAt(cellStart, position)
        }
    }
    val (fill, ring) = when (highlight) {
        ScoreHighlight.Position -> colors.accentSoft to colors.accent
        ScoreHighlight.Recording -> colors.recSoft to colors.rec
    }
    val headColor = if (highlight == ScoreHighlight.Recording) colors.rec else colors.play
    val lineColor = if (isDaegangStart) colors.lineStrong else colors.line
    val lineWidth = if (isDaegangStart) 2.dp else 1.dp
    val isSelected = selectedCell ?: (selectedIndex != null && units.any { it.event.index == selectedIndex })
    val rows = remember(units) { units.groupBy { it.unitRow }.values.toList() }

    var cellModifier = Modifier.fillMaxWidth().height(metrics.cellHeight)
    if (dimmed && !isSelected) cellModifier = cellModifier.alpha(0.55f)
    cellModifier = when {
        live && progress == CellProgress.Past -> cellModifier.background(colors.doneSoft)
        live -> cellModifier
        progress == CellProgress.Past -> cellModifier.alpha(0.5f)
        progress == CellProgress.Current -> cellModifier.background(fill)
        else -> cellModifier
    }
    if (live && progress == CellProgress.Current && currentEvent != null) {
        // 현재 정간: 정간 안에서 지난 만큼(나눈 음이면 앞 음 몫 포함) 위에서부터 채우고 그 자리에 진행 선을 긋는다.
        cellModifier = cellModifier.drawWithContent {
            val cellFraction = (currentEvent.unitStart + currentEvent.unitDuration * fraction() - cellStart).toFloat().coerceIn(0f, 1f)
            val rowProgress = cellFraction * rows.size
            val activeRow = rowProgress.toInt().coerceAtMost(rows.lastIndex)
            val rowFraction = (rowProgress - activeRow).coerceIn(0f, 1f)
            val rowHeight = size.height / rows.size
            val y = rowHeight * (activeRow + rowFraction)
            val splitHorizontally = rows[activeRow].size > 1
            val top = rowHeight * activeRow
            drawRect(colors.doneSoft, size = Size(size.width, if (splitHorizontally) top else y))
            if (splitHorizontally) drawRect(colors.doneSoft, topLeft = Offset(0f, top), size = Size(size.width * rowFraction, rowHeight))
            drawContent()
            if (splitHorizontally) {
                val x = size.width * rowFraction
                drawLine(headColor, Offset(x, top), Offset(x, top + rowHeight), strokeWidth = 2.5.dp.toPx())
                return@drawWithContent
            }
            val band = 14.dp.toPx().coerceAtMost(y)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, headColor.copy(alpha = 0.16f)), startY = y - band, endY = y),
                topLeft = Offset(0f, y - band),
                size = Size(size.width, band),
            )
            drawLine(headColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5.dp.toPx())
            val tri = Path().apply {
                moveTo(size.width, y - 4.dp.toPx())
                lineTo(size.width - 5.dp.toPx(), y)
                lineTo(size.width, y + 4.dp.toPx())
                close()
            }
            drawPath(tri, headColor)
        }
    }
    if (errorStyle != null) cellModifier = cellModifier.background(errorStyle.fill)
    if (drawTopLine) {
        cellModifier = cellModifier.drawBehind {
            drawRect(lineColor, size = size.copy(height = lineWidth.toPx()))
        }
    }
    if (errorStyle != null) {
        cellModifier = cellModifier.drawWithContent {
            drawContent()
            val inset = if (errorStyle.dashed) 3.dp.toPx() else 0.75.dp.toPx()
            drawRect(
                errorStyle.ring,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    1.5.dp.toPx(),
                    pathEffect = if (errorStyle.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                ),
            )
        }
    }
    when {
        !live && progress == CellProgress.Current -> cellModifier = cellModifier.border(2.dp, ring)
        isSelected -> cellModifier = cellModifier.border(2.dp, colors.text)
    }

    Box(cellModifier) {
    Column(Modifier.fillMaxSize()) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { unit ->
                    ScoreNoteBox(
                        unit = unit,
                        splitCount = maxOf(rows.size, row.size),
                        metrics = metrics,
                        showDivider = r > 0,
                        isCurrentInSplit = !live && units.size > 1 && unit.event.index == currentIndex,
                        notation = notation,
                        solfege = solfege,
                        onClick = onUnitClick?.let { { it(unit) } } ?: onEventClick?.let { { it(unit.event) } },
                        isSelected = selectedCell ?: (unit.event.index == selectedIndex),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
        if (error != null && errorStyle != null) {
            Text(
                errorTag(error),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 2.dp).clearAndSetSemantics {},
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold),
                color = errorStyle.tag,
            )
        }
    }
}

@Composable
private fun ScoreNoteBox(
    unit: ScoreDisplayUnit,
    splitCount: Int,
    metrics: JeongganboMetrics,
    showDivider: Boolean,
    isCurrentInSplit: Boolean,
    notation: ScoreNotation,
    solfege: Map<Yul, String>?,
    onClick: (() -> Unit)?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val note = unit.note
    val isPitch = note is ScoreNote.Pitch || note is ScoreNote.SourcePitch
    val label = when (note) {
        is ScoreNote.Pitch -> note.yul.fullName
        is ScoreNote.SourcePitch -> note.fullName
        ScoreNote.Hold -> stringResource(Res.string.score_hold)
        ScoreNote.Blank -> stringResource(if (unit.event.note == ScoreNote.Rest) Res.string.score_rest else Res.string.score_hold)
        ScoreNote.Rest -> stringResource(Res.string.score_rest)
    }
    val hangul = when {
        !notation.showHangul -> null
        note is ScoreNote.Pitch -> note.yul.hangul
        note is ScoreNote.SourcePitch -> note.hangul
        note == ScoreNote.Rest -> stringResource(Res.string.score_rest_hangul)
        else -> null
    }
    val solfegeText = note.fingeringYul?.let { solfege?.get(it) }

    var noteModifier = modifier.fillMaxWidth()
    if (isCurrentInSplit) noteModifier = noteModifier.background(Color.White.copy(alpha = 0.55f))
    if (showDivider) {
        noteModifier = noteModifier.drawBehind {
            drawLine(
                color = colors.line,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
            )
        }
    }
    // 화면 읽기 프로그램은 한자 대신 율명 이름(황종)을 읽는다. 글자 자체는 아래에서 읽지 않게 숨긴다.
    if (onClick != null) noteModifier = noteModifier.clickable(role = Role.Button, onClick = onClick)
    noteModifier = noteModifier.semantics(mergeDescendants = true) {
        contentDescription = label
        if (onClick != null) selected = isSelected
    }

    val yulSize = metrics.yulSizeFor(splitCount)
    val auxSize = metrics.auxSizeFor(splitCount)
    Row(
        noteModifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreNoteGlyph(
            note = note,
            modifier = Modifier.clearAndSetSemantics {},
            size = yulSize,
            color = if (isPitch) colors.text else colors.textMuted,
            fontWeight = if (isPitch) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (hangul != null || solfegeText != null) {
            val auxStyle = MaterialTheme.typography.labelSmall.copy(fontSize = auxSize, lineHeight = auxSize * 1.1f)
            Column(Modifier.clearAndSetSemantics {}) {
                if (hangul != null) Text(hangul, style = auxStyle, color = colors.textMuted)
                if (solfegeText != null) Text(solfegeText, style = auxStyle, color = colors.playText)
            }
        }
    }
}
