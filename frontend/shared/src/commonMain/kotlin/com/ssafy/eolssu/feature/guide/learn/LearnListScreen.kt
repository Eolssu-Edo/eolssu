package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.component.GuideText
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.feature.guide.component.JudgeTag
import com.ssafy.eolssu.feature.guide.component.dashedRoundBorder
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_done
import eolssu.shared.generated.resources.learn_list_source
import eolssu.shared.generated.resources.learn_optional
import eolssu.shared.generated.resources.learn_progress_all_done
import eolssu.shared.generated.resources.learn_progress_done
import eolssu.shared.generated.resources.learn_progress_label
import eolssu.shared.generated.resources.learn_progress_next
import eolssu.shared.generated.resources.learn_progress_not_started
import eolssu.shared.generated.resources.learn_progress_total
import eolssu.shared.generated.resources.learn_reset
import eolssu.shared.generated.resources.learn_stage_item
import eolssu.shared.generated.resources.learn_stage_item_done
import eolssu.shared.generated.resources.learn_stages
import eolssu.shared.generated.resources.learn_subtitle
import eolssu.shared.generated.resources.learn_title
import org.jetbrains.compose.resources.stringResource

/** LEARN-02 단계 목록. 악기별 진행률과 9단계를 보여주고, 잠금 없이 아무 단계나 열 수 있다. */
@Composable
fun LearnListScreen(
    state: LearnUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleDropdown: () -> Unit,
    onDismissDropdown: () -> Unit,
    onSelectInstrument: (GuideInstrument) -> Unit,
    onResetDone: () -> Unit,
    onOpenStage: (Int) -> Unit,
) {
    EolssuScaffold(
        topBar = { EolssuTopBar(stringResource(Res.string.learn_title), onBack = onBack, subtitle = stringResource(Res.string.learn_subtitle)) },
        snackbarHostState = snackbarHostState,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            InstrumentDropdown(
                selected = state.instrument,
                open = state.isDropdownOpen,
                onToggle = onToggleDropdown,
                onSelect = onSelectInstrument,
                onDismiss = onDismissDropdown,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            ProgressCard(state)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.learn_stages),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = EolssuTheme.colors.textMuted,
                )
                Text(
                    stringResource(Res.string.learn_reset),
                    Modifier.clickable(role = Role.Button, onClick = onResetDone),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, textDecoration = TextDecoration.Underline),
                    color = EolssuTheme.colors.textMuted,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnStages.forEach { stage -> StageRow(stage, state.isDone(stage.number)) { onOpenStage(stage.number) } }
            }
            Text(
                stringResource(Res.string.learn_list_source),
                Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 17.sp),
                color = EolssuTheme.colors.textMuted,
            )
        }
    }
}

/** 진행 카드(`.lp-progress`). 진행률은 필수 8단계 기준이다. */
@Composable
private fun ProgressCard(state: LearnUiState) {
    val colors = EolssuTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(colors.surface, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.learn_progress_label, stringResource(state.instrument.label)), Modifier.weight(1f), style = small, color = colors.textMuted)
            val doneCount = state.doneStages.size
            Text(
                if (doneCount > 0) stringResource(Res.string.learn_progress_done, doneCount) else stringResource(Res.string.learn_progress_not_started),
                style = small,
                color = colors.textMuted,
            )
        }
        val total = stringResource(Res.string.learn_progress_total, RequiredLearnStages.size)
        Text(
            buildAnnotatedString {
                append("${state.requiredDoneCount}")
                withStyle(SpanStyle(fontFamily = MaterialTheme.typography.bodySmall.fontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textMuted)) {
                    append("  ")
                    append(total)
                }
            },
            style = EolssuTheme.serif.yul.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.background)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(state.progress).background(colors.text))
        }
        val next = state.nextStage
        GuideText(
            if (next != null) stringResource(Res.string.learn_progress_next, next.number, next.title) else stringResource(Res.string.learn_progress_all_done),
            Modifier.padding(top = 10.dp),
            style = small,
        )
    }
}

/** 단계 한 줄(`.lp-stage`). 완료면 번호 대신 ✓, 선택 단계는 점선 테두리. */
@Composable
private fun StageRow(stage: LearnStage, done: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = MaterialTheme.shapes.medium
    val description = if (done) {
        stringResource(Res.string.learn_stage_item_done, stage.number, stage.title)
    } else {
        stringResource(Res.string.learn_stage_item, stage.number, stage.title)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background)
            .then(if (stage.optional) Modifier.dashedRoundBorder(colors.line, 16.dp) else Modifier.border(1.dp, colors.line, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .then(if (done) Modifier.background(colors.text, CircleShape) else Modifier.border(1.5.dp, colors.lineStrong, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimary)
            } else {
                Text("${stage.number}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        }
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stage.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
                if (stage.optional) JudgeTag(stringResource(Res.string.learn_optional), LearnJudge.Read)
            }
            Text(
                stage.goal,
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.textMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            JudgeTag(stringResource(stage.judge.label), stage.judge)
            if (done) {
                Text(stringResource(Res.string.learn_done), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
            } else {
                Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.lineStrong)
            }
        }
    }
}
