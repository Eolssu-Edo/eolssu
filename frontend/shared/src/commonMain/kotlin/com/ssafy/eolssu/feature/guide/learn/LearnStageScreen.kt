package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.guide.ReadingPage
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideGrid
import com.ssafy.eolssu.feature.guide.component.GuideIcons
import com.ssafy.eolssu.feature.guide.component.GuideTabletBottomContent
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.LearnPatternBox
import com.ssafy.eolssu.feature.guide.component.LearnSection
import com.ssafy.eolssu.feature.guide.component.LearnTaskList
import com.ssafy.eolssu.feature.guide.component.MoreLink
import com.ssafy.eolssu.feature.guide.component.NoteChips
import com.ssafy.eolssu.feature.guide.component.blowShortLabel
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_stage_position_description
import eolssu.shared.generated.resources.learn_stage_position
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import eolssu.shared.generated.resources.learn_confirm_next
import eolssu.shared.generated.resources.learn_detail_failed
import eolssu.shared.generated.resources.learn_listen_model
import eolssu.shared.generated.resources.learn_mark_done
import eolssu.shared.generated.resources.learn_more_fingering
import eolssu.shared.generated.resources.learn_more_fingering_blow
import eolssu.shared.generated.resources.learn_more_fingering_detail
import eolssu.shared.generated.resources.learn_pattern_ascending
import eolssu.shared.generated.resources.learn_pattern_descending
import eolssu.shared.generated.resources.learn_section_inspect
import eolssu.shared.generated.resources.learn_section_new_notes
import eolssu.shared.generated.resources.learn_section_order
import eolssu.shared.generated.resources.learn_section_patterns
import eolssu.shared.generated.resources.learn_section_range
import eolssu.shared.generated.resources.learn_section_tasks
import eolssu.shared.generated.resources.learn_stage_subtitle_optional
import eolssu.shared.generated.resources.learn_stage_title
import eolssu.shared.generated.resources.learn_start_practice
import eolssu.shared.generated.resources.learn_task_range
import eolssu.shared.generated.resources.learn_task_range_detail
import eolssu.shared.generated.resources.learn_tour_done_body_posture
import eolssu.shared.generated.resources.learn_unmark_done
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.label
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.semantics
import eolssu.shared.generated.resources.learn_stage_nav_next
import eolssu.shared.generated.resources.learn_stage_nav_prev

/** 단계 상세의 동작. 화면 이동은 Route가 이어 준다. */
class LearnStageActions(
    val onBack: () -> Unit,
    val onOpenStage: (Int) -> Unit,
    val onToggleDone: (Int) -> Unit,
    val onConfirm: (Int) -> Unit,
    val onListenModel: () -> Unit,
    val onOpenFingering: (Blow) -> Unit,
    val onOpenReading: (ReadingPage) -> Unit,
    /** 단계 내용을 받지 못했을 때 '다시 시도'. */
    val onRetryDetail: (Int) -> Unit = {},
)

/**
 * 단계 상세 본문의 칸 수. 휴대폰은 모두 한 칸, 태블릿은 운지 카드·할 일 2칸,
 * 시김새 목록은 태블릿 세로 2칸 · 가로 3칸(와이어프레임 `.tabapp .learn`).
 */
internal data class LearnStageColumns(val grid: Int, val sigimsae: Int) {
    val wide: Boolean get() = grid > 1

    companion object {
        val Compact = LearnStageColumns(grid = 1, sigimsae = 1)
        val Portrait = LearnStageColumns(grid = 2, sigimsae = 2)
        val Landscape = LearnStageColumns(grid = 2, sigimsae = 3)
    }
}

/**
 * LEARN-02 단계 상세. 단계 그림과 목표 한 줄 · 단계별 그림 내용 · 통과 기준 · 이전/다음 단계, 아래에 완료 표시와 판정 방식별 버튼.
 * 입문자가 그림으로 따라가도록 글은 할 일·기준 한 줄씩만 두고, 근거·출처·예시 표기는 보이지 않는다.
 * 태블릿 세로는 같은 화면을 넓게 쓰고, 태블릿 가로는 단계 목록과 한 화면이라 [com.ssafy.eolssu.feature.guide.layout.LearnExpandedLayout]이 그린다.
 */
