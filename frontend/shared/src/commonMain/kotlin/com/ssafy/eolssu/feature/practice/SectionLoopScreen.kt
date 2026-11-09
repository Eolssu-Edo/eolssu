package com.ssafy.eolssu.feature.practice

import eolssu.shared.generated.resources.weak_loop_stop_recording
import eolssu.shared.generated.resources.weak_loop_record_progress
import eolssu.shared.generated.resources.weak_loop_record_hint
import eolssu.shared.generated.resources.weak_loop_count_record
import eolssu.shared.generated.resources.weak_loop_badge_recording
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.BeatCountdownOverlay
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import com.ssafy.eolssu.core.ui.score.ErrorKindLegend
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.rememberReadingScoreFormat
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.feature.practice.component.sectionDecorations
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_legend_last
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_loop_badge_done
import eolssu.shared.generated.resources.weak_loop_badge_paused
import eolssu.shared.generated.resources.weak_loop_badge_paused_endless
import eolssu.shared.generated.resources.weak_loop_badge_running
import eolssu.shared.generated.resources.weak_loop_badge_running_endless
import eolssu.shared.generated.resources.weak_loop_count_resume
import eolssu.shared.generated.resources.weak_loop_count_start
import eolssu.shared.generated.resources.weak_loop_evaluate
import eolssu.shared.generated.resources.weak_loop_hint
import eolssu.shared.generated.resources.weak_loop_no_score
import eolssu.shared.generated.resources.weak_loop_pause
import eolssu.shared.generated.resources.weak_loop_progress
import eolssu.shared.generated.resources.weak_loop_restart
import eolssu.shared.generated.resources.weak_loop_resume
import eolssu.shared.generated.resources.weak_loop_subtitle
import org.jetbrains.compose.resources.stringResource

/**
 * PRACTICE-03 구간 반복(2차 `SectionLoop` · `SectionLoop-Resume`). 정간보는 연습 화면과 같은 부품(정간 44dp · 율명 21sp · 각 78dp, 곁줄=가사 칸)이고,
 * 구간 각만 연두 테두리·크림 머리, 앞뒤 한 각은 흐리게 한다(처음·끝 표시는 없다). 지난번 틀린 정간은 종류별 색·아이콘이다.
 * 멈췄다가 ‘반복 계속’을 누르면 3·2·1(1초마다)을 센 뒤 이어 간다.
 */
@Composable
fun SectionLoopScreen(
    state: SectionLoopUiState,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onEvaluate: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = EolssuTheme.colors
    val song = state.song
    val section = state.section
    Box(Modifier.fillMaxSize()) {
        EolssuScaffold(
            containerColor = colors.surface,
            snackbarHostState = snackbarHostState,
            topBar = {
                EolssuTopBar(
                    title = song?.title.orEmpty(),
                    onBack = onBack,
                    subtitle = if (song != null && section != null) {
                        stringResource(Res.string.weak_loop_subtitle, songInstrumentLabel(song), scoreSectionLabel(section, song.score))
                    } else {
                        null
                    },
                    below = if (song != null) {
                        { LoopBadge(state) }
                    } else {
                        null
                    },
                )
            },
            bottomBar = if (song != null) {
                { LoopControls(state, onTogglePause, onEvaluate) }
            } else {
                null
            },
        ) {
            if (song == null || section == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator() else Text(stringResource(Res.string.weak_load_missing))
                }
                return@EolssuScaffold
            }
            // 칸 크기는 다른 화면과 같은 공통 형식을 쓰고, 연주 중 표시(‘← 다음’)가 있는 연습 모양은 그대로 둔다.
            val format = rememberReadingScoreFormat(song)
            val current = state.current
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ErrorKindLegend(Modifier.fillMaxWidth(), title = stringResource(Res.string.weak_legend_last), boxed = true, dense = true)
                Text(stringResource(Res.string.weak_loop_hint), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
                val shown = state.shownColumns
                JeongganboView(
                    song = song,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    metrics = format.metrics,
                    cellHeights = format.cellHeights,
                    look = JeongganboLook.practice(big = false),
                    currentIndex = current?.let { c -> song.timeline.firstOrNull { it.column == c.column && it.row == c.row }?.index },
                    follow = true,
                    columns = shown,
                    decorations = sectionDecorations(
                        song.score, state.errors, section, outsideAlpha = OutsideAlpha, edgeRing = false, edgeLabels = false,
                        columns = shown ?: section.columns, markColumns = section.columns, current = current,
                    ),
                    sectionColumns = section.fullColumns(song.score),
                    fadedColumns = state.contextColumns,
                )
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
            )
        }
    }
}

