package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.ui.component.mouseDragScroll
import com.ssafy.eolssu.core.diagnostics.*
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceIn
import com.ssafy.eolssu.core.model.scoreLyricSlots
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
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
 * 율명 옆에 작게 붙이는 보조 표기(한글 읽는 법). 율명은 항상 가장 크게 그린다.
 * 계이름 표시는 쓰지 않아 없앴다(S15P31E102-716).
 */
@Immutable
data class ScoreNotation(val showHangul: Boolean = false)

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
 * @property description 색·태그로 표시한 상태를 화면 읽기 프로그램에도 알린다. 율명 설명은 보존한다.
 * @property selected 명시적인 편집 선택 상태. null이면 기존 이벤트·표시 정간 선택을 따른다.
 * @property fillInMillis 실시간 판정 바탕([JeongganboView]의 `unitMarks`를 `unitMarksAfterPass`로 칠할 때와 운지법 보기)이 이전 바탕에서 [fill]로 바뀌는 색 전환 시간.
 *   빈 바탕에서는 투명한 [fill]에서 시작한다. 표시가 사라지면 전환 없이 지운다.
 */
@Immutable
data class ScoreCellMark(
    val fill: Color? = null,
    val ring: Color? = null,
    val ringWidth: Dp = 2.dp,
    val dashed: Boolean = false,
    val tag: String? = null,
    val description: String? = null,
    val selected: Boolean? = null,
    val fillInMillis: Int = 0,
)

