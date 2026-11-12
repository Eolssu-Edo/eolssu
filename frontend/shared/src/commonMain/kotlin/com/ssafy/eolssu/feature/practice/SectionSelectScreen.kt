package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuStartTopBar
import com.ssafy.eolssu.core.ui.component.TintPillButton
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.ui.score.ErrorKindSegments
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.rememberReadingScoreFormat
import com.ssafy.eolssu.feature.practice.component.SegmentButton
import com.ssafy.eolssu.feature.practice.component.TempoSlider
import com.ssafy.eolssu.feature.practice.component.sectionDecorations
import com.ssafy.eolssu.feature.practice.component.tempoLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_subtitle
import eolssu.shared.generated.resources.result_weak_section
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_score_changed
import eolssu.shared.generated.resources.weak_score_unavailable
import eolssu.shared.generated.resources.weak_select_bpm
import eolssu.shared.generated.resources.weak_select_endless
import eolssu.shared.generated.resources.weak_select_hint
import eolssu.shared.generated.resources.weak_select_hint_end
import eolssu.shared.generated.resources.weak_select_hint_ready
import eolssu.shared.generated.resources.weak_select_repeat
import eolssu.shared.generated.resources.weak_select_reset
import eolssu.shared.generated.resources.weak_select_speed
import eolssu.shared.generated.resources.weak_loop_evaluate_start
import eolssu.shared.generated.resources.weak_loop_evaluate
import eolssu.shared.generated.resources.weak_select_times
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 취약 구간 선택에서 부르는 동작. */
class SectionSelectActions(
    val onBack: () -> Unit,
    val onTap: (ScorePosition) -> Unit,
    val onReset: () -> Unit,
    val onFilter: (ErrorFilter) -> Unit,
    val onSpeed: (TempoStep) -> Unit,
    /** ‘구간 재평가 시작’: 고른 구간·속도로 구간 재평가 화면에 간다. */
    val onEvaluate: () -> Unit,
)

/**
 * 구간 재평가의 구간 고르기(S15P31E102-767 와이어프레임 ①, S15P31E102-776에서 연습 기록 ‘구간 재평가’ 전용).
 * 연주 결과와 같은 왼쪽 정렬 머리 ‘곡 · 악기 · 난이도 / 구간 재평가’ ·
 * 5칸 틀린 종류 필터 · 연두 견본과 단계별 안내(시작 정간 → 끝 정간 → 연습) · 추천 구간이 있으면 ‘취약 구간으로’ ·
 * 곡 전체 정간보(틀린 정간은 종류별 색·아이콘, 구간 밖은 흐리게, 구간 각은 연두 테두리, 시작·끝 표시) ·
 * 아래 ‘연습 속도 · 약간 느리게 · 58 BPM’과 막대 · ‘구간 재평가 시작’. 구간을 반복해 연습하는 것은 연습 화면의 ‘구간 연습’이 맡는다.
 */
@Composable
fun SectionSelectScreen(state: SectionSelectUiState, actions: SectionSelectActions) {
    val colors = EolssuTheme.colors
    val song = state.song
    val result = state.result
    EolssuScaffold(
        containerColor = colors.surface,
        topBar = {
            EolssuStartTopBar(
                title = song?.let { stringResource(Res.string.result_subtitle, it.title, songInstrumentLabel(it), songLevelLabel(it.level)) }
                    ?: stringResource(Res.string.weak_loop_evaluate),
                onBack = actions.onBack,
                subtitle = stringResource(Res.string.weak_loop_evaluate).takeIf { song != null },
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
        val format = rememberReadingScoreFormat(song)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            ErrorKindSegments(result, state.filter, actions.onFilter, Modifier.padding(top = 2.dp, bottom = 8.dp))
            SelectHintRow(state, actions.onReset)
            val section = state.section
            val kind = state.filter.kind
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                metrics = format.metrics,
                look = format.look,
                cellHeights = format.cellHeights,
                onUnitClick = { actions.onTap(ScorePosition(it.column, it.row)) },
                decorations = sectionDecorations(
                    song.score, result.errors.filter { kind == null || it.kind == kind }, section, outsideAlpha = OutsideAlpha, edgeRing = true,
                ),
                sectionColumns = section?.fullColumns(song.score),
            )
        }
    }
}

/**
 * 연두 견본(연주 결과의 취약 구간 줄과 같은 14dp) · 안내 문구 · 오른쪽 ‘취약 구간으로’.
 * 판본이 바뀌었거나 확인하지 못했으면 안내 대신 그 사유를 보인다.
 */
@Composable
private fun SelectHintRow(state: SectionSelectUiState, onReset: () -> Unit) {
    val colors = EolssuTheme.colors
    val text = stringResource(
        when (state.currentScore) {
            false -> Res.string.weak_score_changed
            null -> Res.string.weak_score_unavailable
            true -> when (state.step) {
                SectionSelectStep.Start -> Res.string.weak_select_hint
                SectionSelectStep.End -> Res.string.weak_select_hint_end
                SectionSelectStep.Ready -> Res.string.weak_select_hint_ready
            }
        },
    )
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).background(colors.accentSoft, RoundedCornerShape(3.dp)).border(2.dp, colors.accent, RoundedCornerShape(3.dp)))
        Text(
            text,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textSub,
        )
        if (state.recommended != null) TintPillButton(stringResource(Res.string.weak_select_reset), onReset)
    }
}

/** 아래 고정 영역: 연습 속도와 막대 · ‘구간 재평가 시작’. */
@Composable
private fun SectionSettings(state: SectionSelectUiState, actions: SectionSelectActions) {
    val song = state.song ?: return
    SpeedSetting(state.speed, song.baseBpm, actions.onSpeed)
    EolssuButton(stringResource(Res.string.weak_loop_evaluate_start), actions.onEvaluate, icon = EolssuIcons.Mic, enabled = state.canStart)
}

/**
 * 연습 속도: ‘연습 속도’와 오른쪽 ‘약간 느리게 · 58 BPM’(단계 이름만 굵게), 그 아래 연습 화면 속도 설정과 같은 막대(단계 점 · 북 손잡이).
 * 화면 읽기 프로그램은 ‘58 BPM · 기준 72 BPM의 80%’를 읽는다.
 */
@Composable
private fun SpeedSetting(speed: TempoStep, baseBpm: Int, onSpeed: (TempoStep) -> Unit) {
    val colors = EolssuTheme.colors
    val bpm = speed.bpm(baseBpm)
    val full = stringResource(Res.string.weak_select_bpm, bpm, baseBpm, (speed.ratio * 100).roundToInt())
    val name = tempoLabel(speed)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.weak_select_speed), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textSub,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.text)) { append(name) }
                    append(" · $bpm BPM")
                },
                modifier = Modifier.semantics { contentDescription = "$name, $full" },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textMuted,
                maxLines = 1,
            )
        }
        TempoSlider(speed, onSpeed)
    }
}

/** 구간 밖 정간 불투명도(시안 `opacity: .5`). */
private const val OutsideAlpha = 0.5f
