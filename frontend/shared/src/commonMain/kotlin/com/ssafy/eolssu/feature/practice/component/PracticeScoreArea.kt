package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.score.BandGap
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.ui.score.ScoreCellMark
import com.ssafy.eolssu.core.ui.score.ScoreCursor
import com.ssafy.eolssu.core.ui.score.ScoreHighlight
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.core.ui.score.ScorePlayback
import com.ssafy.eolssu.core.ui.score.rememberScoreCursor
import com.ssafy.eolssu.core.ui.score.twoBandColumnCount
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.practice.LivePitchMark
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.stepCurrentIndex
import com.ssafy.eolssu.feature.practice.modelPointAt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_follow_now
import eolssu.shared.generated.resources.practice_score_hint
import org.jetbrains.compose.resources.stringResource

/** 연습 악보를 놓는 배치. 정간 크기·여백과 두 단 나누기가 달라진다. */
enum class PracticeScoreStyle {
    /** 휴대폰: 한 단, 가로 스크롤. */
    Phone,

    /** 태블릿 세로: 각 72dp. 가장 긴 줄이 두 단에 들어가면 위·아래 두 단으로 나눈다. */
    TabletPortrait,

    /** 태블릿 가로: 각 72dp, 한 단, 가로 스크롤. 오른쪽 상태 패널 옆에 둔다. */
    TabletLandscape,
}

/**
 * PRACTICE-01의 악보 영역. 한 줄의 정간이 세로 스크롤 없이 들어가도록 정간 높이를 맞춘다.
 * 준비 상태에서는 음을 눌러 운지법을 보고, 모범 연주·연주 중에는 현재 위치를 따라간다.
 */
