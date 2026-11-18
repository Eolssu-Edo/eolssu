package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuStartTopBar
import com.ssafy.eolssu.core.ui.component.EolssuTextLink
import com.ssafy.eolssu.core.ui.component.ScoreSummaryCard
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
import com.ssafy.eolssu.feature.result.layout.ResultButtonRow
import com.ssafy.eolssu.feature.result.layout.ResultExpandedPanes
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_home
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_result_compare_failed
import eolssu.shared.generated.resources.weak_result_compare_rejected
import eolssu.shared.generated.resources.weak_result_corrected
import eolssu.shared.generated.resources.weak_result_errors_change
import eolssu.shared.generated.resources.weak_result_errors_now
import eolssu.shared.generated.resources.weak_result_fewer
import eolssu.shared.generated.resources.weak_result_more
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
    /** 오른쪽 위 홈(연주 결과와 같은 자리). */
    val onHome: () -> Unit = {},
    val onOtherSection: () -> Unit,
    /** ‘연습 기록 저장’: 저장 시트(①, 저장했으면 ②)를 연다. */
    val onSave: () -> Unit = {},
    val onConfirmSave: () -> Unit = {},
    val onMemoChange: (String) -> Unit = {},
    val onCloseSheet: () -> Unit = {},
    val onWholeSong: () -> Unit,
    val onSelectFilter: (ErrorFilter) -> Unit = {},
    /** 저장 완료 시트의 ‘기록으로 이동’. */
    val onOpenRecord: () -> Unit = {},
)

/**
 * RESULT-04 구간 재평가 결과(S15P31E102-776, 와이어프레임 ⑤ · 연주 결과 틀). 왼쪽 정렬 머리 ‘곡 · 2–3각 · 58 BPM / 구간 재평가 결과’와 오른쪽 위 홈 ·
 * 머리는 연주 결과처럼 뒤로 가기 없이 왼쪽 끝에서 시작한다(S15P31E102-808). 결과를 불러오는 중이거나 없으면 뒤로 가기를 둔다 ·
 * 연주 결과와 같은 점수 상자(일원상 구간 점수 · 항목 막대) · 회색 칸의 틀린 음 비교(EVA-04) ·
 * 5칸 틀린 종류 필터 · 구간 정간보(보기 전용) · ‘다른 구간 고르기 ›’,
 * 아래 반씩 나란한 ‘연습 기록 저장’(보조) · ‘곡 전체 다시 평가’(주)(S15P31E102-832).
 * 점수는 서버 값만 쓰고 화면에서 계산하지 않는다. 저장은 연주 결과와 같은 저장 시트(①·②)로 한다.
 */
