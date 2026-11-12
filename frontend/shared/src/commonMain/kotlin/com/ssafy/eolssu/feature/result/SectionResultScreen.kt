package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuStartTopBar
import com.ssafy.eolssu.core.ui.component.ScoreSummaryCard
import com.ssafy.eolssu.core.ui.component.TintPillButton
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.ui.score.ErrorKindSegments
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.rememberReadingScoreFormat
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.core.ui.score.visibleErrorKinds
import com.ssafy.eolssu.core.ui.score.visibleErrors
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.SessionCheck
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_result_compare_failed
import eolssu.shared.generated.resources.weak_result_compare_rejected
import eolssu.shared.generated.resources.weak_result_corrected
import eolssu.shared.generated.resources.weak_result_errors_change
import eolssu.shared.generated.resources.weak_result_errors_now
import eolssu.shared.generated.resources.weak_result_fewer
import eolssu.shared.generated.resources.weak_result_more
import eolssu.shared.generated.resources.weak_result_next_ready
import eolssu.shared.generated.resources.weak_result_next_slow
import eolssu.shared.generated.resources.weak_result_not_comparable
import eolssu.shared.generated.resources.weak_result_other_more
import eolssu.shared.generated.resources.weak_result_same
import eolssu.shared.generated.resources.weak_result_subtitle
import eolssu.shared.generated.resources.weak_result_subtitle_plain
import eolssu.shared.generated.resources.weak_result_title
import eolssu.shared.generated.resources.weak_result_whole
import org.jetbrains.compose.resources.stringResource

/** 구간 재평가 결과에서 부르는 동작. */
class SectionResultActions(
    val onBack: () -> Unit,
    val onOtherSection: () -> Unit,
    val onWholeSong: () -> Unit,
    val onSelectFilter: (ErrorFilter) -> Unit = {},
)

/**
 * RESULT-04 구간 재평가 결과(S15P31E102-776, 와이어프레임 ⑤ · 연주 결과 틀). 왼쪽 정렬 머리 ‘곡 · 2–3각 · 58 BPM / 구간 재평가 결과’ ·
 * 연주 결과와 같은 점수 상자(일원상 구간 점수 · 항목 막대 · ‘이 구간 다시 연습’) · 회색 칸의 틀린 음 비교(EVA-04) ·
 * 5칸 틀린 종류 필터 · 구간 정간보(보기 전용) · 다음 단계 한 줄 · ‘다른 구간 고르기 ›’, 아래 ‘곡 전체 다시 평가’.
 * 점수는 서버 값만 쓰고 화면에서 계산하지 않는다. 구간 재평가는 연습 기록으로 저장하지 않아(S15P31E102-343) 저장 버튼은 두지 않는다.
 */
@Composable
fun SectionResultScreen(state: SectionResultUiState, snackbarHostState: SnackbarHostState, actions: SectionResultActions) {
    val colors = EolssuTheme.colors
    val evaluation = state.evaluation
    val section = state.section
    EolssuScaffold(
        containerColor = colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = {
            EolssuStartTopBar(
                title = evaluation?.let {
                    if (section != null) {
                        stringResource(Res.string.weak_result_subtitle, it.song.title, scoreSectionLabel(section, it.song.score), it.bpm)
                    } else {
                        stringResource(Res.string.weak_result_subtitle_plain, it.song.title, it.bpm)
                    }
                } ?: stringResource(Res.string.weak_result_title),
                onBack = actions.onBack,
                subtitle = stringResource(Res.string.weak_result_title).takeIf { evaluation != null },
            )
        },
        bottomBar = if (evaluation != null) {
            { EolssuButton(stringResource(Res.string.weak_result_whole), actions.onWholeSong) }
        } else {
            null
        },
    ) {
        if (evaluation == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(Res.string.weak_load_missing))
            }
            return@EolssuScaffold
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ScoreSummaryCard(
                total = evaluation.total,
                metrics = evaluation.metrics,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            ErrorCompareBox(evaluation.check, Modifier.padding(bottom = 8.dp))
            val result = evaluation.result
            if (result != null) ErrorKindSegments(result, state.filter, actions.onSelectFilter, Modifier.padding(bottom = 8.dp))
            if (section != null) SectionScore(evaluation, section, state.filter)
            Text(
                if (evaluation.bpm < evaluation.song.baseBpm) {
                    stringResource(Res.string.weak_result_next_slow, evaluation.song.baseBpm, WholeSongReadyScore)
                } else {
                    stringResource(Res.string.weak_result_next_ready)
                },
                modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.textSub,
            )
            Text(
                stringResource(Res.string.weak_result_other_more),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp, bottom = 10.dp)
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = actions.onOtherSection)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textSub,
            )
        }
    }
}