@Composable
fun PracticeScoreArea(
    state: PracticeUiState,
    style: PracticeScoreStyle,
    onSelectUnit: (ScoreDisplayUnit) -> Unit,
    onUserScroll: () -> Unit,
    onFollowNow: () -> Unit,
    modifier: Modifier = Modifier,
    livePitch: Map<ScoreUnitKey, LivePitchMark> = emptyMap(),
) {
    val song = state.song ?: return
    val pitchMarks = livePitchCellMarks(livePitch)
    val padding = when (style) {
        PracticeScoreStyle.Phone -> PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
        PracticeScoreStyle.TabletPortrait -> PaddingValues(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 12.dp)
        PracticeScoreStyle.TabletLandscape -> PaddingValues(0.dp)
    }
    val tablet = style != PracticeScoreStyle.Phone
    val big = state.settings.bigScore
    val strips = remember(song) { scoreStrips(song.score, song.jangdan) }
    val longestStrip = strips.maxOf { it.rows.count() }
    val stripsPerColumn = strips.groupBy { it.column }.maxOf { it.value.size }
    BoxWithConstraints(modifier.fillMaxSize().padding(padding)) {
        // 확대 글꼴에서는 고정 높이 두 단 대신 가로·세로 스크롤로 모든 정간을 열어 둔다.
        val bandColumns = if (style == PracticeScoreStyle.TabletPortrait && LocalDensity.current.fontScale <= 1f) {
            twoBandColumnCount(
                columnCount = song.score.columns.size,
                stripsPerColumn = stripsPerColumn,
                longestStrip = longestStrip,
                availableWidth = maxWidth,
                availableHeight = maxHeight,
                metrics = JeongganboMetrics.practice(song.jangdan, maxHeight, big, tablet = true, longestStrip = longestStrip),
                minCellHeight = JeongganboMetrics.practiceMinCellHeight(big),
            )
        } else {
            null
        }
        val recording = state.mode == PracticeMode.Recording || state.resuming
        val playback = state.scorePlayback()
        val notation = ScoreNotation(state.settings.showHangul, state.settings.showSolfege)
        // 한 음씩 따라 불기는 진행 모습(playback) 없이 지금 음의 정간에만 연두 테두리를 둔다(곁줄에는 표시하지 않는다).
        val currentIndex = if (state.live) state.position.coerceAtMost(state.eventCount - 1) else state.stepCurrentIndex
        val highlight = if (recording) ScoreHighlight.Recording else ScoreHighlight.Position
        val selectedCell = state.scoreSelection?.let { it.column to it.row }
        val onUnitClick = onSelectUnit.takeIf { state.canSelectUnit }
        val annotations = state.scoreAnnotations
        if (bandColumns == null) {
            val scrollState = rememberHorizontalScoreScroll(state)
            JeongganboView(
                song = song,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                metrics = JeongganboMetrics.practice(song.jangdan, maxHeight, big = big, tablet = tablet, longestStrip = longestStrip),
                notation = notation,
                scrollState = scrollState,
                currentIndex = currentIndex,
                highlight = highlight,
                playback = playback,
                follow = state.follow,
                onUserScroll = onUserScroll,
                selectedCell = selectedCell,
                onUnitClick = onUnitClick,
                annotations = annotations,
                unitMarks = pitchMarks,
            )
        } else {
            val bandHeight = (maxHeight - BandGap) / 2
            val metrics = JeongganboMetrics.practice(song.jangdan, bandHeight, big = big, tablet = true, longestStrip = longestStrip)
            val columnCount = song.score.columns.size
            val scrollState = rememberBandScroll(state, playback, bandColumns, bandHeight, onUserScroll)
            Column(Modifier.fillMaxSize().verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(BandGap)) {
                for (first in 0 until columnCount step bandColumns) {
                    JeongganboView(
                        song = song,
                        modifier = Modifier.fillMaxWidth().height(bandHeight),
                        metrics = metrics,
                        notation = notation,
                        scrollState = null,
                        currentIndex = currentIndex,
                        highlight = highlight,
                        playback = playback,
                        selectedCell = selectedCell,
                        onUnitClick = onUnitClick,
                        columns = first until minOf(first + bandColumns, columnCount),
                        annotations = annotations,
                        unitMarks = pitchMarks,
                    )
                }
            }
        }
        if (state.live && !state.follow) {
            EolssuButton(
                stringResource(Res.string.practice_follow_now),
                onFollowNow,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 14.dp).shadow(8.dp, MaterialTheme.shapes.extraLarge),
                style = EolssuButtonStyle.Secondary,
                icon = EolssuIcons.Restart,
                small = true,
            )
        }
    }
}

/**
 * 모범 연주·연주 중 진행 선이 그릴 위치. 모범 연주는 음원 위치를, 연주는 현재 음이 시작된 시각을 기준으로 매 프레임 계산한다.
 * 모범 연주는 음이 바뀌는 순간도 화면이 직접 계산하므로 음원 위치 보고를 기다리지 않는다.
 */
internal fun PracticeUiState.scorePlayback(): ScorePlayback? {
    val song = song
    if (!live || song == null || eventCount == 0) return null
    val clock = modelClock?.takeIf { mode == PracticeMode.Model && playing }
    if (clock != null) {
        return ScorePlayback(running = true) {
            val point = song.modelPointAt(clock.audio, clock.anchor.positionNow())
            ScoreCursor(point.position, point.fraction.toFloat())
        }
    }
    val index = position.coerceAtMost(eventCount - 1)
    val started = eventStartedAt
    if (started == null) {
        val paused = ScoreCursor(index, pausedFraction)
        return ScorePlayback(running = false) { paused }
    }
    val durationMs = eventDurationMs()
    return ScorePlayback(running = true) {
        ScoreCursor(index, (started.elapsedNow().inWholeMicroseconds / 1000.0 / durationMs).toFloat().coerceIn(0f, 1f))
    }
}

