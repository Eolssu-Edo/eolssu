package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.diagnostics.QaTarget

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.imageResource
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
import eolssu.shared.generated.resources.learning_mascot_study
import eolssu.shared.generated.resources.sheet_conversion_building_body
import eolssu.shared.generated.resources.sheet_conversion_building_title
import eolssu.shared.generated.resources.sheet_conversion_checking
import eolssu.shared.generated.resources.sheet_conversion_done_body
import eolssu.shared.generated.resources.sheet_conversion_done_title
import eolssu.shared.generated.resources.sheet_conversion_open_editor
import eolssu.shared.generated.resources.sheet_conversion_leave_hint
import eolssu.shared.generated.resources.sheet_conversion_missing
import eolssu.shared.generated.resources.sheet_conversion_progress
import eolssu.shared.generated.resources.sheet_conversion_queued_body
import eolssu.shared.generated.resources.sheet_conversion_queued_title
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
 * 실패하면 붓 대신 호랑이와 원인 · 할 일 목록을 두고, 원본을 보관하지 않아 다시 불러올 수 없으므로 '내 악보 목록으로'만 둔다(S15P31E102-1140). 완료되면 Route가 편집 화면으로 보내고,
 * 화면 전환 중이라 이동이 무시돼도 머물지 않게 '편집 화면 열기'([onOpenEditor])를 둔다.
 */
@Composable
fun SheetConversionScreen(
    state: SheetConversionUiState,
    onList: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val failure = state.failure
    EolssuScaffold(
        topBar = { EolssuTopBar(stringResource(Res.string.sheet_conversion_title), onBack = onList) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    failure != null ->
                        EolssuButton(stringResource(Res.string.sheet_conversion_to_list), onList, qaTarget = QaTarget.SHEET_CONVERSION_SCREEN_BUTTON_02)
                    state.missing -> EolssuButton(stringResource(Res.string.sheet_conversion_to_list), onList, qaTarget = QaTarget.SHEET_CONVERSION_SCREEN_BUTTON_03)
                    state.done -> EolssuButton(stringResource(Res.string.sheet_conversion_open_editor), onOpenEditor, qaTarget = QaTarget.SHEET_CONVERSION_SCREEN_BUTTON_04)
                    else -> EolssuButton(stringResource(Res.string.sheet_conversion_wait_in_list), onList,
                        style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SHEET_CONVERSION_SCREEN_BUTTON_05)
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
    ConvertingFigure(progress, finishing = state.done)
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
    // 안내는 두 줄: 지금 할 일·걸리는 시간 한 줄, 나가도 계속된다는 한 줄(S15P31E102-1042).
    val body = waitingBody(step, conversion?.notation, state.done)?.let { stringResource(it.text, *it.args.toTypedArray()) }
    val lines = listOfNotNull(body, stringResource(Res.string.sheet_conversion_leave_hint).takeIf { !state.done })
    if (lines.isNotEmpty()) BodyText(lines.joinToString("\n"))
    if (conversion != null) StepLine(step, state.done)
    if (state.unreachable) BodyText(stringResource(Res.string.sheet_conversion_unreachable), color = colors.recText)
}

/** 단계 아래 한 줄 설명. 제목이 단계를 말하므로 설명은 한 줄만 둔다. */
internal data class WaitingBody(val text: StringResource, val args: List<Any> = emptyList())

/**
 * 단계 아래 한 줄 설명을 고른다. 서버가 읽은 쪽 수를 주면 그것을, 오선보는 걸리는 시간을, 여러 쪽 정간보는 쪽 수를 쓴다.
 * 1쪽 정간보처럼 금방 끝나 더 말할 것이 없으면 null이다.
 */
internal fun waitingBody(step: ConversionStep?, notation: SheetNotation?, done: Boolean): WaitingBody? = when {
    done -> WaitingBody(Res.string.sheet_conversion_done_body)
    step == ConversionStep.Queued -> WaitingBody(Res.string.sheet_conversion_queued_body)
    step == ConversionStep.Building -> WaitingBody(Res.string.sheet_conversion_building_body)
    step !is ConversionStep.Reading -> null
    step.pagesRead != null -> WaitingBody(Res.string.sheet_conversion_reading_pages_body, listOf(step.pagesRead, step.pageCount))
    notation == SheetNotation.Staff -> WaitingBody(Res.string.sheet_conversion_staff_hint)
    step.pageCount > 1 -> WaitingBody(Res.string.sheet_conversion_reading_total_body, listOf(step.pageCount))
    else -> null
}

/**
 * 붓 일원상(220dp)과 책 읽는 호랑이([StudyingTiger]). 붓은 변환 전체에 걸쳐 딱 한 번 그린다(S15P31E102-1042).
 * 단계·쪽이 바뀌며 진행 추정이 잠깐 줄어도 지운 뒤 다시 그리지 않도록 지금까지의 최댓값만 따라간다.
 */
@Composable
private fun ConvertingFigure(progress: Float?, finishing: Boolean) {
    // 서버 응답이 2초 간격이라 그 사이를 부드럽게 잇는다. 완료되면 편집 화면으로 넘어가기 전에(FINISH_SHOW_MS) 끝까지 그린다.
    var peak by remember { mutableFloatStateOf(0f) }
    if (progress != null && progress > peak) peak = progress
    val shown by animateFloatAsState(peak, animationSpec = tween(if (finishing) FINISH_DRAW_MS else 1_800))
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
        BrushEnso(color = EolssuTheme.colors.text, progress = shown, modifier = Modifier.fillMaxSize())
        StudyingTiger(Modifier.align(Alignment.Center).size(160.dp))
    }
}

/**
 * 책 읽는 호랑이 전신 애니메이션. 마이페이지 학습 마스코트(S15P31E102-524)의 프레임 묶음(192px × 30장, 6열)을 재생한다.
 * 프레임은 흰 바탕이라 곱하기 혼합으로 그려 화면 바탕색에 녹인다.
 */
@Composable
private fun StudyingTiger(modifier: Modifier) {
    val sheet = imageResource(Res.drawable.learning_mascot_study)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(if (frame % 3 == 1) 60L else 70L)
            frame = (frame + 1) % STUDY_FRAME_COUNT
        }
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        if (sheet.width < STUDY_FRAME_SIZE * STUDY_FRAME_COLUMNS) return@Canvas
        drawImage(
            image = sheet,
            srcOffset = IntOffset(frame % STUDY_FRAME_COLUMNS * STUDY_FRAME_SIZE, frame / STUDY_FRAME_COLUMNS * STUDY_FRAME_SIZE),
            srcSize = IntSize(STUDY_FRAME_SIZE, STUDY_FRAME_SIZE),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            blendMode = BlendMode.Multiply,
        )
    }
}

private const val STUDY_FRAME_SIZE = 192

/** 완료 붓을 끝까지 그리는 시간. 넘어가기 전 보이는 시간([SheetConversionViewModel.FINISH_SHOW_MS])보다 조금 짧다. */
private const val FINISH_DRAW_MS = 800
private const val STUDY_FRAME_COLUMNS = 6
private const val STUDY_FRAME_COUNT = 30

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