/**
 * 정간보. 각 0을 가장 오른쪽에 두고 다음 각을 왼쪽에 놓아, 위 → 아래, 오른쪽 → 왼쪽으로 읽는다.
 * 원본의 가변 각 길이를 유지하고, 긴 각은 여러 줄로 나눈다. 기존 샘플에만 장단의 대강 선을 적용한다.
 *
 * @param scrollState 가로 스크롤 상태. 처음에는 오른쪽 끝(시작 각)이 보인다. null이면 스크롤 없이 넘치는 왼쪽을 자른다(미리보기).
 * @param currentIndex 현재 위치의 연주 순서([ScoreEvent.index]). 그 정간을 강조하고 앞의 정간을 흐리게 한다.
 * @param playback 재생·연주 중이면 진행 모습([ScorePlayback])으로 그린다. 이때 현재 음은 [currentIndex] 대신 진행 위치에서 읽는다.
 * @param follow 현재 위치가 화면 밖으로 나가면 가로로 따라간다. 사용자가 악보를 끌면 [onUserScroll]을 부른다.
 * @param userScrollEnabled false면 사용자가 악보를 끌어 넘길 수 없다. 따라가기([follow])는 그대로 움직인다(평가 연주 중).
 * @param errors 틀린 정간(각, 정간) → 틀린 항목. 종류별 색·아이콘으로 칠한다. 곁줄(가사 칸)이 있으면 바탕·테두리는 가사 칸까지 덮는다
 *   ([decorations]·[section]도 같다). 종류 아이콘은 율명 칸 오른쪽 위에 둔다.
 * @param errorKinds 틀린 정간(각, 정간) → 그 정간의 틀린 종류 전부. 있으면 종류마다 아이콘 상자를 오른쪽 끝에 세로로 쌓는다(한 정간의 복합 오류).
 *   바탕·테두리는 [errors]의 대표 종류로 칠한다.
 * @param unitErrors 소박(표시 자리)마다의 틀린 종류. 비어 있지 않으면 나뉜 정간은 정간 전체 대신 틀린 소박만 칠한다.
 *   그 정간에 [decorations]가 있어도 정간을 칠하지 않는 꾸밈([ScoreCellDecoration.paintsCell]이 거짓)이면 함께 그린다.
 * @param selectedIndex 사용자가 고른 음의 연주 순서. 정간에 회색 테두리를 그린다.
 * @param onEventClick 있으면 음을 누를 수 있다.
 * @param maxColumns 앞에서부터 이 수의 각만 그린다.
 * @param maxRows 각마다 첫 정간부터 이 수만 한 줄로 그린다.
 * @param columns 이 범위의 각만 그린다. 악보를 여러 단으로 나눠 그릴 때 쓴다.
 * @param dimUnmarked [errors]에 없는 정간을 흐리게 해 틀린 정간을 돋보이게 한다.
 * @param highlightColumns 이 범위의 각을 노란 테두리로 강조한다(틀린 칸이 몰린 각).
 * @param selectedCell 선택한 표시 정간. 같은 이벤트를 잇는 다른 정간까지 선택하지 않는다.
 * @param onUnitClick 표시 자리를 선택할 때 쓴다. 이음과 빈 자리가 원본 이벤트를 공유해도 표시 좌표는 다르다.
 * @param look 모양([JeongganboLook]). 기본은 옛 와이어프레임, 연습 화면은 연두 보드 `.ms`.
 * @param decorations 정간(각, 정간)마다의 꾸밈([ScoreCellDecoration]). 같은 정간의 [errors] 표시보다 먼저 쓴다(이전 연주 비교·구간 처음/끝 등).
 * @param section 고른·연주한 구간([ScoreSection]). 처음부터 끝까지 모든 정간에 연두 바탕(`accentTint` `#EFF2D6`)을 깐다.
 *   각 표시(연두 테두리 · 옅은 바깥 띠 `#EFF2D6` 3dp · 크림 머리)는 각 전체가 구간에 든 각([ScoreSection.fullColumns])에만 둔다.
 *   나머지 각의 구간 정간은 칸 선 바로 안쪽에 연두 테두리를 두르고, 틀린·고른 정간 테두리는 그 안쪽에 그려 두 겹으로 보인다.
 * @param sectionColumns 정간은 칠하지 않고 이 범위의 각에만 각 표시를 둔다(연주 결과의 각 단위 취약 구간).
 * @param fadedColumns 이 각들은 머리까지 흐리게(35%) 그린다(구간 반복의 앞뒤 각).
 * @param cellMarks (각, 정간) → 덧그릴 표시([ScoreCellMark]). 악보 편집에서 고른 정간·빈 정간을 표시한다.
 * @param focusCell 실제 표시 정간(각, 정간)을 가로·세로 스크롤 안에 보이게 한다. 줄을 나눈 각도 원본 좌표로 지정한다.
 * @param focusRequestKey 같은 정간으로 다시 이동할 때 증가시킨다. 사용자가 다른 곳으로 스크롤한 뒤에도 다시 보이게 한다.
 * @param unitMarksAfterPass 재생·연주 중에는 [unitMarks]를 진행이 그 표시 자리를 다 지난 뒤에만 칠한다.
 *   연주 중인 자리의 진행 채움을 덮지 않게 한다(연주 평가의 실시간 판정). 진행 모습([playback])이 없으면 모두 칠한다.
 *   바탕은 [ScoreCellMark.fillInMillis] 동안 색 전환으로 바꾼다(S15P31E102-980).
 * @param wholeCellHoldAsBlank 정간 전체를 잇는 이음(ㅡ)을 정간보 관행대로 빈칸으로 그린다([displayedNote]).
 *   원본 기호를 고치는 편집 화면은 false로 원본 표기를 그대로 보인다.
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
    userScrollEnabled: Boolean = true,
    errors: Map<Pair<Int, Int>, ScoreErrorKind> = emptyMap(),
    errorKinds: Map<Pair<Int, Int>, List<ScoreErrorKind>> = emptyMap(),
    unitErrors: Map<ScoreUnitKey, List<ScoreErrorKind>> = emptyMap(),
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
    /** 연습 화면의 짧은 각 묶음. 원본 각·음표 좌표는 바꾸지 않는다. */
    columnsPerStack: Int = 1,
    /** 한 줄로 그릴 각을 대강 경계에서 두 줄로 나눈다([scoreStrips]). 각 번호 머리는 첫 줄에만 둔다. 원본 좌표는 바꾸지 않는다. */
    splitColumns: Boolean = false,
    stackGap: Dp = 12.dp,
    /** 원본 (각, 정간) 좌표별 높이. 다른 화면은 기존 고정 높이를 쓴다. */
    cellHeights: Map<Pair<Int, Int>, Dp> = emptyMap(),
    /** 묶음 전체가 공유하는 가로줄 높이. 각 머리글의 시작점을 수평으로 맞춘다. */
    stackRowHeights: List<Dp> = emptyList(),
    decorations: Map<Pair<Int, Int>, ScoreCellDecoration> = emptyMap(),
    section: ScoreSection? = null,
    sectionColumns: IntRange? = null,
    fadedColumns: Set<Int> = emptySet(),
    cellMarks: Map<Pair<Int, Int>, ScoreCellMark> = emptyMap(),
    /** 나눈 정간의 소박 하나만 칠하는 표시(편집기에서 고른 소박). 정간 전체 표시([cellMarks]) 위에 그린다. */
    unitMarks: Map<ScoreUnitKey, ScoreCellMark> = emptyMap(),
    unitMarksAfterPass: Boolean = false,
    focusCell: Pair<Int, Int>? = null,
    focusRequestKey: Int = 0,
    /** 연습 보드는 보이는 묶음과 양쪽 이웃만 구성하며 나머지는 같은 너비의 빈 공간으로 보존한다. */
    windowed: Boolean = false,
    wholeCellHoldAsBlank: Boolean = true,
) {
    // sp만 커져 분할 음·각 머리글이 잘리지 않도록 해당 악보의 기하도 함께 늘린다.
    // 화면 전체를 확대하지 않으며 기존 가로/세로 스크롤과 원본 줄바꿈을 유지한다.
    val renderMetrics = metrics.forFontScale(LocalDensity.current.fontScale)
    val stackSize = columnsPerStack.coerceAtLeast(1)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val strips = remember(song, maxColumns, maxRows, columns, splitColumns) {
        scoreStrips(song.score, song.jangdan, maxColumns, maxRows, columns, splitColumns)
    }
    val unitsByCell = remember(song) { song.displayUnits.groupBy { it.column to it.row } }
    val fullSectionColumns = remember(song, section) { section?.fullColumns(song.score) }
    // 원본에는 대강 경계가 없다. 예전 장단의 고정 분할을 새 악보에 덧씌우지 않되, 보드처럼 각 길이가 장단 정간 수와 같으면 선만 긋는다.
    val patternStarts = song.jangdan?.daegangStarts.orEmpty()
    fun daegangStarts(column: Int): List<Int> = when {
        song.score.timing == null -> patternStarts
        look.daegangWhenFits && song.score.columns[column].size == song.jangdan?.gak -> patternStarts
        else -> emptyList()
    }
    // 위아래로 붙은 두 정간이 모두 테두리를 그리면 맞닿은 변을 반씩만 그려 다른 변과 두께를 맞춘다.
    fun unitMode(cell: Pair<Int, Int>): Boolean =
        unitErrors.isNotEmpty() && decorations[cell]?.paintsCell != true && (unitsByCell[cell]?.size ?: 0) > 1
    fun hasRing(cell: Pair<Int, Int>): Boolean = decorations[cell]?.ring != null || (decorations[cell] == null && cell in errors)
    // 소박마다 칠한 정간은 맞닿는 줄(위 정간의 맨 아래 줄, 아래 정간의 맨 윗줄)의 소박이 틀렸는지로 본다.
    fun ringAtEdge(cell: Pair<Int, Int>, bottom: Boolean): Boolean {
        if (!unitMode(cell)) return hasRing(cell)
        val units = unitsByCell[cell].orEmpty()
        val edgeRow = if (bottom) units.maxOf { it.unitRow } else units.minOf { it.unitRow }
        return units.any { it.unitRow == edgeRow && !unitErrors[it.key].isNullOrEmpty() }
    }

    // 재생·연주 중에는 현재 음도 진행 위치에서 함께 읽는다. 채우기와 가로 따라가기는 매 프레임, 화면 구성은 음이 바뀔 때만 갱신한다.
    val live = playback != null
    val cursor = rememberScoreCursor(playback)
    val shownIndex = if (live) cursor.index ?: currentIndex else currentIndex
    val currentUnit by remember(song, shownIndex, live) {
        derivedStateOf { shownIndex?.let { song.displayUnitAt(it, if (live) cursor.fraction else 0f) } }
    }
    if (scrollState != null) {
        FollowPlayhead(
            scrollState = scrollState,
            // 녹음 기록은 live playback 없이 currentIndex로 위치를 받는다. 이 위치도 따라간다.
            playheadStrip = {
                (if (live) cursor.index else currentIndex)?.let {
                    playheadStrip(song, strips, unitsByCell, it, if (live) cursor.fraction else 0f, stackSize)
                }
            },
            follow = follow,
            metrics = renderMetrics,
            onUserScroll = onUserScroll,
        )
    }

    // 노래방처럼 지금 연주하는 표시 자리의 글자를 붉게 한다. 음(표시 자리)이 바뀔 때만 다시 그린다.
    val playingKey = if (live) currentUnit?.key else null

    val rowModifier = if (scrollState != null) {
        Modifier
            .mouseDragScroll(scrollState, reverseDirection = true, enabled = userScrollEnabled, onDragStart = onUserScroll)
            .horizontalScroll(scrollState, enabled = userScrollEnabled, reverseScrolling = true)
    } else {
        Modifier.wrapContentWidth(align = Alignment.End, unbounded = true)
    }
    val drawStrip: @Composable (Int, ScoreStrip) -> Unit = { readingIndex, strip ->
        val starts = daegangStarts(strip.column)
        val highlightedColumn = highlightColumns != null && strip.column in highlightColumns
        val sectionColumn = sectionColumns?.contains(strip.column) == true || fullSectionColumns?.contains(strip.column) == true
        // 각 테두리가 이미 연두로 둘러싼 각은 정간 테두리를 겹쳐 긋지 않는다.
        val columnOutlined = !look.joined && (highlightedColumn || sectionColumn)
        fun inSection(row: Int) = section?.contains(strip.column, row) == true
        // 구간 테두리를 두르는 정간. 구간 테두리가 사이에 놓이는 두 정간은 틀린 테두리의 맞닿은 변을 나눠 그리지 않는다.
        fun outlined(row: Int) = !columnOutlined && inSection(row)
        ScoreStripColumn(
            strip, renderMetrics, readingIndex, look,
            highlighted = highlightedColumn,
            section = sectionColumn,
            faded = strip.column in fadedColumns,
            leftDivider = look.joined && readingIndex != strips.lastIndex,
        ) { row ->
            val height = cellHeights[strip.column to row]
            ScoreCellBox(
                units = unitsByCell[strip.column to row].orEmpty(),
                lyric = song.score.columns[strip.column][row].lyric,
                metrics = if (height == null) renderMetrics else renderMetrics.copy(cellHeight = height * fontScale),
                look = look,
                drawTopLine = !(strip.strip > 0 && row == strip.rows.first),
                isDaegangStart = row in starts,
                notation = notation,
                currentIndex = shownIndex,
                currentEvent = shownIndex?.let { song.timeline.getOrNull(it) },
                highlight = highlight,
                live = live,
                fraction = { cursor.fraction },
                error = errors[strip.column to row],
                errorKinds = errorKinds[strip.column to row].orEmpty(),
                unitErrors = if (unitMode(strip.column to row)) unitErrors else emptyMap(),
                decoration = decorations[strip.column to row],
                inSection = inSection(row),
                sectionEdges = if (outlined(row)) {
                    RingEdges(
                        sharedTop = row - 1 in strip.rows && inSection(row - 1),
                        sharedBottom = row + 1 in strip.rows && inSection(row + 1),
                        coveredTop = strip.strip > 0 && row == strip.rows.first,
                        coveredBottom = row == strip.rows.last,
                        // 곁줄이 있으면 구간 테두리는 곁줄까지 감싸므로 오른쪽 끝은 늘 각 경계선 아래다.
                    )
                } else {
                    null
                },
                ringEdges = RingEdges(
                    sharedTop = row - 1 in strip.rows && outlined(row - 1) == outlined(row) && ringAtEdge(strip.column to row - 1, bottom = true),
                    sharedBottom = row + 1 in strip.rows && outlined(row + 1) == outlined(row) && ringAtEdge(strip.column to row + 1, bottom = false),
                    coveredTop = strip.strip > 0 && row == strip.rows.first,
                    coveredBottom = row == strip.rows.last,
                ),
                dimmed = dimUnmarked && (strip.column to row) !in errors && (strip.column to row) !in decorations,
                selectedIndex = selectedIndex,
                selectedCell = selectedCell?.let { it == (strip.column to row) },
                onEventClick = onEventClick,
                onUnitClick = onUnitClick,
                annotations = annotations,
                wholeCellHoldAsBlank = wholeCellHoldAsBlank,
                tempoMarks =song.score.tempoMarks.filterKeys { it.cellId == song.score.columns[strip.column][row].id }.values.flatten(),
                cellMark = cellMarks[strip.column to row],
                pickedUnits = unitMarks,
                pickedAfterPass = unitMarksAfterPass,
                playingKey = playingKey,
                focusRequestKey = focusRequestKey.takeIf { focusCell == (strip.column to row) },
            )
        }
    }
    val groups = remember(strips, stackSize) { strips.withIndex().toList().chunked(stackSize) }
    val density = LocalDensity.current
    val columnPx = with(density) { renderMetrics.columnWidth.roundToPx() }
    val gapPx = with(density) { metrics.columnGap.roundToPx() }
    val stridePx = (columnPx + gapPx).toFloat()
    val drawRow: @Composable (Int) -> Unit = { viewportPx ->
        val window by remember(groups, windowed, scrollState, focusCell, stridePx, viewportPx) {
            derivedStateOf {
                if (windowed && scrollState != null && focusCell == null)
                    visibleScoreStacks(groups.size, scrollState.value, viewportPx, stridePx)
                else groups.indices
            }
        }
        fun omittedWidth(count: Int): Dp = with(density) { scoreStackSpanPx(count, columnPx, gapPx).toDp() }
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
            val omittedLeft = if (window.isEmpty()) 0 else groups.lastIndex - window.last
            if (omittedLeft > 0) Spacer(Modifier.width(omittedWidth(omittedLeft)))
            if (stackSize == 1) {
                for (readingIndex in window.reversed()) drawStrip(readingIndex, strips[readingIndex])
            } else {
                for (groupIndex in window.reversed()) {
                    val group = groups[groupIndex]
                    Column(
                        Modifier.semantics { isTraversalGroup = true; traversalIndex = groupIndex.toFloat() },
                        verticalArrangement = Arrangement.spacedBy(stackGap),
                    ) {
                        for ((row, indexedStrip) in group.withIndex()) {
                            val rowHeight = stackRowHeights.getOrNull(row)
                            Box(if (rowHeight == null) Modifier else Modifier.height(rowHeight * fontScale)) {
                                drawStrip(indexedStrip.index, indexedStrip.value)
                            }
                        }
                    }
                }
            }
            if (!window.isEmpty() && window.first > 0) Spacer(Modifier.width(omittedWidth(window.first)))
        }
    }
    if (windowed && scrollState != null && focusCell == null) {
        BoxWithConstraints(modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopEnd) {
            drawRow(constraints.maxWidth)
        }
    } else {
        Box(modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopEnd) { drawRow(Int.MAX_VALUE) }
    }
}