/** 처음 들어오거나 크기를 바꾸면 시작 위치(오른쪽 끝)로, 카운트다운이 시작되면 처음으로 되돌린다. */
@Composable
private fun rememberHorizontalScoreScroll(state: PracticeUiState): ScrollState {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.settings.bigScore) { scrollState.scrollTo(0) }
    LaunchedEffect(state.mode == PracticeMode.Countdown && !state.resume) {
        if (state.mode == PracticeMode.Countdown && !state.resume) scrollState.animateScrollTo(0)
    }
    return scrollState
}

/**
 * 두 단 악보의 세로 스크롤. 현재 위치가 든 단이 화면 밖이면 그 단으로 옮기고, 사용자가 끌면 따라가기를 멈추도록 알린다.
 * 카운트다운이 시작되면 첫 단으로 되돌린다.
 */
@Composable
private fun rememberBandScroll(
    state: PracticeUiState,
    playback: ScorePlayback?,
    bandColumns: Int,
    bandHeight: Dp,
    onUserScroll: () -> Unit,
): ScrollState {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val latestOnUserScroll by rememberUpdatedState(onUserScroll)
    LaunchedEffect(scrollState) {
        scrollState.interactionSource.interactions.collect { if (it is DragInteraction.Start) latestOnUserScroll() }
    }
    LaunchedEffect(state.mode == PracticeMode.Countdown && !state.resume) {
        if (state.mode == PracticeMode.Countdown && !state.resume) scrollState.animateScrollTo(0)
    }
    val song = state.song
    val live = playback != null
    val cursor = rememberScoreCursor(playback)
    val currentBand by remember(song, live, bandColumns) {
        derivedStateOf {
            val index = cursor.index
            if (!live || song == null || index == null) null else song.displayUnitAt(index, cursor.fraction)?.column?.div(bandColumns)
        }
    }
    LaunchedEffect(currentBand, state.follow, scrollState.viewportSize, bandHeight) {
        val band = currentBand
        if (!state.follow || band == null || scrollState.viewportSize == 0) return@LaunchedEffect
        val top = with(density) { ((bandHeight + BandGap) * band).toPx() }
        val height = with(density) { bandHeight.toPx() }
        val visible = top >= scrollState.value && top + height <= scrollState.value + scrollState.viewportSize
        if (!visible) scrollState.animateScrollTo(top.toInt().coerceIn(0, scrollState.maxValue))
    }
    return scrollState
}

/**
 * 휴대폰 보드의 악보 영역. 각의 실제 높이로 위→아래 들어가는 수를 정하고 다음 묶음은 왼쪽에 둔다.
 * 3줄 이상의 소박 표기 정간은 높이를 늘린다. 공간이 부족하면 정간과 글자를 함께 줄여 세로 스크롤 없이 좌우로만 넘긴다.
 * 아래에는 준비 상태 안내(‘율명을 누르면 운지법을 볼 수 있어요 · 오른쪽 각부터 읽어요’)와, 따라가기를 끈 뒤의 ‘현재 위치로’ 버튼이 온다.
 */
