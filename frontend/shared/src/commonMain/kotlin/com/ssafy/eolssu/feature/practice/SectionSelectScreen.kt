package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.practice.component.TempoSlider
import com.ssafy.eolssu.feature.practice.component.tempoLabel
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuSwitch
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.core.ui.score.ErrorKindLegend
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.scoreSectionCellsLabel
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.practice.component.PillChip
import com.ssafy.eolssu.feature.practice.component.SegmentButton
import com.ssafy.eolssu.feature.practice.component.sectionDecorations
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_subtitle
import eolssu.shared.generated.resources.weak_legend_wrong
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_select_bpm
import eolssu.shared.generated.resources.weak_select_endless
import eolssu.shared.generated.resources.weak_select_hint
import eolssu.shared.generated.resources.weak_select_hint_end
import eolssu.shared.generated.resources.weak_select_loop
import eolssu.shared.generated.resources.weak_select_preset
import eolssu.shared.generated.resources.weak_select_speed
import eolssu.shared.generated.resources.weak_select_start
import eolssu.shared.generated.resources.weak_select_times
import eolssu.shared.generated.resources.weak_select_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 취약 구간 선택에서 부르는 동작. */
class SectionSelectActions(
    val onBack: () -> Unit,
    val onTap: (ScorePosition) -> Unit,
    val onRecommended: () -> Unit,
    val onToggleLoop: () -> Unit,
    val onRepeat: (SectionRepeat) -> Unit,
    val onSpeed: (TempoStep) -> Unit,
    val onStart: () -> Unit,
)

/**
 * PRACTICE-02 취약 구간 선택(2차 `SectionSelect`): 추천 구간 칩 · 틀린 종류 범례(항상 보임) · 안내 ·
 * 곡 전체 정간보(연습 화면 부품, 틀린 정간은 종류별 색·아이콘, 구간 밖은 흐리게, 구간 각은 연두 테두리, 처음·끝 표시) ·
 * 고른 구간 · 구간 반복(3회·5회·계속) · 연습 속도 5단계 · ‘구간 연습 시작’.
 */
@Composable
fun SectionSelectScreen(state: SectionSelectUiState, actions: SectionSelectActions) {
    val colors = EolssuTheme.colors
    val song = state.song
    val result = state.result
    EolssuScaffold(
        containerColor = colors.surface,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.weak_select_title),
                onBack = actions.onBack,
                subtitle = song?.let { stringResource(Res.string.result_subtitle, it.title, songInstrumentLabel(it), songLevelLabel(it.level)) },
            )
        },
        bottomBar = if (song != null && result != null) {
            { SectionSettings(state, actions) }
        } else {
            null
        },
    ) {
        if (song == null || result == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(Res.string.weak_load_missing))
            }
            return@EolssuScaffold
        }
        val longestStrip = remember(song) { scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 1 }
        val recommended = state.recommended
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (recommended != null) {
                Row(Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip(
                        stringResource(Res.string.weak_select_preset, scoreSectionLabel(recommended, song.score)),
                        selected = state.section == recommended && !state.picking,
                        onClick = actions.onRecommended,
                    )
                }
            }
            ErrorKindLegend(Modifier.fillMaxWidth().padding(vertical = 4.dp), title = stringResource(Res.string.weak_legend_wrong), boxed = true)
            Text(
                stringResource(if (state.picking) Res.string.weak_select_hint_end else Res.string.weak_select_hint),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            val section = state.section
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                metrics = JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip),
                look = JeongganboLook.practice(big = false),
                onUnitClick = { actions.onTap(ScorePosition(it.column, it.row)) },
                decorations = sectionDecorations(song.score, result.errors, section, outsideAlpha = OutsideAlpha, edgeRing = true),
                sectionColumns = section?.fullColumns(song.score),
            )
        }
    }
}

/** 아래 고정 영역: 고른 구간 · 구간 반복 칸 · 연습 속도 칸 · 시작 버튼. */
@Composable
private fun SectionSettings(state: SectionSelectUiState, actions: SectionSelectActions) {
    val colors = EolssuTheme.colors
    val song = state.song ?: return
    val section = state.section
    if (section != null) {
        Text(scoreSectionCellsLabel(section), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val shape = RoundedCornerShape(12.dp)
        val loopLabel = stringResource(Res.string.weak_select_loop)
        Column(Modifier.weight(1f).border(1.dp, colors.lineSoft, shape).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(loopLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                EolssuSwitch(state.loop, actions.onToggleLoop, loopLabel, width = 40.dp, height = 24.dp, offColor = colors.surfaceStrong)
            }
            Row(Modifier.alpha(if (state.loop) 1f else 0.4f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionRepeat.entries.forEach { repeat ->
                    SegmentButton(
                        text = repeat.count?.let { stringResource(Res.string.weak_select_times, it) } ?: stringResource(Res.string.weak_select_endless),
                        selected = repeat == state.repeat,
                        enabled = state.loop,
                        onClick = { actions.onRepeat(repeat) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).border(1.dp, colors.lineSoft, shape).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val speedLabel = tempoLabel(state.speed)
            val speedTitle = stringResource(Res.string.weak_select_speed)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(speedTitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Text(speedLabel, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            // 연습 화면 속도 설정과 같은 슬라이더(단계 점 · 삼태극 북 손잡이).
            TempoSlider(state.speed, actions.onSpeed)
            Text(
                stringResource(Res.string.weak_select_bpm, state.speed.bpm(song.baseBpm), song.baseBpm, (state.speed.ratio * 100).roundToInt()),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textMuted,
            )
        }
    }
    EolssuButton(stringResource(Res.string.weak_select_start), actions.onStart, icon = EolssuIcons.Restart, enabled = state.canStart)
}

/** 구간 밖 정간 불투명도(시안 `opacity: .5`). */
private const val OutsideAlpha = 0.5f
