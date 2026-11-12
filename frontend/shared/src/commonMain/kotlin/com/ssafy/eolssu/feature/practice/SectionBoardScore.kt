package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.ssafy.eolssu.feature.practice.component.PlayerTrack
import com.ssafy.eolssu.feature.practice.component.PracticeSplitLineFill
import com.ssafy.eolssu.feature.practice.component.PracticeStackGap
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.feature.practice.component.sectionDecorations
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_follow_now
import eolssu.shared.generated.resources.practice_time
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_loop_evaluate_start
import eolssu.shared.generated.resources.weak_loop_title
import org.jetbrains.compose.resources.stringResource

/** 불러오는 중이면 원, 찾지 못했으면 안내. */
@Composable
internal fun SectionLoading(loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(color = EolssuTheme.colors.accentText) else Text(stringResource(Res.string.weak_load_missing))
    }
}


/**
 * 구간 정간보: 연습 화면 악보([com.ssafy.eolssu.feature.practice.component.PracticeBoardScore])와 같은 모양으로 구간과 앞뒤 각만 그린다.
 * 곡 전체가 아니라 보이는 각만으로 묶음 배치·정간 높이를 정하고 큰 악보 크기에서 시작해 남은 높이를 채운다(S15P31E102-776).
 * 세로 스크롤 없이 좌우로만 넘기며 연주 위치를 따라가고, 손으로 넘기면 따라가기를 멈추고 ‘현재 위치로’를 보인다.
 * 재평가 녹음 중에는 연주 중과 같은 붉은 현재 표시를 쓴다. [showCurrent]가 false면 현재 위치를 칠하지 않는다(구간 재평가의 평가 시작 전).
 */
@Composable
internal fun SectionBoardScore(state: SectionLoopUiState, song: Song, modifier: Modifier = Modifier, showCurrent: Boolean = true) {
    val section = state.section ?: return
    val colors = EolssuTheme.colors
    val shown = state.shownColumns ?: section.columns
    // 보이는 각만 담은 악보. 묶음 배치는 보이는 첫 각부터 세므로 화면의 묶음과 같은 순서가 된다.
    val shownScore = remember(song, shown) { song.score.copy(columns = song.score.columns.slice(shown), events = null) }
    val longestStrip = remember(shownScore, song) { scoreStrips(shownScore, song.jangdan).maxOf { it.rows.count() } }
    val current = state.current.takeIf { showCurrent }
    var follow by rememberSaveable { mutableStateOf(true) }
    // 새 회차나 재평가 녹음이 시작되면 다시 따라간다.
    LaunchedEffect(state.round, state.recording) { follow = true }
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val packing = remember(shownScore, maxHeight, fontScale) {
                val metrics = JeongganboMetrics.practiceBoard(big = true, longestStrip).copy(splitLineFill = PracticeSplitLineFill)
                practiceScorePacking(shownScore, metrics, maxHeight, fontScale, song.jangdan)
            }
            val cellHeights = remember(packing, shown) { packing.cellHeights.mapKeys { (cell, _) -> (cell.first + shown.first) to cell.second } }
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
                look = JeongganboLook.practice(big = true),
                columns = shown,
                columnsPerStack = packing.columnsPerStack,
                splitColumns = packing.splitColumns,
                stackGap = PracticeStackGap,
                cellHeights = cellHeights,
                stackRowHeights = packing.stackRowHeights,
                decorations = sectionDecorations(
                    song.score, emptyList(), section, outsideAlpha = OutsideAlpha, edgeRing = false, edgeLabels = false,
                    columns = shown, markColumns = section.columns, current = current,
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

/** 구간 각 안이지만 구간 밖인 정간 불투명도(시안 `opacity: .5`). */
private const val OutsideAlpha = 0.5f
