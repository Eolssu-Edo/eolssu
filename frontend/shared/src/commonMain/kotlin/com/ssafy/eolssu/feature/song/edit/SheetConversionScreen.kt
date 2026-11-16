package com.ssafy.eolssu.feature.song.edit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.BrushEnso
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.data.song.SheetNotation
import com.ssafy.eolssu.feature.song.ConversionFailureGuide
import com.ssafy.eolssu.feature.song.ConversionNextAction
import com.ssafy.eolssu.feature.song.ConversionStep
import com.ssafy.eolssu.feature.song.conversionStep
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.profile_tiger_study
import eolssu.shared.generated.resources.sheet_conversion_building_body
import eolssu.shared.generated.resources.sheet_conversion_building_title
import eolssu.shared.generated.resources.sheet_conversion_checking
import eolssu.shared.generated.resources.sheet_conversion_done_body
import eolssu.shared.generated.resources.sheet_conversion_done_title
import eolssu.shared.generated.resources.sheet_conversion_leave_hint
import eolssu.shared.generated.resources.sheet_conversion_missing
import eolssu.shared.generated.resources.sheet_conversion_progress
import eolssu.shared.generated.resources.sheet_conversion_queued_body
import eolssu.shared.generated.resources.sheet_conversion_queued_title
import eolssu.shared.generated.resources.sheet_conversion_reading_body
import eolssu.shared.generated.resources.sheet_conversion_reading_pages_body
import eolssu.shared.generated.resources.sheet_conversion_reading_title
import eolssu.shared.generated.resources.sheet_conversion_reading_total_body
import eolssu.shared.generated.resources.sheet_conversion_staff_hint
import eolssu.shared.generated.resources.sheet_conversion_step_building
import eolssu.shared.generated.resources.sheet_conversion_step_queued
import eolssu.shared.generated.resources.sheet_conversion_step_reading
import eolssu.shared.generated.resources.sheet_conversion_step_state_done
import eolssu.shared.generated.resources.sheet_conversion_step_state_next
import eolssu.shared.generated.resources.sheet_conversion_step_state_now
import eolssu.shared.generated.resources.sheet_conversion_title
import eolssu.shared.generated.resources.sheet_conversion_to_list
import eolssu.shared.generated.resources.sheet_conversion_unreachable
import eolssu.shared.generated.resources.sheet_conversion_wait_in_list
import eolssu.shared.generated.resources.sheet_conversion_what_to_do
import eolssu.shared.generated.resources.tiger_analysis_failed
import eolssu.shared.generated.resources.tiger_retry
import kotlin.math.floor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 악보 변환 대기 화면(S15P31E102-998). 연주 분석 대기처럼 붓 일원상이 진행만큼 그려지고 가운데에 공부하는 호랑이를 둔다.
 * 위에서부터 단계 제목 · 악보 제목 · 단계 설명 · 단계 줄(차례 기다리기 → 악보 읽기 → 악보 만들기) · 나가도 된다는 안내 순서다.
 * 실패하면 붓 대신 호랑이와 원인 · 할 일 목록 · 다음 행동 버튼을 둔다. 완료되면 Route가 편집 화면으로 보낸다.
 */
@Composable
fun SheetConversionScreen(
    state: SheetConversionUiState,
    onList: () -> Unit,
    onReimport: () -> Unit,
) {
    val failure = state.failure
    EolssuScaffold(
        topBar = { EolssuTopBar(stringResource(Res.string.sheet_conversion_title), onBack = onList) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    failure != null -> {
                        EolssuButton(stringResource(failure.action.label), onReimport)
                        EolssuButton(stringResource(Res.string.sheet_conversion_to_list), onList,
                            style = EolssuButtonStyle.Secondary, borderColor = Color.Transparent)
                    }
                    state.missing -> EolssuButton(stringResource(Res.string.sheet_conversion_to_list), onList)
                    !state.done -> EolssuButton(stringResource(Res.string.sheet_conversion_wait_in_list), onList,
                        style = EolssuButtonStyle.Secondary)
                }
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                when {
                    failure != null -> FailedContent(failure, state.title)
                    state.missing -> BodyText(stringResource(Res.string.sheet_conversion_missing))
                    else -> WaitingContent(state)
                }
            }
        }
    }
}