@Composable
fun LearnStageScreen(
    state: LearnUiState,
    stage: LearnStage,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: LearnStageActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    /** 3~7단계의 연습 박스. 마지막 할 일 박스로 붙는다. */
    practice: StepPractice? = null,
) {
    val tablet = widthClass != WindowWidthClass.Compact
    val tour = rememberLearnTour(stage.number)
    EolssuScaffold(
        topBar = {
            GuideTopBar(learnStageTitle(stage), onBack = actions.onBack, widthClass = widthClass, subtitle = learnStageSubtitle(state, stage))
        },
        bottomBar = {
            if (tablet) GuideTabletBottomContent { LearnStageBottomBar(state, stage, actions, tour) }
            else LearnStageBottomBar(state, stage, actions, tour)
        },
        snackbarHostState = snackbarHostState,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        val side = widthClass.guideSidePadding
        LearnStageContent(
            state,
            stage,
            actions,
            noteAudioContent,
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
            columns = if (tablet) LearnStageColumns.Portrait else LearnStageColumns.Compact,
            tour = tour,
            practice = practice,
        )
    }
}

/** 상단 바 제목(예: 4단계 · 기본음). */
@Composable
internal fun learnStageTitle(stage: LearnStage): String = stringResource(Res.string.learn_stage_title, stage.number, stage.title)

/** 상단 바 보조 문구: 악기 이름(선택 단계면 '· 선택 단계'를 붙인다). */
@Composable
internal fun learnStageSubtitle(state: LearnUiState, stage: LearnStage): String {
    val instrumentName = stringResource(state.instrument.label)
    return if (stage.optional) stringResource(Res.string.learn_stage_subtitle_optional, instrumentName) else instrumentName
}

/** 단계 상세 본문. 세로로 스크롤하며, [scrollState]를 단계마다 새로 주면 단계를 바꿀 때 맨 위에서 시작한다. */
@Composable
internal fun LearnStageContent(
    state: LearnUiState,
    stage: LearnStage,
    actions: LearnStageActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    columns: LearnStageColumns = LearnStageColumns.Compact,
    scrollState: ScrollState = rememberScrollState(),
    tour: LearnTour = rememberLearnTour(stage.number),
    practice: StepPractice? = null,
) {
    // 1단계 따라 배우기를 다 마치면 '완료했어요!'를 띄우고, '다음 단계로'는 '확인하고 다음 단계'와 같이 완료 표시 뒤 2단계로 간다.
    if (stage.number == 1) LearnTourDoneDialog(tour) { actions.onConfirm(stage.number) }
    // 2단계 따라 배우기도 같은 창을 띄우고 완료 표시 뒤 3단계로 간다(3단계는 마지막 칸에서 바로 '연습 시작').
    if (stage.number == 2) LearnTourDoneDialog(tour, Res.string.learn_tour_done_body_posture) { actions.onConfirm(stage.number) }
    Column(
        modifier
            .verticalScroll(scrollState)
            .padding(contentPadding),
    ) {
        when (val load = state.detail(stage.number)) {
            is LearnDetailLoad.Loaded -> {
                val detail = load.detail
                StagePosition(state, stage.number) { DoneToggleButton(state.isDone(stage.number), onClick = { actions.onToggleDone(stage.number) }) }
                StageHero(stage, detail)
                StageBody(state, stage, detail, actions, columns, noteAudioContent, tour, practice)
            }
            else -> LearnLoadStatus(
                failed = load == LearnDetailLoad.Failed,
                failedMessage = Res.string.learn_detail_failed,
                onRetry = { actions.onRetryDetail(stage.number) },
            )
        }
    }
}

/**
 * 단계 머리: 왼쪽에 단계 그림, 오른쪽에 판정 방식과 목표 한 줄.
 * 입문자가 글을 읽지 않아도 무엇을 하는 단계인지 그림으로 알도록, 근거·배경 설명([LearnStageDetail.why])은 보이지 않는다.
 */