@Composable
fun PracticeBoardScore(
    state: PracticeUiState,
    onSelectUnit: (ScoreDisplayUnit) -> Unit,
    onUserScroll: () -> Unit,
    onFollowNow: () -> Unit,
    modifier: Modifier = Modifier,
    livePitch: Map<ScoreUnitKey, LivePitchMark> = emptyMap(),
) {
    val song = state.song ?: return
    val colors = EolssuTheme.colors
    val pitchMarks = livePitchCellMarks(livePitch)
    val big = state.settings.bigScore
    val strips = remember(song) { scoreStrips(song.score, song.jangdan) }
    val longestStrip = strips.maxOf { it.rows.count() }
    val recording = state.mode == PracticeMode.Recording || state.resuming
    val playback = state.scorePlayback()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bounded = constraints.hasBoundedHeight
        val fontScale = LocalDensity.current.fontScale
        Column(if (bounded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            BoxWithConstraints(if (bounded) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()) {
                val packing = remember(song, big, maxHeight, fontScale) {
                    // 휴대폰 연습 악보만 나뉜 정간의 율명을 줄 높이에 맞춰 키운다. 결과·비교·편집 화면의 같은 메트릭은 그대로다.
                    val metrics = JeongganboMetrics.practiceBoard(big, longestStrip).copy(splitLineFill = PracticeSplitLineFill)
                    practiceScorePacking(song.score, metrics, maxHeight, fontScale, song.jangdan)
                }
                JeongganboView(
                    song = song,
                    // 연주 중 손으로 스크롤하기 어려우므로 휴대폰 악보는 좌우로만 넘긴다. 묶음 배치가 정간을 남은 높이에 맞춘다.
                    modifier = Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight().clipToBounds() else Modifier),
                    metrics = packing.metrics,
                    notation = ScoreNotation(state.settings.showHangul, state.settings.showSolfege),
                    scrollState = rememberHorizontalScoreScroll(state),
                    currentIndex = if (state.live) state.position.coerceAtMost(state.eventCount - 1) else state.stepCurrentIndex,
                    highlight = if (recording) ScoreHighlight.Recording else ScoreHighlight.Position,
                    playback = playback,
                    follow = state.follow,
                    onUserScroll = onUserScroll,
                    selectedCell = state.scoreSelection?.let { it.column to it.row },
                    onUnitClick = onSelectUnit.takeIf { state.canSelectUnit },
                    look = JeongganboLook.practice(big),
                    annotations = state.scoreAnnotations,
                    columnsPerStack = packing.columnsPerStack,
                    splitColumns = packing.splitColumns,
                    stackGap = PracticeStackGap,
                    cellHeights = packing.cellHeights,
                    stackRowHeights = packing.stackRowHeights,
                    unitMarks = pitchMarks,
                )
            }
            // 준비 안내의 공간을 재생 중에도 유지해 악보 높이/묶음 수가 변하지 않게 한다.
            Text(
                stringResource(Res.string.practice_score_hint),
                modifier = Modifier.padding(top = 8.dp).alpha(if (state.mode == PracticeMode.Ready) 1f else 0f)
                    .then(if (state.mode == PracticeMode.Ready) Modifier else Modifier.clearAndSetSemantics {}),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.7.sp),
                color = colors.textMuted,
            )
            if (state.live && !state.follow) {
                // 보드 `.score-follow`: 오른쪽 정렬 작은 버튼.
                val shape = RoundedCornerShape(8.dp)
                Text(
                    stringResource(Res.string.practice_follow_now),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .background(colors.surface, shape)
                        .border(1.dp, colors.line, shape)
                        .clickable(role = Role.Button, onClick = onFollowNow)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = colors.accentText,
                )
            }
        }
    }
}

/**
 * 평가 연주에서 음마다 모은 실시간 음높이 안내를 그 음의 표시 자리에 칠한다(S15P31E102-597).
 * 맞음은 연두, 다른 음은 주황, 판단 보류는 회색 테두리만 둔다. 연주 후 채점의 틀린 칸 표시와 달리 아이콘·문구는 없다.
 */
@Composable
private fun livePitchCellMarks(marks: Map<ScoreUnitKey, LivePitchMark>): Map<ScoreUnitKey, ScoreCellMark> {
    if (marks.isEmpty()) return emptyMap()
    val colors = EolssuTheme.colors
    return marks.mapValues { (_, mark) ->
        when (mark) {
            LivePitchMark.Correct -> ScoreCellMark(fill = colors.accentTintStrong, ring = colors.green, ringWidth = 1.5.dp)
            LivePitchMark.Wrong -> ScoreCellMark(fill = colors.pitchErrorSoft, ring = colors.pitchError, ringWidth = 1.5.dp)
            LivePitchMark.Listening -> ScoreCellMark(ring = colors.textDisabled, ringWidth = 1.5.dp)
        }
    }
}