/**
 * 진행 위치를 줄 단위로 센 값. 정수부는 [strips]에서의 줄 번호(오른쪽부터), 소수부는 그 줄의 정간 가운데 지난 몫이다.
 * 정간 안에서도 지난 만큼 늘어나 가로 따라가기가 끊기지 않는다. 세로 묶음에서는 묶음 단위로 환산한다.
 */
internal fun playheadStrip(
    song: Song,
    strips: List<ScoreStrip>,
    unitsByCell: Map<Pair<Int, Int>, List<ScoreDisplayUnit>>,
    index: Int,
    fraction: Float,
    columnsPerStack: Int = 1,
): Double? {
    val event = song.timeline.getOrNull(index) ?: return null
    val unit = song.displayUnitAt(index, fraction) ?: return null
    val stripIndex = strips.indexOfFirst { it.column == unit.column && unit.row in it.rows }
    if (stripIndex < 0) return null
    val strip = strips[stripIndex]
    val cellStart = unitsByCell[unit.column to unit.row]?.minOfOrNull { it.unitStart } ?: unit.unitStart
    val inCell = (event.unitStart + event.unitDuration * fraction - cellStart).coerceIn(0.0, 1.0)
    return (stripIndex + (unit.row - strip.rows.first + inCell) / strip.rows.count()) / columnsPerStack.coerceAtLeast(1)
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
    leftDivider: Boolean = false,
    cell: @Composable (row: Int) -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(ColumnCorner)
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
        if (strip.strip == 0) ColumnHead(strip.column, metrics, look, highlighted, section)
        for (row in strip.rows) cell(row)
    }
}