@Composable
private fun StageHero(stage: LearnStage, detail: LearnStageDetail) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 단계 그림이 숨 쉬듯 위아래로 살짝 떠 있다(S15P31E102-688).
        val float by rememberInfiniteTransition(label = "heroFloat").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "heroFloatValue",
        )
        LearnStageArt(
            stage.number,
            Modifier
                .graphicsLayer { translationY = float * 3.dp.toPx() }
                .size(StageHeroArtSize)
                .clip(shape)
                .background(colors.background)
                .border(1.dp, colors.lineSoft, shape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(detail.goal, style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold))
        }
    }
}

private val StageHeroArtSize = 88.dp

/**
 * 단계 안에서도 지금 몇 단계인지 보이는 작은 줄. 단계마다 점 하나를 두고 완료는 연두, 지금 단계는 길쭉한 진한 점, 나머지는 옅게 칠하며
 * 'n / 전체'를 적는다. 화면 읽기에는 '전체 n단계 중 m단계'로 한 번에 읽힌다. 맨 오른쪽에 [trailing](완료 표시 버튼)을 둔다.
 */
@Composable
private fun StagePosition(state: LearnUiState, current: Int, trailing: @Composable () -> Unit) {
    val stages = state.stages
    val index = stages.indexOfFirst { it.number == current }
    if (index < 0) return
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.learn_stage_position_description, stages.size, index + 1)
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            stages.forEach { s ->
                val isCurrent = s.number == current
                Box(
                    Modifier
                        .size(width = if (isCurrent) 18.dp else 6.dp, height = 6.dp)
                        .background(
                            when {
                                isCurrent -> colors.primary
                                state.isDone(s.number) -> colors.accent
                                else -> colors.line
                            },
                            CircleShape,
                        ),
                )
            }
            Text(
                stringResource(Res.string.learn_stage_position, index + 1, stages.size),
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
        }
        trailing()
    }
}

