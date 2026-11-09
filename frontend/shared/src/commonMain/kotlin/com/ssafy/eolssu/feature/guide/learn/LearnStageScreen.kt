package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.data.user.LEARN_SONG_STAGE
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.guide.ReadingPage
import com.ssafy.eolssu.feature.guide.component.BorrowCard
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideFaint
import com.ssafy.eolssu.feature.guide.component.GuideGrid
import com.ssafy.eolssu.feature.guide.component.GuideIcons
import com.ssafy.eolssu.feature.guide.component.GuideTabletBottomContent
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.LearnPatternBox
import com.ssafy.eolssu.feature.guide.component.LearnSection
import com.ssafy.eolssu.feature.guide.component.LearnTaskList
import com.ssafy.eolssu.feature.guide.component.LengthRow
import com.ssafy.eolssu.feature.guide.component.MoreLink
import com.ssafy.eolssu.feature.guide.component.NoteChips
import com.ssafy.eolssu.feature.guide.component.ReadOrderBoard
import com.ssafy.eolssu.feature.guide.component.SigimsaeBasicBlock
import com.ssafy.eolssu.feature.guide.component.blowShortLabel
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_stage_position_description
import eolssu.shared.generated.resources.learn_stage_position
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import eolssu.shared.generated.resources.catalog_consumer_loading
import eolssu.shared.generated.resources.catalog_consumer_failed
import eolssu.shared.generated.resources.catalog_consumer_retry
import eolssu.shared.generated.resources.learn_confirm_next
import eolssu.shared.generated.resources.learn_detail_failed
import eolssu.shared.generated.resources.learn_listen_model
import eolssu.shared.generated.resources.learn_mark_done
import eolssu.shared.generated.resources.learn_more_fingering
import eolssu.shared.generated.resources.learn_more_fingering_blow
import eolssu.shared.generated.resources.learn_more_fingering_detail
import eolssu.shared.generated.resources.learn_more_reading
import eolssu.shared.generated.resources.learn_pattern_ascending
import eolssu.shared.generated.resources.learn_pattern_descending
import eolssu.shared.generated.resources.learn_pattern_skip_one
import eolssu.shared.generated.resources.learn_pattern_skip_two
import eolssu.shared.generated.resources.learn_pattern_three_note_song
import eolssu.shared.generated.resources.learn_section_beat
import eolssu.shared.generated.resources.learn_section_inspect
import eolssu.shared.generated.resources.learn_section_new_notes
import eolssu.shared.generated.resources.learn_section_order
import eolssu.shared.generated.resources.learn_section_patterns
import eolssu.shared.generated.resources.learn_section_range
import eolssu.shared.generated.resources.learn_section_read_order
import eolssu.shared.generated.resources.learn_section_scale
import eolssu.shared.generated.resources.learn_section_sigimsae
import eolssu.shared.generated.resources.learn_section_songs
import eolssu.shared.generated.resources.learn_section_tasks
import eolssu.shared.generated.resources.learn_stage_subtitle_optional
import eolssu.shared.generated.resources.learn_stage_title
import eolssu.shared.generated.resources.learn_start_practice
import eolssu.shared.generated.resources.learn_task_first
import eolssu.shared.generated.resources.learn_task_first_detail
import eolssu.shared.generated.resources.learn_task_move
import eolssu.shared.generated.resources.learn_task_move_detail
import eolssu.shared.generated.resources.learn_task_range
import eolssu.shared.generated.resources.learn_task_range_detail
import eolssu.shared.generated.resources.learn_task_range_scale
import eolssu.shared.generated.resources.learn_task_range_scale_detail
import eolssu.shared.generated.resources.learn_task_rest
import eolssu.shared.generated.resources.learn_task_rest_detail
import eolssu.shared.generated.resources.learn_tour_done_body_posture
import eolssu.shared.generated.resources.learn_unmark_done
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.label

