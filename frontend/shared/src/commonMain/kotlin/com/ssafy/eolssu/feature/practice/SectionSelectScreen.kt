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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.TintPillButton
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.ui.score.ErrorKindSegments
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.rememberReadingScoreFormat
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors
import com.ssafy.eolssu.core.ui.score.wholeCellErrors
import com.ssafy.eolssu.feature.practice.component.sectionDecorations
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_score_changed
import eolssu.shared.generated.resources.weak_score_unavailable
import eolssu.shared.generated.resources.weak_select_hint
import eolssu.shared.generated.resources.weak_select_hint_end
import eolssu.shared.generated.resources.weak_select_hint_ready
import eolssu.shared.generated.resources.weak_select_hint_recommended
import eolssu.shared.generated.resources.weak_select_reset
import eolssu.shared.generated.resources.weak_loop_evaluate_start
import eolssu.shared.generated.resources.weak_select_title
import eolssu.shared.generated.resources.weak_select_start
import org.jetbrains.compose.resources.stringResource

/** 취약 구간 선택에서 부르는 동작. */
class SectionSelectActions(
    val onBack: () -> Unit,
    val onTap: (ScorePosition) -> Unit,
    val onReset: () -> Unit,
    val onFilter: (ErrorFilter) -> Unit,
    /** ‘구간 재평가 시작’: 고른 구간·속도로 구간 재평가 화면에 간다. */
    val onEvaluate: () -> Unit,
    /** ‘구간 연습 시작’: 고른 구간·속도로 연습 화면의 구간 연습에 간다. */
    val onStart: () -> Unit = {},
)

/**
 * 구간 고르기(S15P31E102-767 와이어프레임 ①). 연습 기록의 ‘구간 연습’·‘구간 재평가’가 함께 쓴다(S15P31E102-794).
 * 회색 베이스(`groupedBackground`) 위에 앱 바 제목 ‘구간 연습-구간 선택’(구간 재평가와 같은 페이지형 상단 바, S15P31E102-821) ·
 * 5칸 틀린 종류 필터 · 연두 견본과 추천/수동 선택 단계별 안내 · 추천 구간이 있으면 ‘취약 구간 보기’ ·
 * 곡 전체 정간보(틀린 정간은 종류별 색·아이콘, 구간 밖은 흐리게, 구간 각은 연두 테두리, 시작·끝 표시) ·
 * 아래는 나란한 ‘구간 연습 시작’(연습 화면의 구간 연습)과 ‘구간 재평가 시작’. 속도는 각 화면의 설정에서 정한다.
 */
@Composable
fun SectionSelectScreen(state: SectionSelectUiState, actions: SectionSelectActions) {
    val colors = EolssuTheme.colors
    val song = state.song
    val result = state.result
    EolssuScaffold(
        // 다른 화면과 같은 회색 베이스. 틀린 종류 탭 바·정간보 각·아래 버튼은 각자 흰 바탕(`surface`)이나 채움색이라 그대로 구분된다.
        containerColor = colors.groupedBackground,
        topBar = {
            EolssuPageTopBar(title = stringResource(Res.string.weak_select_title), onBack = actions.onBack)
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
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            ErrorKindSegments(result, state.filter, actions.onFilter, Modifier.padding(bottom = 8.dp))
            SelectHintRow(state, actions.onReset)
            val section = state.section
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                metrics = format.metrics,
                look = format.look,
                cellHeights = format.cellHeights,
                onUnitClick = { actions.onTap(ScorePosition(it.column, it.row)) },
                // 나뉜 정간은 정간 전체 대신 틀린 소박만 칠해 '틀린 부분' 개수와 칠한 수를 맞춘다(S15P31E102-913).
                decorations = sectionDecorations(
                    song.score, result.wholeCellErrors(song, state.filter), section, outsideAlpha = OutsideAlpha, edgeRing = true,
                ),
                unitErrors = result.visibleUnitErrors(song, state.filter),
                section = section,
            )
        }
    }
}

/**
 * 연두 견본(연주 결과의 취약 구간 줄과 같은 14dp) · 안내 문구 · 오른쪽 ‘취약 구간 보기’.
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
                SectionSelectStep.Recommended -> Res.string.weak_select_hint_recommended
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

/** 아래 고정 영역: 가로로 나란한 ‘구간 연습 시작’과 ‘구간 재평가 시작’(S15P31E102-794). 속도는 각 화면의 설정에서 정한다. */
@Composable
private fun SectionSettings(state: SectionSelectUiState, actions: SectionSelectActions) {
    if (state.song == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EolssuButton(
            stringResource(Res.string.weak_select_start), actions.onStart, Modifier.weight(1f),
            icon = EolssuIcons.Play, enabled = state.canStart, compact = true,
        )
        EolssuButton(
            stringResource(Res.string.weak_loop_evaluate_start), actions.onEvaluate, Modifier.weight(1f),
            icon = EolssuIcons.Mic, enabled = state.canStart, compact = true, style = EolssuButtonStyle.Secondary,
        )
    }
}

/** 구간 밖 정간 불투명도(시안 `opacity: .5`). */
private const val OutsideAlpha = 0.5f
