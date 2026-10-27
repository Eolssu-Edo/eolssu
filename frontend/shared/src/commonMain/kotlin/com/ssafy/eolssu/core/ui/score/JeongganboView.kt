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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.Yul
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
 * 대강이 시작하는 정간 위에는 굵은 선을 긋고, 긴 각은 [com.ssafy.eolssu.core.model.Jangdan.strips]대로 여러 줄로 나눈다.
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
    errors: Map<Pair<Int, Int>, EvaluationMetric> = emptyMap(),
    selectedIndex: Int? = null,
    onEventClick: ((ScoreEvent) -> Unit)? = null,
    maxColumns: Int? = null,
    maxRows: Int? = null,
) {
    val strips = remember(song, maxColumns, maxRows) { scoreStrips(song.score, song.jangdan, maxColumns, maxRows) }
    val eventsByCell = remember(song) { song.timeline.groupBy { it.column to it.row } }
    val daegangStarts = song.jangdan.daegangStarts
    val solfege = song.solfege.takeIf { notation.showSolfege }

    // 진행 비율은 그리기 단계에서만 읽어 매 프레임 다시 구성하지 않는다.
    val fraction = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback) {
        val p = playback ?: return@LaunchedEffect
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

    if (scrollState != null) {
        FollowCurrentStrip(scrollState, strips, song, currentIndex, follow, metrics, onUserScroll)
    }

    val rowModifier = if (scrollState != null) {
        Modifier.horizontalScroll(scrollState, reverseScrolling = true)
    } else {
        Modifier.wrapContentWidth(align = Alignment.End, unbounded = true)
    }
    Box(modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopEnd) {
        Row(
            modifier = rowModifier.padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
            verticalAlignment = Alignment.Top,
        ) {
            for (strip in strips.asReversed()) {
                ScoreStripColumn(strip, metrics) { row ->
                    ScoreCellBox(
                        events = eventsByCell[strip.column to row].orEmpty(),
                        metrics = metrics,
                        drawTopLine = !(strip.strip > 0 && row == strip.rows.first),
                        isDaegangStart = row in daegangStarts,
                        notation = notation,
                        solfege = solfege,
                        currentIndex = currentIndex,
                        highlight = highlight,
                        live = playback != null,
                        fraction = { fraction.floatValue },
                        error = errors[strip.column to row],
                        selectedIndex = selectedIndex,
                        onEventClick = onEventClick,
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
    song: Song,
    currentIndex: Int?,
    follow: Boolean,
    metrics: JeongganboMetrics,
    onUserScroll: () -> Unit,
) {
    val density = LocalDensity.current
    val latestOnUserScroll by rememberUpdatedState(onUserScroll)
    LaunchedEffect(scrollState) {
        scrollState.interactionSource.interactions.collect { if (it is DragInteraction.Start) latestOnUserScroll() }
    }
    val event = currentIndex?.let { song.timeline.getOrNull(it) }
    val stripIndex = event?.let { e -> strips.indexOfFirst { it.column == e.column && e.row in it.rows } } ?: -1
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
    cell: @Composable (row: Int) -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .padding(top = if (strip.strip > 0) metrics.headHeight else 0.dp)
            .width(metrics.columnWidth)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.line, shape),
    ) {
        if (strip.strip == 0) ColumnHead(strip.column, metrics)
        for (row in strip.rows) cell(row)
    }
}

@Composable
private fun ColumnHead(column: Int, metrics: JeongganboMetrics) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp)
    Row(
        Modifier.fillMaxWidth().height(metrics.headHeight).background(colors.surface),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column == 0) {
            Text(stringResource(Res.string.score_column_start), style = style, color = colors.accentText, fontWeight = FontWeight.Bold)
        }
        Text("${column + 1}", style = style, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScoreCellBox(
    events: List<ScoreEvent>,
    metrics: JeongganboMetrics,
    drawTopLine: Boolean,
    isDaegangStart: Boolean,
    notation: ScoreNotation,
    solfege: Map<Yul, String>?,
    currentIndex: Int?,
    highlight: ScoreHighlight,
    live: Boolean,
    fraction: () -> Float,
    error: EvaluationMetric?,
    selectedIndex: Int?,
    onEventClick: ((ScoreEvent) -> Unit)?,
) {
    val colors = EolssuTheme.colors
    val errorStyle = error?.let { errorStyle(it) }
    val progress = cellProgress(events, currentIndex)
    val (fill, ring) = when (highlight) {
        ScoreHighlight.Position -> colors.accentSoft to colors.accent
        ScoreHighlight.Recording -> colors.recSoft to colors.rec
    }
    val headColor = if (highlight == ScoreHighlight.Recording) colors.rec else colors.play
    val lineColor = if (isDaegangStart) colors.lineStrong else colors.line
    val lineWidth = if (isDaegangStart) 2.dp else 1.dp
    val isSelected = selectedIndex != null && events.any { it.index == selectedIndex }
    val current = events.firstOrNull { it.index == currentIndex }

    var cellModifier = Modifier.fillMaxWidth().height(metrics.cellHeight)
    cellModifier = when {
        live && progress == CellProgress.Past -> cellModifier.background(colors.doneSoft)
        live -> cellModifier
        progress == CellProgress.Past -> cellModifier.alpha(0.5f)
        progress == CellProgress.Current -> cellModifier.background(fill)
        else -> cellModifier
    }
    if (live && current != null) {
        // 현재 정간: 정간 안에서 지난 만큼(나눈 음이면 앞 음 몫 포함) 위에서부터 채우고 그 자리에 진행 선을 긋는다.
        cellModifier = cellModifier.drawWithContent {
            val cellFraction = ((current.sub + fraction()) / current.subCount).coerceIn(0f, 1f)
            val y = size.height * cellFraction
            drawRect(colors.doneSoft, size = Size(size.width, y))
            drawContent()
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
        events.forEachIndexed { k, event ->
            ScoreNoteBox(
                event = event,
                metrics = metrics,
                showDivider = k > 0,
                isCurrentInSplit = !live && events.size > 1 && event.index == currentIndex,
                notation = notation,
                solfege = solfege,
                onClick = onEventClick?.let { { it(event) } },
                isSelected = event.index == selectedIndex,
                modifier = Modifier.weight(1f),
            )
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
    event: ScoreEvent,
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
    val note = event.note
    val pitch = note as? ScoreNote.Pitch
    val label = when (note) {
        is ScoreNote.Pitch -> note.yul.fullName
        ScoreNote.Hold -> stringResource(Res.string.score_hold)
        ScoreNote.Rest -> stringResource(Res.string.score_rest)
    }
    val hangul = when {
        !notation.showHangul -> null
        pitch != null -> pitch.yul.hangul
        note == ScoreNote.Rest -> stringResource(Res.string.score_rest_hangul)
        else -> null
    }
    val solfegeText = pitch?.let { solfege?.get(it.yul) }

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

    val yulSize = metrics.yulSizeFor(event.subCount)
    val auxSize = metrics.auxSizeFor(event.subCount)
    Row(
        noteModifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = note.symbol,
            modifier = Modifier.clearAndSetSemantics {},
            style = EolssuTheme.serif.yul.copy(fontSize = yulSize, lineHeight = yulSize),
            color = if (pitch != null) colors.text else colors.textMuted,
            fontWeight = if (pitch != null) FontWeight.SemiBold else FontWeight.Normal,
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
