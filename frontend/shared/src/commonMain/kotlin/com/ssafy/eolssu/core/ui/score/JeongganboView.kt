package com.ssafy.eolssu.core.ui.score

import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_upcoming
import eolssu.shared.generated.resources.score_blank
import eolssu.shared.generated.resources.score_column_head
import eolssu.shared.generated.resources.score_column_head_start
import eolssu.shared.generated.resources.score_column_start
import eolssu.shared.generated.resources.score_hold
import eolssu.shared.generated.resources.score_rest
import eolssu.shared.generated.resources.score_rest_hangul
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 율명 옆에 작게 붙이는 보조 표기. 율명은 항상 가장 크게 그린다.
 *
 * @property showSolfege 곡에 계이름 데이터([Song.solfege])가 있을 때만 표시된다.
 */
@Immutable
data class ScoreNotation(val showHangul: Boolean = false, val showSolfege: Boolean = false)

/** 현재 위치 강조 색. 평소·모범 연주는 [Position](노랑·파랑), 녹음 중은 [Recording](빨강)이다. */
enum class ScoreHighlight { Position, Recording }

/** 진행 위치: 연주 순서 [index]인 음에서 [fraction]만큼 지난 곳. */
@Immutable
data class ScoreCursor(val index: Int, val fraction: Float)

/**
 * 재생·연주 중 진행. [at]은 지금 위치를 돌려주며, [running]이면 매 프레임 다시 물어 이어 그리고 멈춰 있으면 그 자리에 둔다.
 * 지나간 정간은 회색 바탕, 현재 정간은 테두리로 표시한다.
 */
class ScorePlayback(val running: Boolean, val at: () -> ScoreCursor)

/** [rememberScoreCursor]가 따라가는 현재 위치. 음 순서와 지난 비율을 한 번에 바꿔, 음이 바뀌는 순간에도 둘이 어긋나지 않는다. */
@Stable
class ScoreCursorState internal constructor(initial: ScoreCursor?) {
    /** 현재 음의 연주 순서. 화면 구성에서 읽는다. */
    var index: Int? by mutableStateOf(initial?.index)
        private set

    /** 현재 음 안에서 지난 비율. 매 프레임 바뀌므로 그리기 단계나 [derivedStateOf]에서만 읽는다. */
    var fraction: Float by mutableFloatStateOf(initial?.fraction ?: 0f)
        private set

    internal fun set(cursor: ScoreCursor?) {
        index = cursor?.index
        fraction = cursor?.fraction ?: 0f
    }
}

/** [playback]을 따라가는 현재 위치. 재생·연주 중에는 매 프레임 [ScorePlayback.at]으로 갱신한다. */
@Composable
fun rememberScoreCursor(playback: ScorePlayback?): ScoreCursorState {
    val cursor = remember { ScoreCursorState(playback?.at()) }
    val latest = rememberUpdatedState(playback)
    LaunchedEffect(cursor) {
        snapshotFlow { latest.value }.collectLatest { p ->
            cursor.set(p?.at())
            if (p?.running == true) while (true) withFrameNanos { cursor.set(p.at()) }
        }
    }
    return cursor
}

/**
 * 악보 편집·인식 확인 화면이 정간 하나에 덧그리는 표시. 바탕([fill])은 율명 칸에만 칠하고 곁줄(가사 칸)에는 칠하지 않는다.
 *
 * @property ring 안쪽 테두리 색. null이면 긋지 않는다.
 * @property dashed 테두리를 점선으로 정간 안쪽 3dp에 긋는다(빈 정간·확인할 정간).
 * @property tag 오른쪽 위에 붙이는 작은 글자(예: 확인할 정간 '?', 고친 정간 '✓').
 */
@Immutable
data class ScoreCellMark(
    val fill: Color? = null,
    val ring: Color? = null,
    val ringWidth: Dp = 2.dp,
    val dashed: Boolean = false,
    val tag: String? = null,
)