/** 단계 상세의 동작. 화면 이동은 Route가 이어 준다. */
class LearnStageActions(
    val onBack: () -> Unit,
    val onOpenStage: (Int) -> Unit,
    val onToggleDone: (Int) -> Unit,
    val onConfirm: (Int) -> Unit,
    val onStartPractice: (Int) -> Unit,
    val onListenModel: () -> Unit,
    val onOpenFingering: (Blow) -> Unit,
    val onOpenReading: (ReadingPage) -> Unit,
    val onOpenSong: (LearnSong) -> Unit,
    val onRetrySongs: () -> Unit = {},
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
) {
    // 1단계 따라 배우기를 다 마치면 '완료했어요!'를 띄우고, '다음 단계로'는 '확인하고 다음 단계'와 같이 완료 표시 뒤 2단계로 간다.
    if (stage.number == 1) LearnTourDoneDialog(tour) { actions.onConfirm(stage.number) }
    // 2단계 따라 배우기도 같은 창을 띄우고 완료 표시 뒤 3단계로 간다(3단계는 마지막 칸에서 바로 '연습 시작').
    if (stage.number == 2) LearnTourDoneDialog(tour, Res.string.learn_tour_done_body_posture) { actions.onConfirm(stage.number) }
    // 8단계는 평가 없이 곡을 연습하고 돌아와 마지막 칸의 '다 익혔어요'로 스스로 마친다.
    if (stage.number == LEARN_SONG_STAGE) LearnTourDoneDialog(tour, learnDoneBody(stage.number)) { actions.onConfirm(stage.number) }
    Column(
        modifier
            .verticalScroll(scrollState)
            .padding(contentPadding),
    ) {
        when (val load = state.detail(stage.number)) {
            is LearnDetailLoad.Loaded -> {
                val detail = load.detail
                StagePosition(state, stage.number)
                StageHero(stage, detail)
                StageBody(state, stage, detail, actions, columns, noteAudioContent, tour)
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
        LearnStageArt(stage.number, Modifier.size(StageHeroArtSize).clip(shape).background(colors.background).border(1.dp, colors.lineSoft, shape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(detail.goal, style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold))
        }
    }
}

private val StageHeroArtSize = 88.dp

/**
 * 단계 안에서도 지금 몇 단계인지 보이는 작은 줄. 단계마다 점 하나를 두고 완료는 연두, 지금 단계는 길쭉한 진한 점, 나머지는 옅게 칠하며
 * 오른쪽에 'n / 전체'를 적는다. 화면 읽기에는 '전체 n단계 중 m단계'로 한 번에 읽힌다.
 */
@Composable
private fun StagePosition(state: LearnUiState, current: Int) {
    val stages = state.stages
    val index = stages.indexOfFirst { it.number == current }
    if (index < 0) return
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.learn_stage_position_description, stages.size, index + 1)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clearAndSetSemantics { contentDescription = description },
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
) {
    val instrument = state.instrument
    val noteOf = { hanja: String -> instrument.noteOf(hanja) }
    val tasksTitle = stringResource(Res.string.learn_section_tasks)
    val fingeringMore: @Composable (StringResource, Blow) -> Unit = { label, blow ->
        MoreLink(stringResource(label), onClick = { actions.onOpenFingering(blow) })
    }
    val readingMore: (ReadingPage) -> @Composable () -> Unit = { page -> { MoreLink(stringResource(Res.string.learn_more_reading), onClick = { actions.onOpenReading(page) }) } }
    val grid = columns.grid
    val taskImageMaxWidth = if (columns.wide) 560.dp else Dp.Unspecified
    // 8단계 연습할 곡. 받는 중·실패도 같은 자리에 보인다.
    val songs: @Composable () -> Unit = {
        when {
            state.songsLoading -> Text(stringResource(Res.string.catalog_consumer_loading))
            state.songsFailed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.catalog_consumer_failed))
                EolssuButton(stringResource(Res.string.catalog_consumer_retry), actions.onRetrySongs)
            }
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.songs.forEach { song -> SongChip(song) { actions.onOpenSong(song) } }
            }
        }
    }

    // 4~8단계는 시작 전에 할 일 이름만 보이고, 따라 배우는 동안은 칸마다 그림과 한 줄 설명을 보이는 판만 둔다.
    if (stage.number >= 4 && hasTaskTour(stage.number) && stageTourCount(stage.number, detail, instrument) > 0) {
        if (tour.started) {
            LearnStageTour(instrument, stage.number, detail, tour, noteAudioContent, songs)
        } else {
            LearnSection(tasksTitle) { LearnTaskList(stageTourTasks(instrument, stage.number, detail).titles(), grid) }
        }
        return
    }

    when (stage.number) {
        1 -> {
            LearnSection(stringResource(Res.string.learn_section_inspect), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
                LearnFirstTourFigure(instrument, tour, imageMaxWidth = if (columns.wide) 560.dp else Dp.Unspecified)
            }
        }
        // 2단계 '악기 잡기' 설명은 서버가 악기별로 채워 준다. 2·3단계 할 일은 '학습 시작' 뒤 한 칸씩 따라 배운다.
        // 2·3단계도 시작 전에는 할 일 이름만, 따라 배우는 동안은 따라 배우기 판만 보인다(3단계 첫 음 운지는 마지막 칸에 나온다).
        2, 3 -> {
            val tasks = detail.tasks.plain()
            val first = if (stage.number == 3) detail.pitchNames.firstOrNull()?.let(noteOf) else null
            val taskTour: @Composable () -> Unit = {
                LearnTaskTour(instrument, stage.number, tasks, tour, grid, first, noteAudioContent, imageMaxWidth = taskImageMaxWidth)
            }
            if (tour.started) taskTour() else LearnSection(tasksTitle) { taskTour() }
        }
        4 -> {
            val first = detail.pitchNames.getOrNull(0)?.let(noteOf)
            val second = detail.pitchNames.getOrNull(1)?.let(noteOf)
            LearnSection(stringResource(Res.string.learn_section_new_notes), more = { fingeringMore(Res.string.learn_more_fingering_detail, Blow.Mid) }) {
                GuideGrid(listOfNotNull(first, second), grid, horizontalGap = 12.dp, verticalGap = 10.dp, stretch = false) { _, note, cell ->
                    NoteCard(instrument, note, noteAudioContent, cell)
                }
            }
            LearnSection(stringResource(Res.string.learn_section_order)) {
                LearnPatternBox { NoteChips(detail.pitchNames.mapNotNull(noteOf)) }
            }
            if (first != null && second != null) {
                LearnSection(tasksTitle) {
                    LearnTaskList(
                        listOf(
                            LearnTask(stringResource(Res.string.learn_task_first, first.name), stringResource(Res.string.learn_task_first_detail, first.reading)),
                            LearnTask(stringResource(Res.string.learn_task_move, second.name), stringResource(Res.string.learn_task_move_detail)),
                            LearnTask(stringResource(Res.string.learn_task_rest), stringResource(Res.string.learn_task_rest_detail)),
                        ),
                        grid,
                        collapsible = true,
                    )
                }
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
                    detail.ranges.flatMap { range ->
                        val blowName = blowShortLabel(range.label)
                        val name = noteOf(range.first)?.name ?: range.first
                        listOf(
                            LearnTask(stringResource(Res.string.learn_task_range, blowName, name), stringResource(Res.string.learn_task_range_detail)),
                            LearnTask(stringResource(Res.string.learn_task_range_scale, blowName), stringResource(Res.string.learn_task_range_scale_detail)),
                        )
                    },
                    grid,
                    collapsible = true,
                )
            }
        }
        7 -> {
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks.plain(), grid, collapsible = true) }
            LearnSection(stringResource(Res.string.learn_section_beat), more = readingMore(ReadingPage.Length)) {
                BorrowCard { LengthRow() }
            }
        }
        8 -> {
            LearnSection(stringResource(Res.string.learn_section_read_order), more = readingMore(ReadingPage.Order)) {
                BorrowCard { ReadOrderBoard() }
            }
            LearnSection(stringResource(Res.string.learn_section_scale), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
                LearnPatternBox { NoteChips(detail.pitchNames.mapNotNull(noteOf)) }
            }
            LearnSection(stringResource(Res.string.learn_section_songs)) { songs() }
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks.plain(), grid, collapsible = true) }
        }
        9 -> {
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks.plain(), grid, collapsible = true) }
            LearnSection(stringResource(Res.string.learn_section_sigimsae), more = readingMore(ReadingPage.Sigimsae)) {
                BorrowCard { SigimsaeBasicBlock(columns = columns.sigimsae) }
            }
        }
    }
}