@Composable
private fun StageBody(
    state: LearnUiState,
    stage: LearnStage,
    detail: LearnStageDetail,
    actions: LearnStageActions,
    columns: LearnStageColumns,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    tour: LearnTour,
    practice: StepPractice?,
) {
    val instrument = state.instrument
    val noteOf = { hanja: String -> instrument.noteOf(hanja) }
    val tasksTitle = stringResource(Res.string.learn_section_tasks)
    val fingeringMore: @Composable (StringResource, Blow) -> Unit = { label, blow ->
        MoreLink(stringResource(label), onClick = { actions.onOpenFingering(blow) })
    }
    val grid = columns.grid
    val taskImageMaxWidth = if (columns.wide) 560.dp else Dp.Unspecified
    // 할 일 박스를 다 마치면 설명 단계(1·2단계)는 '완료했어요!'를 띄우고, 연습 단계(3~7단계)는 마지막 연습 박스가 펼쳐진다(S15P31E102-688).
    val onAllDone = { if (stage.primaryAction == LearnStageAction.Confirm) tour.finish() }
    // 4~7단계는 처음부터 1번 박스가 펼쳐진 할 일 박스와 마지막 연습 박스만 둔다.
    if (stage.number >= 4 && hasTaskTour(stage.number) && stageTourCount(stage.number, detail, instrument) > 0) {
        LearnSection(tasksTitle) { LearnStageTour(instrument, stage.number, detail, tour, noteAudioContent, onAllDone, practice) }
        return
    }

    when (stage.number) {
        1 -> {
            LearnSection(stringResource(Res.string.learn_section_inspect), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
                LearnFirstTourFigure(instrument, tour, imageMaxWidth = if (columns.wide) 560.dp else Dp.Unspecified, onAllDone = onAllDone)
            }
        }
        // 2단계 '악기 잡기' 설명은 서버가 악기별로 채워 준다. 2·3단계 할 일도 박스 하나씩 펼쳐 따라 한다(3단계 첫 음 운지는 마지막 박스에 나온다).
        2, 3 -> {
            val tasks = detail.tasks.plain()
            val first = if (stage.number == 3) detail.pitchNames.firstOrNull()?.let(noteOf) else null
            LearnSection(tasksTitle) {
                LearnTaskTour(instrument, stage.number, tasks, tour, first, noteAudioContent, imageMaxWidth = taskImageMaxWidth, onAllDone = onAllDone, practice = practice)
            }
        }
        4 -> {
            val first = detail.pitchNames.getOrNull(0)?.let(noteOf)
            val second = detail.pitchNames.getOrNull(1)?.let(noteOf)
            LearnSection(stringResource(Res.string.learn_section_new_notes), more = { fingeringMore(Res.string.learn_more_fingering_detail, Blow.Mid) }) {
                GuideGrid(listOfNotNull(first, second), grid, horizontalGap = 12.dp, verticalGap = 10.dp, stretch = false) { _, note, cell ->
                    NoteCard(instrument, note, noteAudioContent, cell)
                }
            }
            // 두 번째 음의 운지가 있으면 따라 배우기로 보이므로, 여기는 할 일을 칸으로 만들 수 없을 때만 그린다.
            LearnSection(stringResource(Res.string.learn_section_order)) {
                LearnPatternBox { NoteChips(detail.pitchNames.mapNotNull(noteOf)) }
            }
        }
        5 -> LearnSection(stringResource(Res.string.learn_section_patterns), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnPattern.entries.forEach { pattern ->
                    LearnPatternBox {
                        PatternName(stringResource(pattern.label))
                        NoteChips(pattern.notes(detail.pitchNames).mapNotNull(noteOf))
                    }
                }
            }
        }
        6 -> {
            LearnSection(stringResource(Res.string.learn_section_range)) {
                GuideGrid(detail.ranges, grid, horizontalGap = 12.dp, verticalGap = 10.dp, stretch = false) { _, range, cell ->
                    val first = noteOf(range.first)
                    LearnPatternBox(cell) {
                        PatternName(range.label)
                        if (first != null) NoteCard(instrument, first, noteAudioContent)
                        MoreLink(
                            stringResource(Res.string.learn_more_fingering_blow, blowShortLabel(stringResource(range.blow.label))),
                            onClick = { actions.onOpenFingering(range.blow) },
                        )
                    }
                }
            }
            LearnSection(tasksTitle) {
                LearnTaskList(
                    detail.ranges.map { range ->
                        val name = noteOf(range.first)?.name ?: range.first
                        LearnTask(stringResource(Res.string.learn_task_range, blowShortLabel(range.label), name), stringResource(Res.string.learn_task_range_detail))
                    },
                    grid,
                    collapsible = true,
                )
            }
        }
        else -> LearnSection(tasksTitle) { LearnTaskList(detail.tasks.plain(), grid, collapsible = true) }
    }
    // 할 일을 박스로 만들 수 없는 4~7단계도 연습 박스 하나는 둔다.
    if (stage.number >= 4 && practice != null) {
        Spacer(Modifier.height(16.dp))
        TourPanel(emptyList(), tour, practice = practice) {}
    }
}

internal val LearnPattern.label: StringResource
    get() = when (this) {
        LearnPattern.Ascending -> Res.string.learn_pattern_ascending
        LearnPattern.Descending -> Res.string.learn_pattern_descending
    }

@Composable
internal fun NoteCard(
    instrument: GuideInstrument,
    note: FingeringNote,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    FingeringCard(instrument, note, audioContent = { noteAudioContent(note) }, modifier = modifier, inLearn = true)
}

/** 묶음 이름(`.lp-pattern .name`). */
@Composable
private fun PatternName(name: String) {
    Text(name, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp))
}

/** 서버 문구 끝의 출처·예시 표기(예: `(대금 기초편 7차시)`, `(예시)`)를 화면에서 뺀다. */
internal fun String.plain(): String = replace(CitationPattern, "").trim()

private fun List<LearnTask>.plain() = map { LearnTask(it.title.plain(), it.detail?.plain()) }