@Composable
private fun WaitingContent(state: SheetConversionUiState) {
    val colors = EolssuTheme.colors
    val conversion = state.conversion
    val step = conversion?.let(::conversionStep)
    val progress = state.progress
    ConvertingFigure(progress)
    val title = when {
        state.done -> Res.string.sheet_conversion_done_title
        conversion == null -> Res.string.sheet_conversion_checking
        step == ConversionStep.Queued -> Res.string.sheet_conversion_queued_title
        step == ConversionStep.Building -> Res.string.sheet_conversion_building_title
        else -> Res.string.sheet_conversion_reading_title
    }
    Text(
        stringResource(title),
        modifier = Modifier.padding(top = 8.dp).semantics { heading(); liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 28.6.sp),
        color = colors.text,
        textAlign = TextAlign.Center,
    )
    state.title?.let { EolssuBadge(it, style = EolssuBadgeStyle.Outline) }
    val body = when {
        state.done -> stringResource(Res.string.sheet_conversion_done_body)
        step == ConversionStep.Queued -> stringResource(Res.string.sheet_conversion_queued_body)
        step == ConversionStep.Building -> stringResource(Res.string.sheet_conversion_building_body)
        step is ConversionStep.Reading -> {
            val pages = when {
                step.pagesRead != null -> stringResource(Res.string.sheet_conversion_reading_pages_body, step.pagesRead, step.pageCount)
                step.pageCount > 0 -> stringResource(Res.string.sheet_conversion_reading_total_body, step.pageCount)
                else -> stringResource(Res.string.sheet_conversion_reading_body)
            }
            if (conversion?.notation == SheetNotation.Staff) pages + "\n" + stringResource(Res.string.sheet_conversion_staff_hint) else pages
        }
        else -> null
    }
    body?.let { BodyText(it) }
    if (conversion != null) StepLine(step, state.done)
    if (state.unreachable) BodyText(stringResource(Res.string.sheet_conversion_unreachable), color = colors.recText)
    if (!state.done) BodyText(stringResource(Res.string.sheet_conversion_leave_hint), color = colors.textMuted)
}

/** 붓 일원상(220dp)과 공부하는 호랑이. 진행을 모르면(첫 응답 전) 붓을 되풀이해 그린다. */
@Composable
private fun ConvertingFigure(progress: Float?) {
    // 서버 응답이 2초 간격이라 그 사이를 부드럽게 잇는다.
    val shown by animateFloatAsState(progress ?: 0f, animationSpec = tween(1_800))
    val percent = progress?.let { floor(shown * 100).toInt().coerceIn(0, 100) }
    val description = percent?.let { stringResource(Res.string.sheet_conversion_progress, it) }
    Box(
        Modifier.size(220.dp).clearAndSetSemantics {
            if (description != null) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
            }
        },
    ) {
        BrushEnso(color = EolssuTheme.colors.text, progress = if (progress == null) null else shown, modifier = Modifier.fillMaxSize())
        Image(
            painterResource(Res.drawable.profile_tiger_study),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).offset(y = (-4).dp).width(120.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/** 차례 기다리기 → 악보 읽기 → 악보 만들기. 지난 단계는 체크, 지금 단계는 연두 점, 남은 단계는 옅은 점이다. */
@Composable
private fun StepLine(step: ConversionStep?, done: Boolean) {
    val current = when {
        done -> 3
        step == ConversionStep.Queued -> 0
        step == ConversionStep.Building -> 2
        else -> 1
    }
    val labels = listOf(Res.string.sheet_conversion_step_queued, Res.string.sheet_conversion_step_reading, Res.string.sheet_conversion_step_building)
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            StepItem(stringResource(label), state = when {
                index < current -> ConversionStepMark.Done
                index == current -> ConversionStepMark.Now
                else -> ConversionStepMark.Next
            })
        }
    }
}

private enum class ConversionStepMark { Done, Now, Next }

@Composable
private fun StepItem(label: String, state: ConversionStepMark) {
    val colors = EolssuTheme.colors
    val spoken = stringResource(
        when (state) {
            ConversionStepMark.Done -> Res.string.sheet_conversion_step_state_done
            ConversionStepMark.Now -> Res.string.sheet_conversion_step_state_now
            ConversionStepMark.Next -> Res.string.sheet_conversion_step_state_next
        },
        label,
    )
    Row(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            ConversionStepMark.Done -> Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.accentText)
            ConversionStepMark.Now -> Box(Modifier.size(8.dp).background(colors.primary, CircleShape))
            ConversionStepMark.Next -> Box(Modifier.size(8.dp).background(colors.lineSoft, CircleShape))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = if (state == ConversionStepMark.Next) colors.textMuted else colors.text,
        )
    }
}

/** 실패: 호랑이 · 경고 아이콘과 원인 · 악보 제목 · '이렇게 해 보세요'와 할 일 목록. 서버 문제는 실패 전용 호랑이를 쓴다. */
@Composable
private fun FailedContent(failure: ConversionFailureGuide, title: String?) {
    val colors = EolssuTheme.colors
    Image(
        painterResource(if (failure.action == ConversionNextAction.TryLater) Res.drawable.tiger_analysis_failed else Res.drawable.tiger_retry),
        contentDescription = null,
        modifier = Modifier.size(132.dp),
        contentScale = ContentScale.Fit,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(EolssuIcons.Alert, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.recText)
        Text(
            stringResource(failure.title),
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 28.6.sp),
            color = colors.text,
            textAlign = TextAlign.Center,
        )
    }
    title?.let { EolssuBadge(it, style = EolssuBadgeStyle.Outline) }
    Column(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.sheet_conversion_what_to_do),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = colors.text,
        )
        failure.tips.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.accentText)
                Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.3.sp), color = colors.textSub)
            }
        }
    }
}

@Composable
private fun BodyText(text: String, color: Color = EolssuTheme.colors.textSub) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.3.sp),
        color = color,
        textAlign = TextAlign.Center,
    )
}
