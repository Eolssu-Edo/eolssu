package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.component.GuideGrid
import com.ssafy.eolssu.feature.guide.component.GuideText
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.feature.guide.component.JudgeTag
import com.ssafy.eolssu.feature.guide.component.dashedRoundBorder
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
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

/** 단계 목록의 동작. 휴대폰·태블릿 세로 목록과 태블릿 가로 왼쪽 칸이 함께 쓴다. */
class LearnListActions(
    val onToggleDropdown: () -> Unit,
    val onDismissDropdown: () -> Unit,
    val onSelectInstrument: (GuideInstrument) -> Unit,
    val onResetDone: () -> Unit,
    val onOpenStage: (Int) -> Unit,
)

/**
 * LEARN-02 단계 목록. 악기별 진행률과 9단계를 보여주고, 잠금 없이 아무 단계나 열 수 있다.
 * 태블릿 세로는 단계를 두 칸 격자로 놓는다. 태블릿 가로는 단계 상세와 한 화면이라 [com.ssafy.eolssu.feature.guide.layout.LearnExpandedLayout]이 그린다.
 */
@Composable
fun LearnListScreen(
    state: LearnUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    actions: LearnListActions,
) {
    EolssuScaffold(
        topBar = {
            GuideTopBar(stringResource(Res.string.learn_title), onBack = onBack, widthClass = widthClass, subtitle = stringResource(Res.string.learn_subtitle))
        },
        snackbarHostState = snackbarHostState,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        val tablet = widthClass != WindowWidthClass.Compact
        val side = widthClass.guideSidePadding
        LearnListContent(
            state,
            actions,
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
            stageColumns = if (tablet) 2 else 1,
            dropdownMaxWidth = if (tablet) TabletDropdownMaxWidth else Dp.Unspecified,
        )
    }
}

/** 태블릿 세로 목록의 악기 고르기 최대 너비(이름 + 드롭다운 360dp, `.tabapp .fg-select .dd`). */
private val TabletDropdownMaxWidth = 400.dp

/**
 * 단계 목록 본문(악기 · 진행 카드 · 단계 · 출처). 세로로 스크롤한다.
 *
 * @param stageColumns 단계 칸 수. 태블릿 세로는 2칸.
 * @param highlightedStage 태블릿 가로 배치에서 오른쪽 칸에 보이는 단계. 굵은 테두리로 표시한다.
 */
@Composable
internal fun LearnListContent(
    state: LearnUiState,
    actions: LearnListActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    stageColumns: Int = 1,
    highlightedStage: Int? = null,
    dropdownMaxWidth: Dp = Dp.Unspecified,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        InstrumentDropdown(
            selected = state.instrument,
            open = state.isDropdownOpen,
            onToggle = actions.onToggleDropdown,
            onSelect = actions.onSelectInstrument,
            onDismiss = actions.onDismissDropdown,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp).widthIn(max = dropdownMaxWidth),
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
                Modifier.clickable(role = Role.Button, onClick = actions.onResetDone),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, textDecoration = TextDecoration.Underline),
                color = EolssuTheme.colors.textMuted,
            )
        }
        GuideGrid(LearnStages, stageColumns, horizontalGap = 10.dp, verticalGap = if (stageColumns > 1) 10.dp else 8.dp) { _, stage, cell ->
            StageRow(stage, state.isDone(stage.number), highlighted = stage.number == highlightedStage, modifier = cell) {
                actions.onOpenStage(stage.number)
            }
        }
        Text(
            stringResource(Res.string.learn_list_source),
            Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 17.sp),
            color = EolssuTheme.colors.textMuted,
        )
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

/**
 * 단계 한 줄(`.lp-stage`). 완료면 번호 대신 ✓, 선택 단계는 점선 테두리.
 * [highlighted]는 태블릿 가로 배치에서 오른쪽에 보이는 단계로, 진한 2dp 테두리와 회색 바탕이다.
 */
@Composable
private fun StageRow(stage: LearnStage, done: Boolean, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = MaterialTheme.shapes.medium
    val description = if (done) {
        stringResource(Res.string.learn_stage_item_done, stage.number, stage.title)
    } else {
        stringResource(Res.string.learn_stage_item, stage.number, stage.title)
    }
    Row(
        modifier
            .clip(shape)
            .background(if (highlighted) colors.surface else colors.background)
            .then(
                when {
                    highlighted -> Modifier.border(2.dp, colors.text, shape)
                    stage.optional -> Modifier.dashedRoundBorder(colors.line, 16.dp)
                    else -> Modifier.border(1.dp, colors.line, shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (highlighted) selected = true
            }
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
                Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onInk)
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