internal val LearnPattern.label: StringResource
    get() = when (this) {
        LearnPattern.Ascending -> Res.string.learn_pattern_ascending
        LearnPattern.Descending -> Res.string.learn_pattern_descending
        LearnPattern.SkipOne -> Res.string.learn_pattern_skip_one
        LearnPattern.SkipTwo -> Res.string.learn_pattern_skip_two
        LearnPattern.ThreeNoteSong -> Res.string.learn_pattern_three_note_song
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

/** 8단계 연습할 곡(`.lp-song`): 곡 이름만 둔 작은 칩. 누르면 곡 상세로 간다. */
@Composable
private fun SongChip(song: LearnSong, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Row(Modifier.height(36.dp).padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp), tint = GuideFaint)
        }
    }
}

/** 서버 문구 끝의 출처·예시 표기(예: `(대금 기초편 7차시)`, `(예시)`)를 화면에서 뺀다. */
internal fun String.plain(): String = replace(CitationPattern, "").trim()

private fun List<LearnTask>.plain() = map { LearnTask(it.title.plain(), it.detail?.plain()) }

/** 할 일 이름만 남긴 목록. 시작 전·연습 화면은 설명 없이 이름만 보인다. */
internal fun List<LearnTask>.titles() = map { LearnTask(it.title.plain(), null) }