/** 제목 아래 알약: 반복 중이면 연두, 멈추면 흰 바탕 · 옅은 테두리. */
@Composable
private fun LoopBadge(state: SectionLoopUiState) {
    val colors = EolssuTheme.colors
    if (state.recording) {
        RecordingBadge()
        return
    }
    val running = state.running || state.countdown != null
    val total = state.repeats
    val text = when {
        state.finished && total != null -> stringResource(Res.string.weak_loop_badge_done, total, total)
        total == null -> stringResource(if (running) Res.string.weak_loop_badge_running_endless else Res.string.weak_loop_badge_paused_endless, state.round)
        else -> stringResource(if (running) Res.string.weak_loop_badge_running else Res.string.weak_loop_badge_paused, state.round, total)
    }
    Row(
        Modifier.heightIn(min = 22.dp)
            .background(if (running) colors.primary else colors.surface, CircleShape)
            .then(if (running) Modifier else Modifier.border(1.dp, colors.lineSoft, CircleShape))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (running) EolssuIcons.Restart else EolssuIcons.Pause, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.text)
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
    }
}

/** 재평가 녹음 중 알약: 옅은 붉은 바탕에 붉은 점과 글자(연습 화면 ‘녹음 중’과 같은 색). */
@Composable
private fun RecordingBadge() {
    val colors = EolssuTheme.colors
    Row(
        Modifier.heightIn(min = 22.dp).background(colors.recSoft, CircleShape).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(colors.rec, CircleShape))
        Text(
            stringResource(Res.string.weak_loop_badge_recording),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = colors.recText,
        )
    }
}

/** 아래 고정 영역: 회차·시간 · 회차 막대 · ‘반복 멈춤/계속/다시 연습’ · 붉은 ‘구간 재평가’. 재평가 녹음 중에는 진행과 ‘녹음 그만하기’만 둔다. */
@Composable
private fun LoopControls(state: SectionLoopUiState, onTogglePause: () -> Unit, onEvaluate: () -> Unit) {
    val colors = EolssuTheme.colors
    if (state.recording) {
        RecordingControls(state, onTogglePause)
        return
    }
    // 시안: 왼쪽 굵은 회차·시간, 오른쪽 흐린 안내(space-between). 가운데 빈칸이 남는 폭을 채우고, 좁으면 안내가 아랫줄 오른쪽으로 내려간다.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.weak_loop_progress, state.round, formatClock(state.elapsedSeconds), formatClock(state.roundSeconds)),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.weight(1f))
        Text(stringResource(Res.string.weak_loop_no_score), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        state.roundProgress.forEach { fraction ->
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceStrong)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(colors.primary))
            }
        }
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val running = state.running || state.countdown != null
        EolssuButton(
            stringResource(
                when {
                    running -> Res.string.weak_loop_pause
                    state.finished -> Res.string.weak_loop_restart
                    else -> Res.string.weak_loop_resume
                },
            ),
            onTogglePause,
            Modifier.weight(1f).fillMaxHeight(),
            style = EolssuButtonStyle.Secondary,
            icon = if (running) EolssuIcons.Pause else EolssuIcons.Play,
            compact = true,
        )
        EolssuButton(
            stringResource(Res.string.weak_loop_evaluate), onEvaluate, Modifier.weight(1.4f).fillMaxHeight(),
            style = EolssuButtonStyle.Danger, icon = EolssuIcons.Mic, compact = true,
        )
    }
}

@Composable
private fun RecordingControls(state: SectionLoopUiState, onStop: () -> Unit) {
    val colors = EolssuTheme.colors
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.weak_loop_record_progress, formatClock(state.elapsedSeconds), formatClock(state.roundSeconds)),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.weight(1f))
        Text(stringResource(Res.string.weak_loop_record_hint), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
    }
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceStrong)) {
        Box(Modifier.fillMaxWidth(state.roundProgress.firstOrNull()?.coerceIn(0f, 1f) ?: 0f).fillMaxHeight().background(colors.rec))
    }
    EolssuButton(stringResource(Res.string.weak_loop_stop_recording), onStop, style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Stop, compact = true)
}

/** 구간 각 안이지만 구간 밖인 정간 불투명도(시안 `opacity: .5`). */
private const val OutsideAlpha = 0.5f