/**
 * 정간보. 각 0을 가장 오른쪽에 두고 다음 각을 왼쪽에 놓아, 위 → 아래, 오른쪽 → 왼쪽으로 읽는다.
 * 원본의 가변 각 길이를 유지하고, 긴 각은 여러 줄로 나눈다. 기존 샘플에만 장단의 대강 선을 적용한다.
 *
 * @param scrollState 가로 스크롤 상태. 처음에는 오른쪽 끝(시작 각)이 보인다. null이면 스크롤 없이 넘치는 왼쪽을 자른다(미리보기).
 * @param currentIndex 현재 위치의 연주 순서([ScoreEvent.index]). 그 정간을 강조하고 앞의 정간을 흐리게 한다.
 * @param playback 재생·연주 중이면 진행 모습([ScorePlayback])으로 그린다. 이때 현재 음은 [currentIndex] 대신 진행 위치에서 읽는다.
 * @param follow 현재 위치가 화면 밖으로 나가면 가로로 따라간다. 사용자가 악보를 끌면 [onUserScroll]을 부른다.
 * @param errors 틀린 정간(각, 정간) → 틀린 항목. [JeongganboLook.errorMarks]에 따라 종류별 색·아이콘(또는 붉은 바탕·테두리)으로 칠한다.
 * @param selectedIndex 사용자가 고른 음의 연주 순서. 정간에 검은 테두리를 그린다.
 * @param onEventClick 있으면 음을 누를 수 있다.
 * @param maxColumns 앞에서부터 이 수의 각만 그린다.
 * @param maxRows 각마다 첫 정간부터 이 수만 한 줄로 그린다.
 * @param columns 이 범위의 각만 그린다. 악보를 여러 단으로 나눠 그릴 때 쓴다.
 * @param dimUnmarked [errors]에 없는 정간을 흐리게 해 틀린 정간을 돋보이게 한다.
 * @param highlightColumns 이 범위의 각을 노란 테두리로 강조한다(틀린 칸이 몰린 각).
 * @param selectedCell 결과에서 선택한 표시 정간. 같은 이벤트를 잇는 다른 정간까지 선택하지 않는다.
 * @param onUnitClick 표시 자리를 선택할 때 쓴다. 연습의 [onEventClick]은 원본 이벤트를 그대로 전달한다.
 * @param look 모양([JeongganboLook]). 기본은 옛 와이어프레임, 연습 화면은 연두 보드 `.ms`.
 * @param decorations 정간(각, 정간)마다의 꾸밈([ScoreCellDecoration]). 같은 정간의 [errors] 표시보다 먼저 쓴다(이전 연주 비교·구간 처음/끝 등).
 * @param sectionColumns 이 범위의 각을 취약 구간으로 표시한다(2차): 연두 테두리 · 옅은 바깥 띠(`#EFF2D6` 3dp) · 크림 머리.
 * @param fadedColumns 이 각들은 머리까지 흐리게(35%) 그린다(구간 반복의 앞뒤 각).
 * @param cellMarks (각, 정간) → 덧그릴 표시([ScoreCellMark]). 악보 편집에서 고른 정간·빈 정간을 표시한다.
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
    annotations: Map<ScoreUnitKey, List<String>> = emptyMap(),
    look: JeongganboLook = JeongganboLook.Classic,
    decorations: Map<Pair<Int, Int>, ScoreCellDecoration> = emptyMap(),
    sectionColumns: IntRange? = null,
    fadedColumns: Set<Int> = emptySet(),
    cellMarks: Map<Pair<Int, Int>, ScoreCellMark> = emptyMap(),
) {
    // sp만 커져 분할 음·각 머리글이 잘리지 않도록 해당 악보의 기하도 함께 늘린다.
    // 화면 전체를 확대하지 않으며 기존 가로/세로 스크롤과 원본 줄바꿈을 유지한다.
    val renderMetrics = metrics.forFontScale(LocalDensity.current.fontScale)
    val strips = remember(song, maxColumns, maxRows, columns) { scoreStrips(song.score, song.jangdan, maxColumns, maxRows, columns) }
    val unitsByCell = remember(song) { song.displayUnits.groupBy { it.column to it.row } }
    // 원본에는 대강 경계가 없다. 예전 장단의 고정 분할을 새 악보에 덧씌우지 않되, 보드처럼 각 길이가 장단 정간 수와 같으면 선만 긋는다.
    val patternStarts = song.jangdan?.daegangStarts.orEmpty()
    fun daegangStarts(column: Int): List<Int> = when {
        song.score.timing == null -> patternStarts
        look.daegangWhenFits && song.score.columns[column].size == song.jangdan?.gak -> patternStarts
        else -> emptyList()
    }
    val solfege = song.solfege.takeIf { notation.showSolfege }

    // 재생·연주 중에는 현재 음도 진행 위치에서 함께 읽는다. 채우기와 가로 따라가기는 매 프레임, 화면 구성은 음이 바뀔 때만 갱신한다.
    val live = playback != null
    val cursor = rememberScoreCursor(playback)
    val shownIndex = if (live) cursor.index ?: currentIndex else currentIndex
    val currentUnit by remember(song, shownIndex, live) {
        derivedStateOf { shownIndex?.let { song.displayUnitAt(it, if (live) cursor.fraction else 0f) } }
    }
    // 보드 `.ms-col.upcoming`: 연주 중 각의 마지막 정간에서 10% 넘게 지나면 다음 각 머리를 강조한다.
    val upcomingColumn by remember(song, live, look.upcomingMark) {
        derivedStateOf {
            val unit = currentUnit
            if (!look.upcomingMark || !live || unit == null) return@derivedStateOf null
            val lastRow = song.score.columns[unit.column].size - 1
            if (unit.row == lastRow && cursor.fraction >= 0.1f && unit.column + 1 < song.score.columns.size) unit.column + 1 else null
        }
    }

    if (scrollState != null) {
        FollowPlayhead(
            scrollState = scrollState,
            playheadStrip = { cursor.index?.takeIf { live }?.let { playheadStrip(song, strips, unitsByCell, it, cursor.fraction) } },
            follow = follow,
            metrics = renderMetrics,
            onUserScroll = onUserScroll,
        )
    }

    // 노래방처럼 지금 연주하는 표시 자리의 글자를 붉게 한다. 음(표시 자리)이 바뀔 때만 다시 그린다.
    val playingKey = if (live) currentUnit?.key else null

    val rowModifier = if (scrollState != null) {
        Modifier.horizontalScroll(scrollState, reverseScrolling = true)
    } else {
        Modifier.wrapContentWidth(align = Alignment.End, unbounded = true)
    }
    Box(modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopEnd) {
        Row(
            modifier = rowModifier.semantics { isTraversalGroup = true }
                .then(
                    if (look.joined) {
                        Modifier.clip(JoinedShape).border(1.dp, JoinedOuterLine, JoinedShape).padding(1.dp)
                    } else {
                        Modifier.padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 10.dp)
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
            verticalAlignment = Alignment.Top,
        ) {
            for ((readingIndex, strip) in strips.withIndex().reversed()) {
                val starts = daegangStarts(strip.column)
                ScoreStripColumn(
                    strip, renderMetrics, readingIndex, look,
                    highlighted = highlightColumns != null && strip.column in highlightColumns,
                    section = sectionColumns != null && strip.column in sectionColumns,
                    faded = strip.column in fadedColumns,
                    upcoming = upcomingColumn == strip.column,
                    leftDivider = look.joined && readingIndex != strips.lastIndex,
                ) { row ->
                    ScoreCellBox(
                        units = unitsByCell[strip.column to row].orEmpty(),
                        metrics = renderMetrics,
                        look = look,
                        drawTopLine = !(strip.strip > 0 && row == strip.rows.first),
                        isDaegangStart = row in starts,
                        notation = notation,
                        solfege = solfege,
                        currentIndex = shownIndex,
                        currentEvent = shownIndex?.let { song.timeline.getOrNull(it) },
                        highlight = highlight,
                        live = live,
                        fraction = { cursor.fraction },
                        error = errors[strip.column to row],
                        decoration = decorations[strip.column to row],
                        dimmed = dimUnmarked && (strip.column to row) !in errors && (strip.column to row) !in decorations,
                        selectedIndex = selectedIndex,
                        selectedCell = selectedCell?.let { it == (strip.column to row) },
                        onEventClick = onEventClick,
                        onUnitClick = onUnitClick,
                        annotations = annotations,
                        cellMark = cellMarks[strip.column to row],
                        playingKey = playingKey,
                    )
                }
            }
        }
    }
}

/**
 * 진행 위치를 줄 단위로 센 값. 정수부는 [strips]에서의 줄 번호(오른쪽부터), 소수부는 그 줄의 정간 가운데 지난 몫이다.
 * 정간 안에서도 지난 만큼 늘어나 가로 따라가기가 끊기지 않는다.
 */