@Composable
fun SectionResultScreen(
    state: SectionResultUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: SectionResultActions,
) {
    val colors = EolssuTheme.colors
    val evaluation = state.evaluation
    val section = state.section
    // 태블릿 가로만 넓게 두고 두 칸으로 나눈다(S15P31E102-974). 휴대폰·태블릿 세로는 560dp 한 줄 그대로다.
    val expanded = widthClass == WindowWidthClass.Expanded
    val maxWidth = if (expanded) WindowBreakpoints.ContentMaxWidth else CompactContentMaxWidth
    EolssuScaffold(
        containerColor = colors.surface,
        snackbarHostState = snackbarHostState,
        contentMaxWidth = maxWidth,
        topBar = {
            EolssuStartTopBar(
                title = evaluation?.let {
                    if (section != null) {
                        stringResource(Res.string.weak_result_subtitle, it.song.title, scoreSectionLabel(section, it.song.score), it.bpm)
                    } else {
                        stringResource(Res.string.weak_result_subtitle_plain, it.song.title, it.bpm)
                    }
                } ?: stringResource(Res.string.weak_result_title),
                onBack = actions.onBack.takeIf { evaluation == null },
                subtitle = stringResource(Res.string.weak_result_title).takeIf { evaluation != null },
                maxWidth = maxWidth,
                right = {
                    if (evaluation != null) {
                        EolssuIconButton(EolssuIcons.House, stringResource(Res.string.result_home), actions.onHome, bordered = false)
                    }
                },
            )
        },
        bottomBar = if (evaluation != null) {
            { ResultButtons(state, expanded, actions) }
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
        if (expanded) {
            // 태블릿 가로: 왼쪽 칸에 점수 상자 · 틀린 음 비교 · 종류 필터 · ‘다른 구간 고르기’, 오른쪽 칸에 구간 정간보.
            ResultExpandedPanes(
                summary = {
                    SectionSummary(evaluation, state.filter, actions)
                    OtherSectionLink(actions.onOtherSection, Modifier.align(Alignment.CenterHorizontally))
                },
                score = {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        if (section != null) SectionScore(evaluation, section, state.filter)
                    }
                },
            )
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SectionSummary(evaluation, state.filter, actions)
                if (section != null) SectionScore(evaluation, section, state.filter)
                // 다음 단계 안내 문구는 두지 않는다(S15P31E102-832).
                OtherSectionLink(actions.onOtherSection, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
    val recordedAt = evaluation?.result?.recordedAt
    when {
        evaluation == null || recordedAt == null -> Unit
        state.saveSheet == SaveSheet.Form -> SaveFormSheet(
            title = evaluation.song.title,
            recordedAt = recordedAt,
            total = evaluation.total,
            memo = state.memo,
            saving = state.savingRecord,
            onMemoChange = actions.onMemoChange,
            onConfirm = actions.onConfirmSave,
            onDismiss = actions.onCloseSheet,
        )
        state.saveSheet == SaveSheet.Saved -> SavedSheet(actions.onCloseSheet, actions.onOpenRecord)
    }
}

/** 점수 상자 · 틀린 음 비교 · 틀린 종류 필터. 휴대폰은 악보 위에, 태블릿 가로는 왼쪽 칸에 쌓는다. */
@Composable
private fun SectionSummary(evaluation: SectionEvaluation, filter: ErrorFilter, actions: SectionResultActions) {
    ScoreSummaryCard(
        total = evaluation.total,
        metrics = evaluation.metrics,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
    ErrorCompareBox(evaluation.check, Modifier.padding(bottom = 8.dp))
    val result = evaluation.result
    if (result != null) ErrorKindSegments(result, filter, actions.onSelectFilter, Modifier.padding(bottom = 8.dp))
}

/** ‘다른 구간 고르기 ›’ 글자 버튼. */
@Composable
private fun OtherSectionLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    EolssuTextLink(onClick, modifier.padding(top = 10.dp, bottom = 10.dp), qaTarget = QaTarget.SECTION_RESULT_SCREEN_LINK_01) {
        Text(
            stringResource(Res.string.weak_result_other_more),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = EolssuTheme.colors.textSub,
        )
    }
}

/**
 * 아래 고정 영역: 왼쪽 보조 ‘연습 기록 저장’(저장했으면 ‘저장됨’), 오른쪽 주 ‘곡 전체 다시 평가’. 휴대폰·태블릿 모두 한 줄에 반씩 두고,
 * 태블릿 가로는 줄을 최대 640dp로 묶는다(S15P31E102-974).
 * 글꼴을 키워 줄이 바뀌어도 두 버튼 높이가 같다. 평가 결과(EVA-01)를 받지 못해 저장할 수 없으면 ‘곡 전체 다시 평가’만 둔다.
 */
@Composable
private fun ColumnScope.ResultButtons(state: SectionResultUiState, expanded: Boolean, actions: SectionResultActions) {
    ResultButtonRow(expanded) {
        if (state.canSave) {
            RecordSaveButton(
                state.savedRecord != null, actions.onSave, Modifier.weight(1f).fillMaxHeight(), style = EolssuButtonStyle.Secondary,
            )
        }
        EolssuButton(
            stringResource(Res.string.weak_result_whole), actions.onWholeSong, Modifier.weight(1f).fillMaxHeight(),
            compact = true, qaTarget = QaTarget.SECTION_RESULT_SCREEN_BUTTON_01
        )
    }
}

/**
 * 재평가한 구간의 정간보(보기 전용, 연주 결과와 같은 읽기 형식). 틀린 정간은 [filter] 종류만 색·아이콘으로 칠한다.
 * [section]의 각을 그리고, 연두 하이라이트는 실제로 평가된 정간([playedSection])에만 둔다. 각 표시는 전체가 평가된 각에만 둔다.
 * 평가 결과가 없으면(EVA-01을 받지 못함) 틀린 표시 없이 악보만 그리고 [section] 전체를 하이라이트한다.
 */
@Composable
private fun SectionScore(evaluation: SectionEvaluation, section: ScoreSection, filter: ErrorFilter) {
    val song = evaluation.song
    val result = evaluation.result
    val format = rememberReadingScoreFormat(song)
    val played = remember(evaluation) { evaluation.playedSection() } ?: section
    JeongganboView(
        song = song,
        modifier = Modifier.fillMaxWidth(),
        metrics = format.metrics,
        look = format.look,
        cellHeights = format.cellHeights,
        columns = minOf(section.start.column, played.start.column)..maxOf(section.end.column, played.end.column),
        errors = result?.visibleErrors(filter).orEmpty(),
        errorKinds = result?.visibleErrorKinds(filter).orEmpty(),
        unitErrors = result?.visibleUnitErrors(song, filter).orEmpty(),
        section = played,
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
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
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
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (fewer) colors.accentText else colors.text,
        )
    }
}