/**
 * 재평가한 구간의 정간보(보기 전용, 연주 결과와 같은 읽기 형식). 틀린 정간은 [filter] 종류만 색·아이콘으로 칠하고, 구간 각은 연두 테두리다.
 * 평가 결과가 없으면(EVA-01을 받지 못함) 틀린 표시 없이 악보만 그린다.
 */
@Composable
private fun SectionScore(evaluation: SectionEvaluation, section: ScoreSection, filter: ErrorFilter) {
    val song = evaluation.song
    val result = evaluation.result
    val format = rememberReadingScoreFormat(song, lyrics = false)
    JeongganboView(
        song = song,
        modifier = Modifier.fillMaxWidth(),
        metrics = format.metrics,
        look = format.look,
        cellHeights = format.cellHeights,
        columns = section.columns,
        errors = result?.visibleErrors(filter).orEmpty(),
        errorKinds = result?.visibleErrorKinds(filter).orEmpty(),
        unitErrors = result?.visibleUnitErrors(song, filter).orEmpty(),
        sectionColumns = section.fullColumns(song.score),
    )
}

/**
 * 회색 칸의 틀린 음 비교(EVA-04): ‘이전 12개 → 이번 5개’와 줄어든 수 알약, 고친 음 수.
 * 견줄 수 없으면(악보 판본·채점 모델·속도가 다름) 서버 사유와 이번 틀린 음 수만, 비교를 받지 못하면 안내만 보인다.
 */
@Composable
private fun ErrorCompareBox(check: CheckLoad, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    Column(
        modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (check) {
            CheckLoad.Rejected -> Text(stringResource(Res.string.weak_result_compare_rejected), style = small, color = colors.textMuted)
            CheckLoad.Failed -> Text(stringResource(Res.string.weak_result_compare_failed), style = small, color = colors.textMuted)
            is CheckLoad.Loaded -> CheckBody(check.check)
        }
    }
}

@Composable
private fun CheckBody(check: SessionCheck) {
    val colors = EolssuTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    val before = check.beforeErrors
    val delta = check.deltaErrors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (check.comparable && before != null) {
                stringResource(Res.string.weak_result_errors_change, before, check.afterErrors)
            } else {
                stringResource(Res.string.weak_result_errors_now, check.afterErrors)
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            color = colors.text,
        )
        if (check.comparable && delta != null) DeltaPill(delta)
    }
    val reason = check.reason
    if (!check.comparable && reason != null) {
        Text(stringResource(Res.string.weak_result_not_comparable, reason), style = small, color = colors.textMuted)
    } else if (check.correctedCount > 0) {
        Text(stringResource(Res.string.weak_result_corrected, check.correctedCount), style = small, color = colors.textMuted)
    }
}

/** 틀린 음 변화 알약. 줄었으면 연두 바탕, 늘었거나 같으면 회색 바탕. */
@Composable
private fun DeltaPill(delta: Int) {
    val colors = EolssuTheme.colors
    val fewer = delta < 0
    Row(
        Modifier.heightIn(min = 24.dp).background(if (fewer) colors.accentTint else colors.surfaceStrong, CircleShape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (fewer) Icon(EolssuIcons.ArrowUpRight, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.accentText)
        Text(
            when {
                delta < 0 -> stringResource(Res.string.weak_result_fewer, -delta)
                delta > 0 -> stringResource(Res.string.weak_result_more, delta)
                else -> stringResource(Res.string.weak_result_same)
            },
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (fewer) colors.accentText else colors.text,
        )
    }
}