internal fun playheadStrip(
    song: Song,
    strips: List<ScoreStrip>,
    unitsByCell: Map<Pair<Int, Int>, List<ScoreDisplayUnit>>,
    index: Int,
    fraction: Float,
): Double? {
    val event = song.timeline.getOrNull(index) ?: return null
    val unit = song.displayUnitAt(index, fraction) ?: return null
    val stripIndex = strips.indexOfFirst { it.column == unit.column && unit.row in it.rows }
    if (stripIndex < 0) return null
    val strip = strips[stripIndex]
    val cellStart = unitsByCell[unit.column to unit.row]?.minOfOrNull { it.unitStart } ?: unit.unitStart
    val inCell = (event.unitStart + event.unitDuration * fraction - cellStart).coerceIn(0.0, 1.0)
    return stripIndex + (unit.row - strip.rows.first + inCell) / strip.rows.count()
}

/**
 * 재생·연주 중 악보를 가로로 따라간다. 한 줄을 연주하는 동안은 악보를 멈춰 두고, 다음 줄로 넘어가면 그 줄의 오른쪽 끝이
 * 화면 오른쪽에서 1/3 지점에 오도록 짧게([FollowStepMillis]) 넘긴다. 곡 처음과 끝에서는 악보 끝을 넘지 않는다.
 * 사용자가 끌면 따라가기를 멈추도록 알린다.
 */