/** 할 일 이름만 남긴 목록. 시작 전·연습 화면은 설명 없이 이름만 보인다. */
internal fun List<LearnTask>.titles() = map { LearnTask(it.title.plain(), null) }

private val CitationPattern = Regex("""\s*\((?:[^()]*(?:차시|교실|지도서)[^()]*|예시)\)""")

/**
 * 하단 고정 영역(S15P31E102-688): 양옆에 이전·다음 단계로 가는 동그란 버튼, 가운데 판정 방식별 주 버튼
 * (설명 단계 '확인하고 다음 단계', 연습 단계 '연습 시작', 듣기 단계 '모범 연주 듣기').
 * 연습 단계의 '연습 시작'은 화면을 옮기지 않고 마지막 연습 박스를 바로 펼친다.
 * 할 일 박스를 모두 마치면 주 버튼이 살짝 커졌다 작아지며 다음 할 일을 알린다(연습 박스가 펼쳐져 있으면 쉰다).
 */
@Composable
internal fun LearnStageBottomBar(state: LearnUiState, stage: LearnStage, actions: LearnStageActions, tour: LearnTour) {
    val stepCount = (state.detail(stage.number) as? LearnDetailLoad.Loaded)?.detail?.let { learnStepCount(stage.number, it, state.instrument) } ?: 0
    val index = state.stages.indexOfFirst { it.number == stage.number }
    val previous = state.stages.getOrNull(index - 1)?.takeIf { index > 0 }
    val next = state.stages.getOrNull(index + 1)?.takeIf { index >= 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        StageNavButton(previous, forward = false, onOpen = actions.onOpenStage)
        val primary = Modifier.weight(1f).attention(tour.allDone(stepCount) && tour.open != stepCount)
        when (stage.primaryAction) {
            LearnStageAction.Confirm -> EolssuButton(stringResource(Res.string.learn_confirm_next), { actions.onConfirm(stage.number) }, primary)
            LearnStageAction.ListenModel -> EolssuButton(stringResource(Res.string.learn_listen_model), actions.onListenModel, primary, icon = GuideIcons.Speaker)
            LearnStageAction.Practice ->
                EolssuButton(stringResource(Res.string.learn_start_practice), { tour.jumpTo(stepCount) }, primary, icon = EolssuIcons.Mic)
        }
        StageNavButton(next, forward = true, onOpen = actions.onOpenStage)
    }
}

/** 이전·다음 단계로 가는 동그란 버튼. 갈 단계가 없으면(1단계의 이전, 마지막 단계의 다음) 옅게 두고 누를 수 없다. */
@Composable
private fun StageNavButton(target: LearnStage?, forward: Boolean, onOpen: (Int) -> Unit) {
    val colors = EolssuTheme.colors
    val label = target?.let {
        stringResource(if (forward) Res.string.learn_stage_nav_next else Res.string.learn_stage_nav_prev, it.number, it.title)
    }.orEmpty()
    Surface(
        onClick = { target?.let { onOpen(it.number) } },
        enabled = target != null,
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer { alpha = if (target != null) 1f else 0.35f }
            .semantics { contentDescription = label },
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (forward) EolssuIcons.Chevron else EolssuIcons.Back, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.text)
        }
    }
}

/** [on]이면 살짝 커졌다 작아지기를 되풀이해 눈길을 끈다. */
@Composable
private fun Modifier.attention(on: Boolean): Modifier {
    if (!on) return this
    val scale by rememberInfiniteTransition(label = "attention").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "attentionScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * '완료로 표시'·'완료 취소' 작은 버튼. 아래 막대를 이전·다음 단계 버튼에 내주고 단계 위치 줄 오른쪽으로 옮겼다(S15P31E102-688).
 * 완료면 옅은 연두 바탕으로 눌린 상태를 보인다.
 */
@Composable
private fun DoneToggleButton(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (done) colors.accentTint else colors.surface,
        border = BorderStroke(1.dp, if (done) colors.accentTintLine else colors.line),
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (done) Res.string.learn_unmark_done else Res.string.learn_mark_done),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}
