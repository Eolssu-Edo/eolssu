package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.BeatCountdownOverlay
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.PageHeading
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreHighlight
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.practice.component.PairedButtons
import com.ssafy.eolssu.feature.practice.component.PracticeSplitLineFill
import com.ssafy.eolssu.feature.practice.component.PracticeStackGap
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.feature.practice.component.sectionDecorations
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_follow_now
import eolssu.shared.generated.resources.practice_rec_time_detail
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_loop_badge_done
import eolssu.shared.generated.resources.weak_loop_badge_paused
import eolssu.shared.generated.resources.weak_loop_badge_paused_endless
import eolssu.shared.generated.resources.weak_loop_badge_recording
import eolssu.shared.generated.resources.weak_loop_badge_running
import eolssu.shared.generated.resources.weak_loop_badge_running_endless
import eolssu.shared.generated.resources.weak_loop_count_record
import eolssu.shared.generated.resources.weak_loop_count_resume
import eolssu.shared.generated.resources.weak_loop_count_start
import eolssu.shared.generated.resources.weak_loop_evaluate
import eolssu.shared.generated.resources.weak_loop_pause
import eolssu.shared.generated.resources.weak_loop_record_hint
import eolssu.shared.generated.resources.weak_loop_restart
import eolssu.shared.generated.resources.weak_loop_resume
import eolssu.shared.generated.resources.weak_loop_stop_recording
import eolssu.shared.generated.resources.weak_loop_title
import org.jetbrains.compose.resources.stringResource

/**
 * PRACTICE-03 구간 반복·재평가. 기본 평가 화면(PRACTICE-01 휴대폰 배치)과 같은 틀을 쓴다(S15P31E102-614):
 * 페이지 머리 ‘구간 연습’ → 곡 제목과 오른쪽 상태 문구 → 남은 높이에 맞춘 정간보(좌우로만 넘기고 연주 위치를 따라간다) →
 * 아래 고정 진행 바(‘0:03 / 0:08 · 37%’, 6dp 막대, 1 : 1.4 두 버튼).
 * 구간 각만 연두 테두리·크림 머리, 앞뒤 한 각은 흐리게 한다. 3·2·1은 평가 시작처럼 막 위에 세고 ‘시작 취소’를 둔다.
 */
@Composable
fun SectionLoopScreen(
    state: SectionLoopUiState,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onEvaluate: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val song = state.song
    val section = state.section
    Box(Modifier.fillMaxSize()) {
        EolssuScaffold(
            snackbarHostState = snackbarHostState,
            topBar = { EolssuPageTopBar(title = stringResource(Res.string.weak_loop_title), onBack = onBack) },
            bottomBar = if (song != null && section != null) {
                { LoopBottomBar(state, onTogglePause, onEvaluate) }
            } else {
                null
            },
        ) {
            if (song == null || section == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator(color = EolssuTheme.colors.accentText) else Text(stringResource(Res.string.weak_load_missing))
                }
                return@EolssuScaffold
            }
            Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PageHeading(song.title, Modifier.weight(1f))
                    LoopStatusLabel(state, Modifier.widthIn(max = 168.dp))
                }
                Spacer(Modifier.height(8.dp))
                SectionBoardScore(state, song, Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
            }
        }
        val count = state.countdown
        val start = state.current
        if (count != null && start != null) {
            BeatCountdownOverlay(
                count = count,
                message = stringResource(
                    when {
                        state.recording -> Res.string.weak_loop_count_record
                        state.resumed -> Res.string.weak_loop_count_resume
                        else -> Res.string.weak_loop_count_start
                    },
                    CountdownBeats, start.column + 1, start.row + 1,
                ),
                action = {
                    // 반복 3·2·1은 멈춤, 재평가 3·2·1은 녹음 그만두기와 같다.
                    EolssuButton(
                        stringResource(Res.string.practice_countdown_cancel),
                        onTogglePause,
                        style = EolssuButtonStyle.Secondary,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                },
            )
        }
    }
}

/**
 * 기본 평가 화면의 휴대폰 악보([com.ssafy.eolssu.feature.practice.component.PracticeBoardScore])와 같은 정간보:
 * 곡 전체로 정한 묶음 배치·정간 높이를 그대로 쓰고 구간과 앞뒤 각만 그린다. 세로 스크롤 없이 좌우로만 넘기며 연주 위치를 따라가고,
 * 손으로 넘기면 따라가기를 멈추고 ‘현재 위치로’를 보인다. 재평가 녹음 중에는 연주 중과 같은 붉은 현재 표시를 쓴다.
 */