@Composable
private fun FollowPlayhead(
    scrollState: ScrollState,
    playheadStrip: () -> Double?,
    follow: Boolean,
    metrics: JeongganboMetrics,
    onUserScroll: () -> Unit,
) {
    val density = LocalDensity.current
    val latestOnUserScroll by rememberUpdatedState(onUserScroll)
    LaunchedEffect(scrollState) {
        scrollState.interactionSource.interactions.collect { if (it is DragInteraction.Start) latestOnUserScroll() }
    }
    val target by rememberUpdatedState {
        playheadStrip()?.let { strip ->
            with(density) {
                val x = 2.dp.toPx() + floor(strip).toFloat() * (metrics.columnWidth + metrics.columnGap).toPx()
                (x - scrollState.viewportSize / 3f).roundToInt().coerceIn(0, scrollState.maxValue)
            }
        }
    }
    LaunchedEffect(follow, scrollState.viewportSize) {
        if (!follow || scrollState.viewportSize == 0) return@LaunchedEffect
        // 진행은 매 프레임 바뀌어도 넘길 자리는 줄이 바뀔 때만 바뀐다.
        snapshotFlow { target() }.collectLatest { if (it != null) scrollState.animateScrollTo(it, tween(FollowStepMillis)) }
    }
}

/** 다음 줄로 넘길 때 걸리는 시간. */
private const val FollowStepMillis = 300