private val CitationPattern = Regex("""\s*\((?:[^()]*(?:차시|교실|지도서)[^()]*|예시)\)""")

/**
 * 하단 고정 영역: 완료 표시 토글과 판정 방식별 주 버튼. 1~8단계는 따라 배우기 버튼(학습 시작 · 이전/다음)이다.
 * 2단계는 마지막 칸에서 '다 익혔어요', 3~8단계는 시작 전 '완료로 표시'를 두고 마지막 칸에서 '연습 시작'으로 이어진다.
 */
@Composable
internal fun LearnStageBottomBar(state: LearnUiState, stage: LearnStage, actions: LearnStageActions, tour: LearnTour) {
    if (stage.number == 1) {
        LearnFirstTourBar(state.instrument, tour)
        return
    }
    val done = state.isDone(stage.number)
    val taskCount = (state.detail(stage.number) as? LearnDetailLoad.Loaded)?.detail?.let {
        if (stage.number >= 4) stageTourCount(stage.number, it, state.instrument) else it.tasks.size
    } ?: 0
    if (hasTaskTour(stage.number) && taskCount > 0) {
        val practice = stage.primaryAction == LearnStageAction.Practice
        when {
            tour.step != null -> LearnTourBar(taskCount - 1, tour) { modifier ->
                if (practice && stage.number != LEARN_SONG_STAGE) {
                    EolssuButton(stringResource(Res.string.learn_start_practice), { actions.onStartPractice(stage.number) }, modifier, icon = EolssuIcons.Mic)
                } else {
                    LearnTourFinishButton(tour, modifier)
                }
            }
            practice -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoneToggleButton(done, onClick = { actions.onToggleDone(stage.number) }, modifier = Modifier.weight(1f))
                LearnTourStartButton(tour, Modifier.weight(1f))
            }
            else -> LearnTourStartButton(tour, Modifier.fillMaxWidth())
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (stage.primaryAction != LearnStageAction.Confirm) {
            DoneToggleButton(done, onClick = { actions.onToggleDone(stage.number) }, modifier = Modifier.weight(1f))
        }
        val primary = if (stage.primaryAction == LearnStageAction.Confirm) Modifier.fillMaxWidth() else Modifier.weight(1f)
        when (stage.primaryAction) {
            LearnStageAction.Confirm -> EolssuButton(stringResource(Res.string.learn_confirm_next), { actions.onConfirm(stage.number) }, primary)
            LearnStageAction.ListenModel -> EolssuButton(stringResource(Res.string.learn_listen_model), actions.onListenModel, primary, icon = GuideIcons.Speaker)
            LearnStageAction.Practice ->
                EolssuButton(stringResource(Res.string.learn_start_practice), { actions.onStartPractice(stage.number) }, primary, icon = EolssuIcons.Mic)
        }
    }
}

/**
 * '완료로 표시'·'완료 취소'(`.btn.secondary`: 흰 바탕, #D9D3C6 테두리, 모서리 14).
 * 보드에 완료한 모습이 없어 완료면 옅은 연두 바탕으로 눌린 상태를 보인다.
 */
@Composable
private fun DoneToggleButton(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (done) colors.accentTint else colors.surface,
        border = BorderStroke(1.dp, if (done) colors.accentTintLine else colors.line),
    ) {
        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (done) Res.string.learn_unmark_done else Res.string.learn_mark_done),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}