@Composable
private fun SectionBoardScore(state: SectionLoopUiState, song: Song, modifier: Modifier = Modifier) {
    val section = state.section ?: return
    val colors = EolssuTheme.colors
    val big = state.settings.bigScore
    val strips = remember(song) { scoreStrips(song.score, song.jangdan) }
    val longestStrip = strips.maxOf { it.rows.count() }
    val current = state.current
    var follow by rememberSaveable { mutableStateOf(true) }
    // 새 회차나 재평가 녹음이 시작되면 다시 따라간다.
    LaunchedEffect(state.round, state.recording) { follow = true }
    val shown = state.shownColumns
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val packing = remember(song, big, maxHeight, fontScale) {
                val metrics = JeongganboMetrics.practiceBoard(big, longestStrip).copy(splitLineFill = PracticeSplitLineFill)
                practiceScorePacking(song.score, metrics, maxHeight, fontScale, song.jangdan)
            }
            JeongganboView(
                song = song,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                metrics = packing.metrics,
                notation = ScoreNotation(state.settings.showHangul),
                scrollState = rememberScrollState(),
                currentIndex = current?.let { c -> song.timeline.firstOrNull { it.column == c.column && it.row == c.row }?.index },
                highlight = if (state.recording) ScoreHighlight.Recording else ScoreHighlight.Position,
                follow = follow,
                onUserScroll = { follow = false },
                look = JeongganboLook.practice(big),
                columns = shown,
                columnsPerStack = packing.columnsPerStack,
                splitColumns = packing.splitColumns,
                stackGap = PracticeStackGap,
                cellHeights = packing.cellHeights,
                stackRowHeights = packing.stackRowHeights,
                decorations = sectionDecorations(
                    song.score, emptyList(), section, outsideAlpha = OutsideAlpha, edgeRing = false, edgeLabels = false,
                    columns = shown ?: section.columns, markColumns = section.columns, current = current,
                ),
                sectionColumns = section.fullColumns(song.score),
                fadedColumns = state.contextColumns,
            )
        }
        if (!follow && (state.running || state.recording)) {
            // 기본 평가 화면 `.score-follow`와 같은 오른쪽 작은 버튼.
            val shape = RoundedCornerShape(8.dp)
            Text(
                stringResource(Res.string.practice_follow_now),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.line, shape)
                    .clickable(role = Role.Button) { follow = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = colors.accentText,
            )
        }
    }
}

/**
 * 제목 오른쪽 상태 문구(기본 평가 화면 `.status-label`과 같은 7dp 점 · 12sp 글자).
 * 반복 중·멈춤·끝은 연두 점, 재평가 녹음 중은 붉은 점·붉은 글자 600이다.
 */
@Composable
private fun LoopStatusLabel(state: SectionLoopUiState, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val recording = state.recording
    val running = state.running || state.countdown != null
    val total = state.repeats
    val text = when {
        recording -> stringResource(Res.string.weak_loop_badge_recording)
        state.finished && total != null -> stringResource(Res.string.weak_loop_badge_done, total, total)
        total == null -> stringResource(if (running) Res.string.weak_loop_badge_running_endless else Res.string.weak_loop_badge_paused_endless, state.round)
        else -> stringResource(if (running) Res.string.weak_loop_badge_running else Res.string.weak_loop_badge_paused, state.round, total)
    }
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).background(if (recording) colors.rec else colors.accent, CircleShape))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = if (recording) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (recording) colors.recText else colors.textSub,
        )
    }
}

/**
 * 아래 고정 진행 바(기본 평가 화면의 연주 중 하단 `.bottombar`와 같은 모양): 큰 경과 시간 ‘0:03’과 흐린 ‘/ 0:08 · 37%’ · 6dp 진행 막대 · 1 : 1.4 두 버튼.
 * 반복은 회차마다 연두 막대를 나란히 두고 ‘반복 멈춤/계속/다시 연습’ + 붉은 ‘구간 재평가’를, 재평가 녹음은 붉은 막대 하나와 안내,
 * 붉은 ‘녹음 그만하기’만 둔다.
 */
@Composable
private fun ColumnScope.LoopBottomBar(state: SectionLoopUiState, onTogglePause: () -> Unit, onEvaluate: () -> Unit) {
    val colors = EolssuTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            buildAnnotatedString {
                append(formatClock(state.elapsedSeconds))
                append(" ")
                withStyle(SpanStyle(fontSize = 13.sp, color = colors.textMuted, fontWeight = FontWeight.Normal)) {
                    append(stringResource(Res.string.practice_rec_time_detail, formatClock(state.roundSeconds), "${state.roundPercent}%"))
                }
            },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
        )
        if (state.recording) {
            ProgressBar(state.roundProgress.firstOrNull() ?: 0f, colors.rec, Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.roundProgress.forEach { fraction -> ProgressBar(fraction, colors.primary, Modifier.weight(1f)) }
            }
        }
    }
    if (state.recording) {
        Text(
            stringResource(Res.string.weak_loop_record_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.textMuted,
        )
        EolssuButton(
            stringResource(Res.string.weak_loop_stop_recording), onTogglePause, Modifier.fillMaxWidth(),
            compact = true, small = true, style = EolssuButtonStyle.Danger, icon = EolssuIcons.Stop,
        )
        return
    }
    val running = state.running || state.countdown != null
    PairedButtons(
        stacked = false,
        first = { m ->
            EolssuButton(
                stringResource(
                    when {
                        running -> Res.string.weak_loop_pause
                        state.finished -> Res.string.weak_loop_restart
                        else -> Res.string.weak_loop_resume
                    },
                ),
                onTogglePause, m, compact = true, small = true, style = EolssuButtonStyle.Secondary,
                icon = if (running) EolssuIcons.Pause else if (state.finished) EolssuIcons.Restart else EolssuIcons.Play,
            )
        },
        second = { m ->
            EolssuButton(
                stringResource(Res.string.weak_loop_evaluate), onEvaluate, m,
                compact = true, small = true, style = EolssuButtonStyle.Danger, icon = EolssuIcons.Mic,
            )
        },
    )
}

/** 6dp 진행 막대(반지름 3): 옅은 회색 바탕 위에 [fraction]만큼 [color]. */
@Composable
private fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Box(modifier.height(6.dp).background(colors.surfaceStrong, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
    }
}

/** 구간 각 안이지만 구간 밖인 정간 불투명도(시안 `opacity: .5`). */
private const val OutsideAlpha = 0.5f