/** 세로 줄 하나. 각의 첫 줄에는 각 번호 머리 칸을 두고, 이어지는 줄은 머리 칸 높이만큼 내려 정간 높이를 맞춘다. */
@Composable
private fun ScoreStripColumn(
    strip: ScoreStrip,
    metrics: JeongganboMetrics,
    readingIndex: Int,
    look: JeongganboLook,
    highlighted: Boolean = false,
    section: Boolean = false,
    faded: Boolean = false,
    upcoming: Boolean = false,
    leftDivider: Boolean = false,
    cell: @Composable (row: Int) -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(6.dp)
    val fade = if (faded) Modifier.alpha(FadedColumnAlpha) else Modifier
    if (look.joined) {
        // 보드 결과 `.ms-col`: 붙은 각, 왼쪽 경계선 `#B9B3A6`(맨 왼쪽 각은 없음).
        Column(
            Modifier
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = readingIndex.toFloat()
                }
                .width(metrics.columnWidth)
                .then(fade)
                .background(colors.surface)
                .drawWithContent {
                    drawContent()
                    if (leftDivider) drawRect(JoinedColumnLine, size = Size(1.dp.toPx(), size.height))
                },
        ) {
            if (strip.strip == 0) ColumnHead(strip.column, metrics, look)
            for (row in strip.rows) cell(row)
        }
        return
    }
    // 강조한 각은 바깥에 연한 노란 띠를 두르되, 각 너비와 정간 위치는 그대로 둔다. 취약 구간(2차)은 띠를 `accentTint`로 칠한다.
    val ring = if (highlighted || section) {
        Modifier.drawBehind {
            val spread = 3.dp.toPx()
            drawRoundRect(
                if (section) colors.accentTint else colors.accentSoft,
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
            .then(fade)
            .then(ring)
            .clip(shape)
            .background(if (look.board || look.soft) colors.surface else colors.background)
            .border(1.dp, if (highlighted || section) colors.accent else if (look.soft) colors.lineSoft else colors.line, shape),
    ) {
        if (strip.strip == 0) ColumnHead(strip.column, metrics, look, highlighted, upcoming, section)
        for (row in strip.rows) cell(row)
    }
}

@Composable
private fun ColumnHead(
    column: Int,
    metrics: JeongganboMetrics,
    look: JeongganboLook,
    highlighted: Boolean = false,
    upcoming: Boolean = false,
    section: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp)
    val background = when {
        upcoming || section -> colors.accentTint
        highlighted -> colors.accentSoft
        look.board -> colors.background
        else -> colors.surface
    }
    Row(
        Modifier.fillMaxWidth().height(metrics.headHeight).background(background),
        horizontalArrangement = Arrangement.spacedBy(if (look.headStartPill) 4.dp else 3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (look.headStartPill) {
            // 보드 결과 `.ms-lab`(24px · 11px): 첫 각만 연두 ‘시작’ 알약(9px)을 번호 앞에 둔다.
            if (column == 0) {
                Text(
                    stringResource(Res.string.score_column_start),
                    modifier = Modifier.background(colors.primary, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    style = style.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    color = colors.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text("${column + 1}", style = style.copy(fontSize = 11.sp, lineHeight = 11.sp), color = colors.textMuted, fontWeight = FontWeight.Normal)
        } else if (look.headCompact) {
            // 보드 `.ms-lab`: ‘시작 · 1각’ · ‘2각’. 다음 각 강조는 글자색을 바꾸고 ‘← 다음’을 덧붙인다.
            Text(
                stringResource(if (column == 0) Res.string.score_column_head_start else Res.string.score_column_head, column + 1),
                style = style,
                color = when {
                    upcoming -> colors.accentText
                    section -> colors.text
                    else -> colors.textMuted
                },
                fontWeight = if (section) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (upcoming) {
                Text(stringResource(Res.string.practice_upcoming), style = style.copy(fontSize = 8.sp, lineHeight = 8.sp), color = colors.accentText, fontWeight = FontWeight.Medium)
            }
        } else {
            if (column == 0) {
                Text(stringResource(Res.string.score_column_start), style = style, color = colors.accentText, fontWeight = FontWeight.Bold)
            }
            Text("${column + 1}", style = style, color = if (highlighted) colors.text else colors.textMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ScoreCellBox(
    units: List<ScoreDisplayUnit>,
    metrics: JeongganboMetrics,
    look: JeongganboLook,
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
    decoration: ScoreCellDecoration?,
    selectedIndex: Int?,
    selectedCell: Boolean?,
    onEventClick: ((ScoreEvent) -> Unit)?,
    onUnitClick: ((ScoreDisplayUnit) -> Unit)?,
    dimmed: Boolean = false,
    annotations: Map<ScoreUnitKey, List<String>> = emptyMap(),
    cellMark: ScoreCellMark? = null,
    playingKey: ScoreUnitKey? = null,
) {
    val colors = EolssuTheme.colors
    val board = look.board
    // 넘겨받은 꾸밈이 먼저이고, 없으면 틀린 종류를 모양([JeongganboLook.errorMarks])에 맞춰 칠한다.
    val mark = decoration ?: error?.let { errorDecoration(it, look.errorMarks) }
    val cellStart = units.minOfOrNull { it.unitStart } ?: 0.0
    val currentFraction by rememberUpdatedState(fraction)
    val progress by remember(units, currentEvent, live) {
        derivedStateOf {
            val position = currentEvent?.let { it.unitStart + if (live) it.unitDuration * currentFraction() else 0.0 }
            cellProgressAt(cellStart, position)
        }
    }
    val (fill, ring) = when (highlight) {
        ScoreHighlight.Position -> (if (look.soft) colors.accentTint else colors.accentSoft) to colors.accent
        ScoreHighlight.Recording -> colors.recSoft to colors.rec
    }
    val lineColor = when {
        isDaegangStart && board -> colors.text
        isDaegangStart -> colors.lineStrong
        look.soft -> colors.lineSoft
        else -> colors.line
    }
    val lineWidth = if (isDaegangStart) 2.dp else 1.dp
    val isSelected = selectedCell ?: (selectedIndex != null && units.any { it.event.index == selectedIndex })
    val rows = remember(units) { units.groupBy { it.unitRow }.values.toList() }
    val mutedGlyph = board && progress == CellProgress.Past && !isSelected

    var cellModifier = Modifier.fillMaxWidth().height(metrics.cellHeight)
    if (dimmed && !isSelected) cellModifier = cellModifier.alpha(0.55f)
    if (mark != null && mark.alpha < 1f && !isSelected) cellModifier = cellModifier.alpha(mark.alpha)
    // 재생·연주 중에는 모양과 관계없이 지나간 정간에 옅은 바탕을 깔아 어디까지 왔는지 보이게 한다.
    cellModifier = when {
        live && progress == CellProgress.Past -> cellModifier.background(colors.doneSoft)
        live || board -> cellModifier
        progress == CellProgress.Past -> cellModifier.alpha(0.5f)
        progress == CellProgress.Current -> cellModifier.background(fill)
        else -> cellModifier
    }
    if (live && progress == CellProgress.Current && currentEvent != null) {
        // 현재 정간은 노래방처럼 지난 만큼 위에서부터(가로로 나뉜 줄은 왼쪽부터) 같은 바탕으로 일정하게 채운다.
        cellModifier = cellModifier.drawBehind {
            val cellFraction = (currentEvent.unitStart + currentEvent.unitDuration * fraction() - cellStart).toFloat().coerceIn(0f, 1f)
            val rowProgress = cellFraction * rows.size
            val activeRow = rowProgress.toInt().coerceAtMost(rows.lastIndex)
            val rowFraction = (rowProgress - activeRow).coerceIn(0f, 1f)
            val rowHeight = size.height / rows.size
            val top = rowHeight * activeRow
            if (rows[activeRow].size > 1) {
                drawRect(colors.doneSoft, size = Size(size.width, top))
                drawRect(colors.doneSoft, topLeft = Offset(0f, top), size = Size(size.width * rowFraction, rowHeight))
            } else {
                drawRect(colors.doneSoft, size = Size(size.width, top + rowHeight * rowFraction))
            }
        }
    }
    mark?.fill?.let { cellModifier = cellModifier.background(it) }
    cellMark?.fill?.let { cellModifier = cellModifier.background(it) }
    if (drawTopLine) {
        cellModifier = cellModifier.drawBehind {
            drawRect(lineColor, size = size.copy(height = lineWidth.toPx()))
        }
    }
    val markRing = mark?.ring
    if (mark != null && markRing != null) {
        cellModifier = cellModifier.drawWithContent {
            drawContent()
            // 안쪽 테두리(inset box-shadow). 점선은 시안의 `outline: 2px dashed; outline-offset: -3px`처럼 가장자리에서 1~3dp 안쪽에 긋는다.
            val stroke = mark.ringWidth.toPx()
            val inset = if (mark.dashed) 2.dp.toPx() else stroke / 2
            drawRect(
                markRing,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    stroke,
                    pathEffect = if (mark.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                ),
            )
        }
    }
    val cellMarkRing = cellMark?.ring
    if (cellMark != null && cellMarkRing != null) {
        cellModifier = cellModifier.drawWithContent {
            drawContent()
            val stroke = cellMark.ringWidth.toPx()
            val inset = if (cellMark.dashed) 3.dp.toPx() else stroke / 2
            drawRect(
                cellMarkRing,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    stroke,
                    pathEffect = if (cellMark.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                ),
            )
        }
    }
    when {
        // 멈춰 있을 때 현재 정간은 2dp 테두리로 표시한다. 재생·연주 중에는 테두리 대신 연주하는 글자를 붉게 한다.
        progress == CellProgress.Current && !live -> cellModifier = cellModifier.border(2.dp, ring)
        isSelected -> cellModifier = cellModifier.border(2.dp, colors.text)
    }

    val notes: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            Column(Modifier.fillMaxSize()) {
                rows.forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        row.forEach { unit ->
                            ScoreNoteBox(
                                unit = unit,
                                splitCount = maxOf(rows.size, row.size),
                                metrics = metrics,
                                look = look,
                                showDivider = r > 0,
                                isCurrentInSplit = !live && units.size > 1 && unit.event.index == currentIndex,
                                notation = notation,
                                solfege = solfege,
                                mutedGlyph = mutedGlyph,
                                playing = playingKey != null && unit.key == playingKey,
                                onClick = onUnitClick?.let { { it(unit) } } ?: onEventClick?.let { { it(unit.event) } },
                                isSelected = selectedCell ?: (unit.event.index == selectedIndex),
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                sigimsae = annotations[unit.key].orEmpty(),
                            )
                        }
                    }
                }
            }
            val tag = mark?.tag
            if (tag != null) {
                // `.ms-cell .tag`: 오른쪽 위 1dp 안쪽에 14dp 종류 색 상자와 흰 아이콘.
                ErrorKindTag(tag, Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp))
            }
            val edge = mark?.edgeLabel
            if (edge != null) {
                // 구간 처음·끝: 왼쪽 아래 2dp 안쪽 연두 알약, 9sp 굵은 글자.
                Text(
                    edge,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp, bottom = 2.dp)
                        .background(colors.accent, RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                    maxLines = 1,
                )
            }
            val markTag = cellMark?.tag
            if (markTag != null) {
                Text(
                    markTag,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 3.dp).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                )
            }
        }
    }
    val lane = look.lyricLane
    if (lane == null) {
        notes(cellModifier)
    } else {
        // 보드 `.ms-grid`: 율명 칸 오른쪽에 곁줄(가사 칸). 가사 데이터가 없으면 빈 칸이다.
        Row(Modifier.fillMaxWidth().height(metrics.cellHeight)) {
            notes(cellModifier.then(Modifier.weight(1f)))
            LyricLane(
                width = lane,
                drawTopLine = drawTopLine,
                lineColor = lineColor,
                lineWidth = lineWidth,
            )
        }
    }
}

/** 곁줄(`.ms-lyr`) 한 칸. 왼쪽 선과 위 선(대강이면 굵게)을 긋는다. */
@Composable
private fun LyricLane(width: androidx.compose.ui.unit.Dp, drawTopLine: Boolean, lineColor: Color, lineWidth: androidx.compose.ui.unit.Dp) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .drawBehind {
                drawRect(colors.line, size = Size(1.dp.toPx(), size.height))
                if (drawTopLine) drawRect(lineColor, size = Size(size.width, lineWidth.toPx()))
            },
    )
}

@Composable
private fun ScoreNoteBox(
    unit: ScoreDisplayUnit,
    splitCount: Int,
    metrics: JeongganboMetrics,
    look: JeongganboLook,
    showDivider: Boolean,
    isCurrentInSplit: Boolean,
    notation: ScoreNotation,
    solfege: Map<Yul, String>?,
    mutedGlyph: Boolean,
    onClick: (() -> Unit)?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    sigimsae: List<String> = emptyList(),
    playing: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val note = unit.note
    val isPitch = note is ScoreNote.Pitch || note is ScoreNote.SourcePitch
    val label = when (note) {
        is ScoreNote.Pitch -> note.yul.fullName
        is ScoreNote.SourcePitch -> note.fullName
        ScoreNote.Hold -> stringResource(Res.string.score_hold)
        // 편집 중인 악보의 빈 정간은 이벤트 자체가 빈 자리다. 공통 악보의 빈 자리는 앞 이벤트(쉼·지속)를 읽는다.
        ScoreNote.Blank -> stringResource(
            when (unit.event.note) {
                ScoreNote.Blank -> Res.string.score_blank
                ScoreNote.Rest -> Res.string.score_rest
                else -> Res.string.score_hold
            },
        )
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
        contentDescription = (listOf(label) + sigimsae.map(::scoreSigimsaeName)).joinToString(" · ")
        if (onClick != null) selected = isSelected
    }

    val yulSize = metrics.yulSizeFor(splitCount)
    val auxSize = metrics.auxSizeFor(splitCount)
    val glyphColor = when {
        playing -> colors.rec
        mutedGlyph -> colors.textDisabled
        isPitch -> colors.text
        look.board -> colors.textSub
        else -> colors.textMuted
    }
    Column(
        noteModifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically) {
        if (look.board && !isPitch) {
            // 보드 `.sym`: ㅡ·△는 산세리프 400.
            Text(
                note.symbol,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = yulSize, lineHeight = yulSize, fontWeight = FontWeight.Normal),
                color = glyphColor,
            )
        } else {
            ScoreNoteGlyph(
                note = note,
                modifier = Modifier.clearAndSetSemantics {},
                size = yulSize,
                color = glyphColor,
                fontWeight = if (isPitch) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        val glyphs = sigimsae.filter { scoreSigimsaeImage(it) != null }
        if (glyphs.isNotEmpty()) {
            Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                glyphs.forEach { ScoreSigimsaeGlyph(it, yulSize * 0.7f) }
            }
        }
        if (hangul != null || solfegeText != null) {
            val auxStyle = MaterialTheme.typography.labelSmall.copy(fontSize = auxSize, lineHeight = auxSize * 1.1f)
            val auxColor = when {
                playing -> colors.rec
                mutedGlyph -> colors.textDisabled
                else -> colors.textMuted
            }
            Column(Modifier.clearAndSetSemantics {}) {
                if (hangul != null) Text(hangul, style = auxStyle, color = auxColor)
                if (solfegeText != null) Text(solfegeText, style = auxStyle, color = if (look.board) auxColor else colors.playText)
            }
        }
      }
      sigimsae.filter { scoreSigimsaeImage(it) == null }.forEach {
          Text(scoreSigimsaeName(it), modifier = Modifier.clearAndSetSemantics {},
              fontSize = auxSize, lineHeight = auxSize * 1.1f, color = colors.textMuted)
      }
    }
}

/** 구간 반복에서 앞뒤 각을 흐리게 하는 불투명도(시안 `opacity: .35`). */
private const val FadedColumnAlpha = 0.35f

/** 보드 결과 `.ms`의 바깥 테두리(반지름 3, `#8E887D`)와 각 경계선(`#B9B3A6`). */
private val JoinedShape = RoundedCornerShape(3.dp)
private val JoinedOuterLine = Color(0xFF8E887D)
private val JoinedColumnLine = Color(0xFFB9B3A6)
