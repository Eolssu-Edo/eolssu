package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.component.metricLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.data.practice.SessionCheck
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.observeFirstDraw
import com.ssafy.eolssu.core.model.MetricScore
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_save
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_result_again
import eolssu.shared.generated.resources.weak_result_next_ready
import eolssu.shared.generated.resources.weak_result_next_slow
import eolssu.shared.generated.resources.weak_result_next_title
import eolssu.shared.generated.resources.weak_result_other
import eolssu.shared.generated.resources.weak_result_same
import eolssu.shared.generated.resources.weak_result_score_note
import eolssu.shared.generated.resources.weak_result_errors_title
import eolssu.shared.generated.resources.weak_result_errors_change
import eolssu.shared.generated.resources.weak_result_errors_now
import eolssu.shared.generated.resources.weak_result_fewer
import eolssu.shared.generated.resources.weak_result_more
import eolssu.shared.generated.resources.weak_result_corrected
import eolssu.shared.generated.resources.weak_result_not_comparable
import eolssu.shared.generated.resources.weak_result_compare_rejected
import eolssu.shared.generated.resources.weak_result_compare_failed
import eolssu.shared.generated.resources.weak_result_subtitle_plain
import eolssu.shared.generated.resources.weak_result_subtitle
import eolssu.shared.generated.resources.weak_result_title
import eolssu.shared.generated.resources.weak_result_whole
import org.jetbrains.compose.resources.stringResource

/** 구간 재평가 결과에서 부르는 동작. */
class SectionResultActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onPracticeAgain: () -> Unit,
    val onOtherSection: () -> Unit,
    val onWholeSong: () -> Unit,
)

/**
 * RESULT-04 구간 재평가 결과(2차 `SectionResult`): 이번 구간 총점 · 바탕 연주와의 틀린 음 비교(EVA-04) · 항목 점수 · 다음 단계.
 * 서버가 점수를 비교하지 않아(EVA-04 2차 계약 미구현) 점수는 이번 값만 보이고, 비교는 틀린 음 수로 한다. 화면에서 점수를 계산하지 않는다.
 * 2차 결정으로 ‘남은 오류’ 칸은 없다. 아래에 ‘다시 연습’과 ‘다른 구간 고르기’ · ‘곡 전체 다시 평가’.
 */
@Composable
fun SectionResultScreen(state: SectionResultUiState, snackbarHostState: SnackbarHostState, actions: SectionResultActions) {
    val colors = EolssuTheme.colors
    val evaluation = state.evaluation
    EolssuScaffold(
        containerColor = colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.weak_result_title),
                onBack = actions.onBack,
                subtitle = evaluation?.let {
                    val section = state.section
                    if (section != null) {
                        stringResource(Res.string.weak_result_subtitle, it.song.title, scoreSectionLabel(section, it.song.score), it.bpm)
                    } else {
                        stringResource(Res.string.weak_result_subtitle_plain, it.song.title, it.bpm)
                    }
                },
                right = { EolssuIconButton(EolssuIcons.Bookmark, stringResource(Res.string.result_save), actions.onSave) },
            )
        },
        bottomBar = if (evaluation != null) {
            {
                EolssuButton(stringResource(Res.string.weak_result_again), actions.onPracticeAgain)
                // 둘 다 흰 바탕에 옅은 테두리(`#E8E4DA`)다.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EolssuButton(
                        stringResource(Res.string.weak_result_other), actions.onOtherSection, Modifier.weight(1f),
                        style = EolssuButtonStyle.Secondary, compact = true, borderColor = colors.lineSoft,
                    )
                    EolssuButton(
                        stringResource(Res.string.weak_result_whole), actions.onWholeSong, Modifier.weight(1f),
                        style = EolssuButtonStyle.Secondary, compact = true, borderColor = colors.lineSoft,
                    )
                }
            }
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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            ScoreHeader(evaluation.total)
            ErrorCompareCard(evaluation.check, Modifier.padding(top = 18.dp))
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                evaluation.metrics.forEach { MetricCard(it) }
            }
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().border(1.dp, colors.lineSoft, shape).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    stringResource(Res.string.weak_result_next_title),
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    if (evaluation.bpm < evaluation.song.baseBpm) {
                        stringResource(Res.string.weak_result_next_slow, evaluation.song.baseBpm, WholeSongReadyScore)
                    } else {
                        stringResource(Res.string.weak_result_next_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                )
            }
        }
    }
}

/** 이번 구간 총점(60sp, ‘85점’)과 점수 안내. */
@Composable
private fun ScoreHeader(total: Int) {
    val colors = EolssuTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                append("$total")
                withStyle(SpanStyle(fontSize = 18.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)) {
                    append(stringResource(Res.string.result_score_unit))
                }
            },
            modifier = Modifier.observeFirstDraw(PerfPhase.SCORE_DRAWN),
            style = EolssuTheme.serif.brand.copy(fontSize = 60.sp, lineHeight = 60.sp),
        )
        Text(
            stringResource(Res.string.weak_result_score_note),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
    }
}

/**
 * 바탕 연주와의 틀린 음 비교(EVA-04): ‘이전 12개 → 이번 5개’와 줄어든 수 알약, 고친 음 수.
 * 견줄 수 없으면(악보 판본·채점 모델·속도가 다름) 서버 사유와 이번 틀린 음 수만, 비교를 받지 못하면 안내만 보인다.
 */
@Composable
private fun ErrorCompareCard(check: CheckLoad, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    Column(
        modifier.fillMaxWidth().border(1.dp, colors.lineSoft, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(Res.string.weak_result_errors_title), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
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
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    val before = check.beforeErrors
    val delta = check.deltaErrors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (check.comparable && before != null) {
                stringResource(Res.string.weak_result_errors_change, before, check.afterErrors)
            } else {
                stringResource(Res.string.weak_result_errors_now, check.afterErrors)
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
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
        Modifier.heightIn(min = 26.dp).background(if (fewer) colors.primary else colors.surfaceStrong, CircleShape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (fewer) Icon(EolssuIcons.ArrowUpRight, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.onPrimary)
        Text(
            when {
                delta < 0 -> stringResource(Res.string.weak_result_fewer, -delta)
                delta > 0 -> stringResource(Res.string.weak_result_more, delta)
                else -> stringResource(Res.string.weak_result_same)
            },
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (fewer) colors.onPrimary else colors.text,
        )
    }
}

/** 항목 카드: 이름 · ‘86점’ · 연두 6dp 막대. */
@Composable
private fun MetricCard(score: MetricScore) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val unit = stringResource(Res.string.result_score_unit)
    Column(Modifier.fillMaxWidth().border(1.dp, colors.lineSoft, shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(metricLabel(score.metric), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) { append("${score.score}") }
                    append(unit)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            )
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.background)) {
            Box(Modifier.fillMaxWidth(score.score.coerceIn(0, 100) / 100f).fillMaxHeight().background(colors.accent))
        }
    }
}