@Composable
private fun ColumnHead(
    column: Int,
    metrics: JeongganboMetrics,
    look: JeongganboLook,
    highlighted: Boolean = false,
    section: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp)
    val background = when {
        section -> colors.accentTint
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
            // 보드 `.ms-lab`: ‘시작 · 1각’ · ‘2각’.
            Text(
                stringResource(if (column == 0) Res.string.score_column_head_start else Res.string.score_column_head, column + 1),
                style = style,
                color = if (section) colors.text else colors.textMuted,
                fontWeight = if (section) FontWeight.SemiBold else FontWeight.Normal,
            )
        } else {
            if (column == 0) {
                Text(stringResource(Res.string.score_column_start), style = style, color = colors.accentText, fontWeight = FontWeight.SemiBold)
            }
            Text("${column + 1}", style = style, color = if (highlighted) colors.text else colors.textMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ScoreCellBox(
    units: List<ScoreDisplayUnit>,
    lyric: String?,
    metrics: JeongganboMetrics,
    look: JeongganboLook,
    drawTopLine: Boolean,
    isDaegangStart: Boolean,
    notation: ScoreNotation,
    currentIndex: Int?,
    currentEvent: ScoreEvent?,
    highlight: ScoreHighlight,
    live: Boolean,
    fraction: () -> Float,
    error: ScoreErrorKind?,
    errorKinds: List<ScoreErrorKind> = emptyList(),
    unitErrors: Map<ScoreUnitKey, List<ScoreErrorKind>> = emptyMap(),
    decoration: ScoreCellDecoration?,
    inSection: Boolean = false,
    sectionEdges: RingEdges? = null,
    ringEdges: RingEdges = RingEdges(),
    selectedIndex: Int?,
    selectedCell: Boolean?,
    onEventClick: ((ScoreEvent) -> Unit)?,
    onUnitClick: ((ScoreDisplayUnit) -> Unit)?,
    dimmed: Boolean = false,
    annotations: Map<ScoreUnitKey, List<String>> = emptyMap(),
    wholeCellHoldAsBlank: Boolean = true,
    tempoMarks: List<HanbaeMark> = emptyList(),
    cellMark: ScoreCellMark? = null,
    pickedUnits: Map<ScoreUnitKey, ScoreCellMark> = emptyMap(),
    pickedAfterPass: Boolean = false,
    playingKey: ScoreUnitKey? = null,
    focusRequestKey: Int? = null,
) {
    val colors = EolssuTheme.colors
    val board = look.board
    // 넘겨받은 꾸밈이 먼저이고, 없으면 틀린 종류의 색·아이콘으로 칠한다.
    // 소박마다 판정이 있으면([unitErrors]) 정간 전체는 칠하지 않고 틀린 소박만 칠한다.
    val unitMarks = decoration?.paintsCell != true && unitErrors.isNotEmpty()
    val mark = decoration ?: error?.takeUnless { unitMarks }?.let { errorDecoration(it) }
    val cellStart = units.minOfOrNull { it.unitStart } ?: 0.0
    val currentFraction by rememberUpdatedState(fraction)
    val progress by remember(units, currentEvent, live) {
        derivedStateOf {
            val position = currentEvent?.let { it.unitStart + if (live) it.unitDuration * currentFraction() else 0.0 }
            cellProgressAt(cellStart, position)
        }
    }
    // 진행이 다 지난 표시 자리. null이면 [pickedUnits]를 모두 칠한다. 자리가 지날 때만 바뀌어 매 프레임 다시 그리지 않는다.
    val passedUnits by remember(units, currentEvent, live, pickedAfterPass) {
        derivedStateOf {
            if (!pickedAfterPass || !live) return@derivedStateOf null
            val position = currentEvent?.let { it.unitStart + it.unitDuration * currentFraction() }
            units.filter { unitPassed(it, position) }.mapTo(HashSet()) { it.key }
        }
    }
    val (fill, ring) = when (highlight) {
        ScoreHighlight.Position -> (if (look.soft) colors.accentTint else colors.accentSoft) to colors.accent
        ScoreHighlight.Recording -> colors.recSoft to colors.rec
    }
    val lineColor = when {
        isDaegangStart && board && look.softDaegang -> colors.lineStrong
        isDaegangStart && board -> colors.text
        isDaegangStart -> colors.lineStrong
        look.soft -> colors.lineSoft
        else -> colors.line
    }
    val lineWidth = if (isDaegangStart) 2.dp else 1.dp
    val isSelected = selectedCell ?: (selectedIndex != null && units.any { it.event.index == selectedIndex })
    val rows = remember(units) { units.groupBy { it.unitRow }.values.toList() }
    val mutedGlyph = board && progress == CellProgress.Past && !isSelected
    // 구간 테두리가 정간 가장자리에서 차지하는 깊이(dp). 틀린·고른·현재 정간 테두리는 그 안쪽에 그리고,
    // 테두리에 붙여 두는 종류 아이콘·구간 알약도 테두리가 들어간 만큼([ringShift]) 함께 들인다.
    val topLine = if (drawTopLine) lineWidth else 0.dp
    // 곁줄(가사 칸)이 있으면 구간 표시와 틀린 바탕·테두리는 율명 칸과 곁줄을 한 칸으로 감싼다([frame]).
    // 진행 채움·현재·고른 정간 테두리·편집 표시는 율명 칸에만 그린다([cellModifier]).
    val lane = look.lyricLane
    val frameReach = sectionEdges?.let { sectionOutlineReach(it, SectionOutlineWidth.value, LineCover.value, topLine.value) } ?: CellInsets.None
    // 율명 칸 안의 표시가 피할 구간 테두리. 곁줄이 있으면 오른쪽 테두리는 곁줄 끝에 있어 율명 칸에는 닿지 않는다.
    val sectionReach = if (lane == null) frameReach else frameReach.copy(right = 0f)
    val ringShift = cellRingInsets(ringEdges, LineCover.value, sectionReach) - cellRingInsets(ringEdges, LineCover.value)

    var cellModifier = Modifier.fillMaxWidth().height(metrics.cellHeight)
    var frame: Modifier = Modifier
    fun onWhole(m: Modifier) {
        if (lane == null) cellModifier = cellModifier.then(m) else frame = frame.then(m)
    }
    if (focusRequestKey != null) {
        // 렌더링한 정간의 실제 위치를 사용한다. 긴 각의 줄바꿈·가변 높이·양방향 스크롤도 같은 요청으로 처리한다.
        val requester = remember { BringIntoViewRequester() }
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(focusRequestKey, placed) {
            if (placed) requester.bringIntoView()
        }
        cellModifier = cellModifier.bringIntoViewRequester(requester).onGloballyPositioned { placed = true }
    }
    if (units.isEmpty() && (cellMark?.description != null || cellMark?.selected != null)) {
        cellModifier = cellModifier.semantics {
            cellMark?.description?.let { stateDescription = it }
            cellMark?.selected?.let { selected = it }
        }
    }
    if (dimmed && !isSelected) onWhole(Modifier.alpha(0.55f))
    if (mark != null && mark.alpha < 1f && !isSelected) onWhole(Modifier.alpha(mark.alpha))
    // 구간 정간 바탕은 맨 아래에 깔아 진행 채움·현재 표시·틀린 종류 바탕이 그 위에 보이게 한다.
    if (inSection) onWhole(Modifier.background(colors.accentTint))
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
    mark?.fill?.let { onWhole(Modifier.background(it)) }
    cellMark?.fill?.let { cellModifier = cellModifier.background(it) }
    if (drawTopLine) {
        cellModifier = cellModifier.drawBehind {
            drawRect(lineColor, size = size.copy(height = lineWidth.toPx()))
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
    // 멈춰 있을 때 현재 정간은 2dp 테두리로 표시한다. 재생·연주 중에는 테두리 대신 연주하는 글자를 붉게 한다.
    val outline = when {
        progress == CellProgress.Current && !live -> ring
        // 고른 정간은 먹색 대신 회색(textMuted #6B655D)으로, 대강선 회갈색(lineStrong)보다 조금 진하게 한다.
        isSelected -> colors.textMuted
        else -> null
    }
    // 구간 정간은 칸 선 바로 안쪽에 연두 테두리로 구간 바깥 둘레만 두른다. 틀린·고른·현재 정간 테두리는 그 안쪽에 그려 겹치지 않는다.
    // 각 모서리의 둥근 안쪽 선이 가려지지 않게 맨 위에 그린다(먼저 붙인 그리기가 나중에 그려진다).
    if (sectionEdges != null) {
        val outlineColor = colors.accentTintLine
        onWhole(
            Modifier.drawWithCache {
                val shape = sectionOutlineShape(size, sectionEdges, SectionOutlineWidth.toPx(), LineCover.toPx(), topLine.toPx(), ColumnCorner.toPx())
                val band = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(shape.outer)
                    addRoundRect(shape.inner)
                }
                onDrawWithContent {
                    drawContent()
                    clipRect(shape.outer.left, shape.outer.top, shape.outer.right, shape.outer.bottom) { drawPath(band, outlineColor) }
                }
            },
        )
    }
    val markRing = mark?.ring
    if (markRing != null || outline != null) {
        // 틀린 정간 테두리 위에 고른·현재 정간 테두리를 그린다. 둘 다 각 경계선에 가리지 않게, 구간 테두리가 있으면 그 안쪽에 들여 그린다.
        // 틀린 테두리는 곁줄까지 감싸고, 고른·현재 정간 테두리는 그 위에 율명 칸만 감싼다.
        val whole = lane != null && markRing != null
        val rings = Modifier.drawWithContent {
            drawContent()
            val cover = LineCover.toPx()
            if (mark != null && markRing != null) {
                drawCellRing(
                    color = markRing,
                    width = mark.ringWidth.toPx(),
                    dash = if (mark.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                    edges = ringEdges,
                    cover = cover,
                    clear = frameReach * density,
                )
            }
            if (outline != null) {
                inset(0f, 0f, if (whole && lane != null) lane.toPx() else 0f, 0f) {
                    drawCellRing(outline, 2.dp.toPx(), null, RingEdges(coveredTop = ringEdges.coveredTop, coveredBottom = ringEdges.coveredBottom), cover, sectionReach * density)
                }
            }
        }
        if (whole) frame = frame.then(rings) else cellModifier = cellModifier.then(rings)
    }

    // 오른쪽 위 종류 아이콘(정간 하나에 하나 또는 세로로 쌓은 여러 개)이 차지하는 높이. 율명은 이 자리를 피해 놓는다.
    val cellTag = mark?.tag
    val cellTags = when {
        unitMarks -> emptyList()
        decoration == null && errorKinds.size > 1 -> errorKinds
        else -> listOfNotNull(cellTag)
    }
    val tagGap = 1.dp
    // 아이콘은 테두리 오른쪽 위 모서리에 붙인다. 구간 테두리 안쪽으로 들어간 테두리를 따라 같이 들인다.
    val tagTop = 1.dp + ringShift.top.dp
    val tagEnd = 1.dp + ringShift.right.dp
    val tagRoom = metrics.cellHeight - tagTop - 1.dp - ringShift.bottom.dp
    val tagSize = if (cellTags.size > 1) ((tagRoom - tagGap * (cellTags.size - 1)) / cellTags.size).coerceIn(9.dp, 14.dp) else 14.dp
    val tagBottom = if (cellTags.isEmpty()) 0.dp else tagTop + tagSize * cellTags.size + tagGap * (cellTags.size - 1)
    // 왼쪽 아래 구간 ‘처음’·‘끝’ 알약(9sp 글자 · 줄 11sp · 안쪽 가로 5 세로 1 · 바깥 2)이 차지하는 대략의 크기. 한글은 한 글자가 1em이다.
    val edgeLabel = mark?.edgeLabel
    val edgeReserve = edgeLabel?.let {
        with(LocalDensity.current) {
            DpSize(9.sp.toDp() * it.length + 10.dp + 1.dp + ringShift.left.dp, 11.sp.toDp() + 2.dp + 3.dp + ringShift.bottom.dp)
        }
    }

    val notes: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            Column(Modifier.fillMaxSize().padding(end = if (tempoMarks.isEmpty()) 0.dp else 14.dp)) {
                rows.forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        row.forEachIndexed { c, unit ->
                            val subKinds = if (unitMarks) unitErrors[unit.key].orEmpty() else emptyList()
                            val subMark = subKinds.firstOrNull()?.let { errorDecoration(it) }
                            // 정간 가장자리에 닿는 소박만 구간 테두리 안쪽으로 들인다.
                            val unitReach = CellInsets(
                                left = if (c == 0) sectionReach.left else 0f,
                                top = if (r == 0) sectionReach.top else 0f,
                                right = if (c == row.lastIndex) sectionReach.right else 0f,
                                bottom = if (r == rows.lastIndex) sectionReach.bottom else 0f,
                            )
                            val unitTagTop = 1.dp + if (r == 0) ringShift.top.dp else 0.dp
                            val unitTagEnd = 1.dp + if (c == row.lastIndex) ringShift.right.dp else 0.dp
                            // 구간 테두리가 위에 놓이는 소박은 바탕이 정간 윗선을 덮지 않게 그 아래부터 칠한다. 테두리가 내려가 윗선이 드러나는 자리다.
                            val unitFillTop = if (unitReach.top > 0f) topLine else 0.dp
                            val subModifier = if (subMark == null) Modifier else {
                                fun marked(u: ScoreDisplayUnit?) = u != null && !unitErrors[u.key].isNullOrEmpty()
                                val subEdges = RingEdges(
                                    sharedTop = if (r == 0) ringEdges.sharedTop else rows[r - 1].any { marked(it) },
                                    sharedBottom = if (r == rows.lastIndex) ringEdges.sharedBottom else rows[r + 1].any { marked(it) },
                                    coveredTop = r == 0 && ringEdges.coveredTop,
                                    coveredBottom = r == rows.lastIndex && ringEdges.coveredBottom,
                                    sharedLeft = marked(row.getOrNull(c - 1)),
                                    sharedRight = marked(row.getOrNull(c + 1)),
                                    coveredLeft = c == 0,
                                    coveredRight = c == row.lastIndex,
                                    // 줄의 맨 오른쪽 소박은 테두리를 곁줄까지 잇는다([LyricLane]이 나머지를 그린다).
                                    openRight = lane != null && c == row.lastIndex,
                                )
                                val subRing = subMark.ring
                                Modifier.unitFill(subMark.fill, unitFillTop).drawWithContent {
                                    drawContent()
                                    if (subRing != null) {
                                        drawCellRing(
                                            subRing, subMark.ringWidth.toPx(),
                                            if (subMark.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                                            subEdges, LineCover.toPx(), unitReach * density,
                                        )
                                    }
                                }
                            }
                            val pick = pickedUnits[unit.key]?.takeIf { passedUnits?.contains(unit.key) != false }
                            val liveFill = if (pickedAfterPass) key(unit.key) { animatedMarkFill(pick?.fill, pick?.fillInMillis ?: 0) } else null
                            val fillModifier = if (liveFill == null) Modifier.unitFill(pick?.fill, unitFillTop) else Modifier.drawBehind {
                                val color = liveFill.value
                                if (color.alpha > 0f) {
                                    val top = unitFillTop.toPx()
                                    drawRect(color, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
                                }
                            }
                            val pickModifier = if (pick == null) fillModifier else fillModifier
                                .drawWithContent {
                                    drawContent()
                                    val ring = pick.ring ?: return@drawWithContent
                                    val stroke = pick.ringWidth.toPx()
                                    val clear = unitReach * density
                                    drawRect(
                                        ring,
                                        topLeft = Offset(clear.left + stroke / 2, clear.top + stroke / 2),
                                        size = Size(size.width - clear.left - clear.right - stroke, size.height - clear.top - clear.bottom - stroke),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke,
                                            pathEffect = if (pick.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null),
                                    )
                                }
                            // 이 소박 오른쪽 위를 덮는 아이콘 자리: 정간 아이콘은 맨 오른쪽 소박 가운데 아이콘 높이에 걸친 줄만, 소박 아이콘은 그 소박.
                            val unitTop = metrics.cellHeight * r / rows.size
                            val subTagSize = (metrics.cellHeight / rows.size - 2.dp - unitTagTop).coerceIn(9.dp, 14.dp)
                            val reserve = when {
                                pick?.tag != null -> DpSize(12.dp, 12.dp)
                                subKinds.isNotEmpty() -> DpSize(subTagSize * subKinds.size + 1.dp * (subKinds.size - 1) + unitTagEnd - 1.dp, subTagSize + 1.dp + unitTagTop)
                                c == row.lastIndex && tagBottom > unitTop -> DpSize(tagSize + tagEnd - 1.dp, tagBottom - unitTop + 1.dp)
                                else -> null
                            }
                            Box(Modifier.weight(1f).fillMaxSize().then(subModifier).then(pickModifier)) {
                            ScoreNoteBox(
                                reserveTopEnd = reserve,
                                reserveBottomStart = edgeReserve?.takeIf { r == rows.lastIndex && c == 0 },
                                // 이 람다에 분기·지역 변수를 더하면 Android 검증기가 클래스를 거부해(VerifyError) 함수 하나로 넘긴다.
                                unit = unit.forDisplay(units.size, annotations, wholeCellHoldAsBlank),
                                splitCount = maxOf(rows.size, row.size),
                                // 3소박 율명은 칸을 늘려도 정한 크기를 넘지 않는다(S15P31E102-1022).
                                lineHeight = if (rows.size > 1) {
                                    val glyphCell = metrics.threeSplitGlyphCell?.takeIf { rows.size >= 3 }
                                    (if (glyphCell != null) minOf(metrics.cellHeight, glyphCell) else metrics.cellHeight) / rows.size
                                } else null,
                                metrics = metrics,
                                look = look,
                                showDivider = r > 0,
                                isCurrentInSplit = !live && units.size > 1 && unit.event.index == currentIndex,
                                notation = notation,
                                mutedGlyph = mutedGlyph,
                                playing = playingKey != null && unit.key == playingKey,
                                onClick = onUnitClick?.let { { it(unit) } } ?: onEventClick?.let { { it(unit.event) } },
                                isSelected = pick?.selected ?: cellMark?.selected ?: selectedCell ?: (unit.event.index == selectedIndex),
                                markDescription = listOfNotNull(cellMark?.description, pick?.description).distinct()
                                    .joinToString(" · ").takeIf { it.isNotEmpty() },
                                modifier = Modifier.fillMaxSize(),
                                sigimsae = annotations[unit.key].orEmpty().filter { HanbaeMark.fromSymbol(it) == null },
                            )
                            pick?.tag?.let { tag ->
                                Text(tag,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 3.dp).clearAndSetSemantics {},
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold),
                                    color = colors.text,
                                    maxLines = 1,
                                )
                            }
                            if (subKinds.isNotEmpty()) {
                                // 틀린 소박의 오른쪽 위에 종류 아이콘. 나뉜 줄 높이에 맞춰 줄인다(최대 14dp, 최소 9dp).
                                Row(
                                    Modifier.align(Alignment.TopEnd).padding(top = unitTagTop, end = unitTagEnd),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    subKinds.forEach { ErrorKindTag(it, size = subTagSize, iconSize = subTagSize * 0.7f) }
                                }
                            }
                            }
                        }
                    }
                }
            }
            // 넘겨받은 꾸밈이 없을 때만 복합 오류 종류를 쓴다(이전 연주 비교 등은 꾸밈 하나를 그대로 그린다).
            if (cellTags.size == 1) {
                // `.ms-cell .tag`: 오른쪽 위 1dp 안쪽에 14dp 종류 색 상자와 흰 아이콘.
                ErrorKindTag(cellTags.single(), Modifier.align(Alignment.TopEnd).padding(top = tagTop, end = tagEnd))
            } else if (cellTags.size > 1) {
                // 한 정간의 여러 종류: 오른쪽 끝에 세로로 쌓아 율명을 가리지 않게 한다. 정간 높이에 들어가도록 상자를 줄인다(최대 14dp, 최소 9dp).
                Column(
                    Modifier.align(Alignment.TopEnd).padding(top = tagTop, end = tagEnd),
                    verticalArrangement = Arrangement.spacedBy(tagGap),
                ) {
                    cellTags.forEach { ErrorKindTag(it, size = tagSize, iconSize = tagSize * 0.7f) }
                }
            }
            if (edgeLabel != null) {
                // 구간 처음·끝: 왼쪽 아래 2dp 안쪽 연두 알약, 9sp 굵은 글자.
                Text(
                    edgeLabel,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp + ringShift.left.dp, bottom = 2.dp + ringShift.bottom.dp)
                        .background(colors.accent, RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                    maxLines = 1,
                )
            }
            tempoMarks.firstOrNull()?.let { tempo ->
                Text(tempo.glyph,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).semantics { contentDescription = tempo.label },
                    color = colors.text, fontSize = 9.sp, lineHeight = 9.sp,
                    style = MaterialTheme.typography.labelSmall)
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
    if (lane == null) {
        notes(cellModifier)
    } else {
        // 소박마다 칠한 정간은 줄의 맨 오른쪽 소박이 틀렸으면 그 줄의 곁줄까지 같은 바탕·테두리로 잇는다.
        val laneKinds = if (unitMarks) rows.map { row -> unitErrors[row.last().key]?.firstOrNull() } else emptyList()
        val laneEdges = laneSegmentEdges(laneKinds.map { it != null }, ringEdges)
        val segments = laneKinds.mapIndexed { r, kind ->
            val edges = laneEdges[r]
            if (kind == null || edges == null) {
                null
            } else {
                LaneSegment(
                    mark = errorDecoration(kind),
                    edges = edges,
                    clear = CellInsets(
                        top = if (r == 0) frameReach.top else 0f,
                        right = frameReach.right,
                        bottom = if (r == laneKinds.lastIndex) frameReach.bottom else 0f,
                    ),
                )
            }
        }
        // 보드 `.ms-grid`: 율명 칸 오른쪽에 곁줄(가사 칸). 가사 데이터가 없으면 빈 칸이다.
        Row(Modifier.fillMaxWidth().height(metrics.cellHeight).then(frame)) {
            notes(cellModifier.then(Modifier.weight(1f)))
            LyricLane(
                width = lane,
                lyric = lyric,
                units = units,
                drawTopLine = drawTopLine,
                lineColor = lineColor,
                lineWidth = lineWidth,
                segments = segments,
            )
        }
    }
}

/** 곁줄 한 줄에 이어 그리는 소박의 틀린 표시. [clear]는 dp 단위로 구간 테두리가 차지한 깊이다. */
@Immutable
private data class LaneSegment(val mark: ScoreCellDecoration, val edges: RingEdges, val clear: CellInsets)

/**
 * 곁줄의 가사 줄마다 소박 테두리를 이어 그릴 변. [marked]는 줄(위→아래)마다 맨 오른쪽 소박이 틀렸는지, [cell]은 이 정간 테두리의 위아래 변이다.
 * 왼쪽은 소박 쪽으로 열고, 위아래는 옆 줄도 이어 그리면 반 두께로 맞붙인다. 이을 표시가 없는 줄은 null이다.
 */
internal fun laneSegmentEdges(marked: List<Boolean>, cell: RingEdges): List<RingEdges?> = marked.mapIndexed { r, on ->
    if (!on) {
        null
    } else {
        RingEdges(
            sharedTop = if (r == 0) cell.sharedTop else marked[r - 1],
            sharedBottom = if (r == marked.lastIndex) cell.sharedBottom else marked[r + 1],
            coveredTop = r == 0 && cell.coveredTop,
            coveredBottom = r == marked.lastIndex && cell.coveredBottom,
            openLeft = true,
        )
    }
}

/**
 * 틀린 정간 테두리에서 각 변을 어떻게 그릴지.
 *
 * @property sharedTop 위 정간도 테두리를 그린다. 맞닿은 변을 반 두께로 가장자리에 붙여 두 테두리의 합이 한 변 두께가 되게 한다.
 * @property coveredTop 각 바깥 테두리(1dp)가 위 가장자리를 덮는다(각을 여러 줄로 나눈 이어지는 줄의 첫 정간).
 * @property coveredBottom 각 바깥 테두리가 아래 가장자리를 덮는다(줄의 마지막 정간).
 * @property sharedLeft 가로로 나뉜 소박에서 왼쪽 소박도 테두리를 그린다. [sharedRight]도 같다.
 * @property coveredLeft 각 경계선이 왼쪽 가장자리를 덮는다. 정간은 양쪽 모두, 가로로 나뉜 소박은 바깥쪽만이다. [coveredRight]도 같다.
 * @property openLeft 왼쪽 변을 긋지 않고 위아래 변을 가장자리까지 잇는다. 옆 칸에 그린 같은 테두리와 한 상자로 보인다
 *   (소박 테두리를 곁줄까지 이을 때 곁줄 쪽). [openRight]도 같다(소박 쪽).
 */
@Immutable
internal data class RingEdges(
    val sharedTop: Boolean = false,
    val sharedBottom: Boolean = false,
    val coveredTop: Boolean = false,
    val coveredBottom: Boolean = false,
    val sharedLeft: Boolean = false,
    val sharedRight: Boolean = false,
    val coveredLeft: Boolean = true,
    val coveredRight: Boolean = true,
    val openLeft: Boolean = false,
    val openRight: Boolean = false,
)

/**
 * 실시간 판정 바탕색. [fill]이 바뀌면 [durationMillis] 동안 색을 바꾸고, 빈 바탕에서는 투명한 [fill]에서 시작한다.
 * 처음 그릴 때(창 밖에서 들어온 정간 포함)는 전환 없이 지금 색으로 둔다. 표시가 사라지면 바로 지운다.
 */
@Composable
internal fun animatedMarkFill(fill: Color?, durationMillis: Int): State<Color> {
    val color = remember { Animatable(fill ?: Color.Transparent) }
    LaunchedEffect(fill) {
        if (fill == null) {
            color.snapTo(Color.Transparent)
        } else {
            if (color.value.alpha == 0f) color.snapTo(fill.copy(alpha = 0f))
            color.animateTo(fill, tween(durationMillis))
        }
    }
    return color.asState()
}

/** 소박 바탕. [top]만큼 위를 비워 그 자리의 정간 윗선이 보이게 한다. [color]가 없으면 칠하지 않는다. */
private fun Modifier.unitFill(color: Color?, top: Dp): Modifier = when {
    color == null -> this
    top == 0.dp -> background(color)
    else -> drawBehind { drawRect(color, topLeft = Offset(0f, top.toPx()), size = Size(size.width, size.height - top.toPx())) }
}

/** 정간 가장자리에서 안쪽으로 잰 네 변의 길이. 단위(px·dp)는 쓰는 쪽이 정한다. */
@Immutable
internal data class CellInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    operator fun times(scale: Float) = CellInsets(left * scale, top * scale, right * scale, bottom * scale)

    operator fun minus(other: CellInsets) = CellInsets(left - other.left, top - other.top, right - other.right, bottom - other.bottom)

    companion object {
        val None = CellInsets()
    }
}

/**
 * 정간 안쪽 테두리의 바깥 끝이 정간 가장자리에서 들어간 길이. 각 경계선이 덮는 변은 [cover]만큼,
 * 구간 테두리가 있는 변은 그 안쪽([clear], [sectionOutlineReach])까지 들인다.
 */
internal fun cellRingInsets(edges: RingEdges, cover: Float, clear: CellInsets = CellInsets.None): CellInsets = CellInsets(
    left = if (edges.openLeft) 0f else maxOf(if (edges.coveredLeft && !edges.sharedLeft) cover else 0f, clear.left),
    top = maxOf(if (edges.coveredTop) cover else 0f, clear.top),
    right = if (edges.openRight) 0f else maxOf(if (edges.coveredRight && !edges.sharedRight) cover else 0f, clear.right),
    bottom = maxOf(if (edges.coveredBottom && !edges.sharedBottom) cover else 0f, clear.bottom),
)

/**
 * 정간 안쪽 테두리. 각의 왼쪽 경계선(1dp)이 정간 위에 덧그려지므로 왼쪽·오른쪽은 [cover]만큼 안으로 들여 네 변이 같은 두께로 보이게 한다.
 * 위아래는 [RingEdges]에 따라 가장자리에 붙이거나(맞닿은 변은 반 두께) 덮이는 만큼 들인다.
 * 구간 테두리가 있는 변은 [clear]만큼 들여 그 안쪽에 온 두께로 그린다.
 */
private fun DrawScope.drawCellRing(color: Color, width: Float, dash: PathEffect?, edges: RingEdges, cover: Float, clear: CellInsets = CellInsets.None) {
    val inset = cellRingInsets(edges, cover, clear)
    val top = inset.top
    val topWidth = if (edges.sharedTop && clear.top == 0f) width / 2 else width
    val bottom = size.height - inset.bottom
    val bottomWidth = if (edges.sharedBottom && clear.bottom == 0f) width / 2 else width
    val left = inset.left
    val leftWidth = if (edges.sharedLeft && clear.left == 0f) width / 2 else width
    val right = size.width - inset.right
    val rightWidth = if (edges.sharedRight && clear.right == 0f) width / 2 else width
    // 가로 변
    drawLine(color, Offset(left, top + topWidth / 2), Offset(right, top + topWidth / 2), topWidth, pathEffect = dash)
    drawLine(color, Offset(left, bottom - bottomWidth / 2), Offset(right, bottom - bottomWidth / 2), bottomWidth, pathEffect = dash)
    // 세로 변. 열린 변([RingEdges.openLeft]·[RingEdges.openRight])은 옆 칸의 테두리로 이어진다.
    if (!edges.openLeft) drawLine(color, Offset(left + leftWidth / 2, top), Offset(left + leftWidth / 2, bottom), leftWidth, pathEffect = dash)
    if (!edges.openRight) drawLine(color, Offset(right - rightWidth / 2, top), Offset(right - rightWidth / 2, bottom), rightWidth, pathEffect = dash)
}

/**
 * 구간 테두리가 정간 가장자리에서 차지하는 깊이: 칸 선 두께에 테두리 두께 [width]를 더한 값이다.
 * 왼쪽·오른쪽·줄 끝의 각 경계선은 정간을 [cover]만큼 덮고, 위는 정간 윗선([topLine], 대강이면 굵다)이 차지한다.
 * 곁줄 쪽(오른쪽)과 다음 정간 쪽(아래)의 선은 이 정간 밖에 있어 가장자리부터 테두리다.
 * 이어지는 구간 정간 사이([RingEdges.sharedTop]·[RingEdges.sharedBottom])에는 테두리가 없어 0이다.
 */
internal fun sectionOutlineReach(edges: RingEdges, width: Float, cover: Float, topLine: Float): CellInsets = CellInsets(
    left = (if (edges.coveredLeft) cover else 0f) + width,
    top = if (edges.sharedTop) 0f else maxOf(if (edges.coveredTop) cover else 0f, topLine) + width,
    right = (if (edges.coveredRight) cover else 0f) + width,
    bottom = if (edges.sharedBottom) 0f else (if (edges.coveredBottom) cover else 0f) + width,
)

/** 구간 테두리 띠: [outer]에서 [inner]를 뺀 자리를 칠한다. */
@Immutable
internal data class SectionOutlineShape(val outer: Rect, val inner: RoundRect)

/**
 * 크기 [size]인 구간 정간의 테두리 띠. 띠의 바깥 끝은 칸 선에 맞닿고 안쪽 끝은 [sectionOutlineReach]다.
 * - 각 경계선이 덮는 변은 가장자리부터 칠해 경계선 아래로 이어지게 하고, 정간 윗선은 덮지 않고 그 아래부터 칠한다.
 * - 이어지는 구간 정간 쪽은 안쪽을 정간 밖까지 열어 선을 긋지 않는다. 한 줄 안의 구간이 하나의 상자로 보인다.
 * - 각의 둥근 모서리([corner])에 놓인 정간은 안쪽 모서리를 같은 중심으로 둥글려 띠 두께가 모서리에서도 같다.
 */
internal fun sectionOutlineShape(size: Size, edges: RingEdges, width: Float, cover: Float, topLine: Float, corner: Float): SectionOutlineShape {
    val reach = sectionOutlineReach(edges, width, cover, topLine)
    val radius = CornerRadius((corner - cover - width).coerceAtLeast(0f))
    val roundTop = !edges.sharedTop && edges.coveredTop
    val roundBottom = !edges.sharedBottom && edges.coveredBottom
    fun radiusIf(rounded: Boolean) = if (rounded) radius else CornerRadius.Zero
    return SectionOutlineShape(
        outer = Rect(0f, if (edges.sharedTop || edges.coveredTop) 0f else topLine, size.width, size.height),
        inner = RoundRect(
            left = reach.left,
            top = if (edges.sharedTop) -width else reach.top,
            right = size.width - reach.right,
            bottom = if (edges.sharedBottom) size.height + width else size.height - reach.bottom,
            topLeftCornerRadius = radiusIf(roundTop && edges.coveredLeft),
            topRightCornerRadius = radiusIf(roundTop && edges.coveredRight),
            bottomRightCornerRadius = radiusIf(roundBottom && edges.coveredRight),
            bottomLeftCornerRadius = radiusIf(roundBottom && edges.coveredLeft),
        ),
    )
}

/**
 * 곁줄(`.ms-lyr`) 한 칸. 왼쪽 선과 위 선(대강이면 굵게)을 긋는다.
 * 정간 전체의 틀린 바탕·테두리는 부모가 율명 칸과 함께 그리고, 소박마다의 틀린 표시는 [segments](가사 줄마다, 없으면 빈 목록)로 받아 이어 그린다.
 */
@Composable
private fun LyricLane(
    width: Dp,
    lyric: String?,
    units: List<ScoreDisplayUnit>,
    drawTopLine: Boolean,
    lineColor: Color,
    lineWidth: Dp,
    segments: List<LaneSegment?> = emptyList(),
) {
    val colors = EolssuTheme.colors
    val lines = remember(lyric, units) {
        val slots = scoreLyricSlots(lyric, units.size)
        units.withIndex().groupBy { it.value.unitRow }.entries.sortedBy { it.key }.map { (_, row) ->
            row.sortedBy { it.value.unitCol }.joinToString("") { slots[it.index] }
        }
    }
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .drawWithContent {
                val rowHeight = if (lines.isEmpty()) size.height else size.height / lines.size
                segments.forEachIndexed { r, segment ->
                    segment?.mark?.fill?.let { drawRect(it, topLeft = Offset(0f, rowHeight * r), size = Size(size.width, rowHeight)) }
                }
                drawRect(colors.line, size = Size(1.dp.toPx(), size.height))
                if (drawTopLine) drawRect(lineColor, size = Size(size.width, lineWidth.toPx()))
                drawContent()
                segments.forEachIndexed { r, segment ->
                    val ring = segment?.mark?.ring ?: return@forEachIndexed
                    inset(0f, rowHeight * r, 0f, size.height - rowHeight * (r + 1)) {
                        drawCellRing(
                            ring, segment.mark.ringWidth.toPx(),
                            if (segment.mark.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                            segment.edges, LineCover.toPx(), segment.clear * density,
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxSize()) {
            lines.forEach { line ->
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (line.isNotEmpty()) {
                        // 줄인 연습 악보처럼 칸이 글자보다 낮을 때만 칸 높이에 맞춰 줄인다.
                        val base = MaterialTheme.typography.labelSmall
                        val fitted = with(LocalDensity.current) { (maxHeight * 0.8f).toSp() }
                        val size = if (fitted.value < base.fontSize.value) fitted else base.fontSize
                        Text(line, style = base.copy(fontSize = size, lineHeight = size), color = colors.text, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreNoteBox(
    unit: ScoreDisplayUnit,
    splitCount: Int,
    /** 위아래로 나뉜 정간의 한 줄 높이. 나뉘지 않았으면 null. */
    lineHeight: Dp?,
    metrics: JeongganboMetrics,
    look: JeongganboLook,
    showDivider: Boolean,
    isCurrentInSplit: Boolean,
    notation: ScoreNotation,
    mutedGlyph: Boolean,
    onClick: (() -> Unit)?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    sigimsae: List<String> = emptyList(),
    playing: Boolean = false,
    reserveTopEnd: DpSize? = null,
    reserveBottomStart: DpSize? = null,
    markDescription: String? = null,
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
    if (onClick != null) noteModifier = noteModifier.clickable(role = Role.Button, onClick = UserActionLog.click(QaTarget.SCORE_NOTE, onClick))
    noteModifier = noteModifier.semantics(mergeDescendants = true) {
        contentDescription = (listOf(label) + sigimsae.map(::scoreSigimsaeName)).joinToString(" · ")
        markDescription?.let { stateDescription = it }
        if (onClick != null) selected = isSelected
    }

    val yulSize = if (lineHeight == null) metrics.yulSizeFor(splitCount) else metrics.splitYulSize(splitCount, lineHeight, LocalDensity.current.fontScale)
    val auxSize = metrics.auxSizeFor(splitCount)
    val glyphColor = when {
        playing -> colors.rec
        mutedGlyph -> colors.textDisabled
        isPitch -> colors.text
        look.board -> colors.textSub
        else -> colors.textMuted
    }
    FitInCell(noteModifier.padding(horizontal = 2.dp), reserveTopEnd, reserveBottomStart) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                glyphs.forEach { ScoreSigimsaeGlyph(it, yulSize * look.sigimsaeScale) }
            }
        }
        if (hangul != null) {
            val auxStyle = MaterialTheme.typography.labelSmall.copy(fontSize = auxSize, lineHeight = auxSize * 1.1f)
            val auxColor = when {
                playing -> colors.rec
                mutedGlyph -> colors.textDisabled
                else -> colors.textMuted
            }
            Text(hangul, style = auxStyle, color = auxColor, modifier = Modifier.clearAndSetSemantics {})
        }
      }
      sigimsae.filter { scoreSigimsaeImage(it) == null }.forEach {
          Text(scoreSigimsaeName(it), modifier = Modifier.clearAndSetSemantics {},
              fontSize = auxSize, lineHeight = auxSize * 1.1f, color = colors.textMuted)
      }
    }
    }
}

/**
 * 율명 묶음(율명 · 시김새 · 한글 · 계이름)을 자리 가운데에 놓는다. 자리에 들어가면 크기를 그대로 두고,
 * 가로로 나뉜 소박처럼 자리보다 크면 잘리지 않도록 가로세로 비율을 지켜 자리에 맞게 줄인다.
 * [reserveTopEnd](오른쪽 위 틀린 종류 아이콘) · [reserveBottomStart](왼쪽 아래 구간 ‘처음’·‘끝’ 알약)와 겹칠 때만
 * 그 자리를 피해 왼쪽·아래(또는 오른쪽·위)로 가장 조금 비키고, 비킬 자리가 모자라면 남은 자리에 맞게 줄인다.
 * 겹치지 않으면 위치와 크기를 바꾸지 않는다.
 */
@Composable
private fun FitInCell(modifier: Modifier, reserveTopEnd: DpSize?, reserveBottomStart: DpSize?, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints())
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        val fit = fitGlyphInCell(
            box = Size(width.toFloat(), height.toFloat()),
            content = Size(placeable.width.toFloat(), placeable.height.toFloat()),
            topEnd = reserveTopEnd?.let { Size(it.width.toPx(), it.height.toPx()) },
            bottomStart = reserveBottomStart?.let { Size(it.width.toPx(), it.height.toPx()) },
        )
        layout(width, height) {
            // 층의 축소 기준은 가운데이므로 줄어든 만큼 반을 빼서 왼쪽 위를 맞춘다.
            val shrinkX = placeable.width * (1 - fit.scale) / 2
            val shrinkY = placeable.height * (1 - fit.scale) / 2
            placeable.placeWithLayer((fit.topLeft.x - shrinkX).roundToInt(), (fit.topLeft.y - shrinkY).roundToInt()) {
                scaleX = fit.scale
                scaleY = fit.scale
            }
        }
    }
}

/** [fitGlyphInCell] 결과: 줄임 배율(1 이하)과 줄인 내용의 왼쪽 위(px). */
internal data class GlyphFit(val scale: Float, val topLeft: Offset)

/**
 * 크기 [box]인 자리에 [content]를 놓을 배율과 위치. 들어가면 배율 1로 가운데에 두고, 크면 비율을 지켜 줄인다.
 * [topEnd] · [bottomStart]는 오른쪽 위·왼쪽 아래에 비워 둘 크기다. 가운데 자리가 그와 겹칠 때만
 * 각 자리를 옆 또는 위아래로 피한 영역들 가운데 내용을 가장 크게, 같으면 가장 조금 옮겨 둘 수 있는 곳을 고른다.
 */
internal fun fitGlyphInCell(box: Size, content: Size, topEnd: Size? = null, bottomStart: Size? = null): GlyphFit {
    val contentW = content.width.coerceAtLeast(1f)
    val contentH = content.height.coerceAtLeast(1f)
    val full = Area(0f, 0f, box.width, box.height)
    fun scaleIn(a: Area) = minOf(1f, (a.right - a.left) / contentW, (a.bottom - a.top) / contentH).coerceAtLeast(0f)
    // 가운데 자리에서 그 영역 안으로 가장 가깝게 옮긴 왼쪽 위.
    fun placeIn(a: Area, scale: Float): Offset {
        val w = contentW * scale
        val h = contentH * scale
        val x = ((box.width - w) / 2).coerceIn(a.left, maxOf(a.left, a.right - w))
        val y = ((box.height - h) / 2).coerceIn(a.top, maxOf(a.top, a.bottom - h))
        return Offset(x, y)
    }
    fun overlaps(at: Offset, scale: Float): Boolean {
        val right = at.x + contentW * scale
        val bottom = at.y + contentH * scale
        val hitTopEnd = topEnd != null && right > box.width - topEnd.width && at.y < topEnd.height
        val hitBottomStart = bottomStart != null && at.x < bottomStart.width && bottom > box.height - bottomStart.height
        return hitTopEnd || hitBottomStart
    }

    val scale = scaleIn(full)
    val center = placeIn(full, scale)
    if (!overlaps(center, scale)) return GlyphFit(scale, center)
    // 각 표시마다 옆으로 비키거나 위아래로 비키는 두 가지 영역이 있다.
    val topEndAreas = topEnd?.let { listOf(full.copy(right = box.width - it.width), full.copy(top = it.height)) } ?: listOf(full)
    val bottomStartAreas = bottomStart?.let { listOf(full.copy(left = it.width), full.copy(bottom = box.height - it.height)) } ?: listOf(full)
    return topEndAreas
        .flatMap { a -> bottomStartAreas.map { b -> Area(maxOf(a.left, b.left), maxOf(a.top, b.top), minOf(a.right, b.right), minOf(a.bottom, b.bottom)) } }
        .map { area -> scaleIn(area).let { s -> GlyphFit(s, placeIn(area, s)) } }
        .maxWith(compareBy<GlyphFit> { it.scale }.thenByDescending { (it.topLeft - center).getDistance() })
}

/** [fitGlyphInCell]에서 내용을 놓을 수 있는 영역(왼·위·오른·아래 경계, px). */
private data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** 구간 반복에서 앞뒤 각을 흐리게 하는 불투명도(시안 `opacity: .35`). */
private const val FadedColumnAlpha = 0.35f

/** 각 테두리의 모서리 반지름과, 각 경계선(1dp)이 정간 가장자리를 덮는 두께. */
private val ColumnCorner = 6.dp
private val LineCover = 1.dp

/** 구간 정간 테두리 두께. */
private val SectionOutlineWidth = 2.dp

/** 보드 결과 `.ms`의 바깥 테두리(반지름 3, `#8E887D`)와 각 경계선(`#B9B3A6`). */
private val JoinedShape = RoundedCornerShape(3.dp)
private val JoinedOuterLine = Color(0xFF8E887D)
private val JoinedColumnLine = Color(0xFFB9B3A6)

/**
 * 화면에 그릴 음. 정간 전체를 잇는 이음(ㅡ)은 정간보 관행대로 빈칸으로 그린다(S15P31E102-1069).
 * 쪼갠 정간 안의 이음은 자리를 나타내야 하고, 시김새·한배 표시가 붙은 이음은 기호가 놓일 자리라 ㅡ로 남긴다.
 * 소리·채점은 [ScoreDisplayUnit.event]를 그대로 쓰며, 빈칸의 화면 읽기 문구도 이 이벤트로 '이어요'를 읽는다.
 */
internal fun ScoreDisplayUnit.displayedNote(unitsInCell: Int, marks: List<String>): ScoreNote =
    if (note == ScoreNote.Hold && unitsInCell == 1 && marks.isEmpty()) ScoreNote.Blank else note

/** [displayedNote]를 적용한 표시 자리. [enabled]가 false(편집 화면)이면 원본 그대로다. */
internal fun ScoreDisplayUnit.forDisplay(unitsInCell: Int, annotations: Map<ScoreUnitKey, List<String>>, enabled: Boolean): ScoreDisplayUnit {
    if (!enabled) return this
    val shown = displayedNote(unitsInCell, annotations[key].orEmpty())
    return if (shown == note) this else copy(note = shown)
}
