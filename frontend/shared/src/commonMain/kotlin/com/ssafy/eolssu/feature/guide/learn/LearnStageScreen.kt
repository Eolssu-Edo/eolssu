package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.ssafy.eolssu.feature.guide.MoreSigimsaeCount
import com.ssafy.eolssu.feature.guide.ReadingPage
import com.ssafy.eolssu.feature.guide.component.BorrowCard
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideFaint
import com.ssafy.eolssu.feature.guide.component.GuideGrid
import com.ssafy.eolssu.feature.guide.component.GuideIcons
import com.ssafy.eolssu.feature.guide.component.GuideTabletBottomContent
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.JudgeTag
import com.ssafy.eolssu.feature.guide.component.LearnNote
import com.ssafy.eolssu.feature.guide.component.LearnPatternBox
import com.ssafy.eolssu.feature.guide.component.LearnSection
import com.ssafy.eolssu.feature.guide.component.LearnSmallNote
import com.ssafy.eolssu.feature.guide.component.LearnTaskList
import com.ssafy.eolssu.feature.guide.component.LengthRow
import com.ssafy.eolssu.feature.guide.component.MoreLink
import com.ssafy.eolssu.feature.guide.component.NoteChips
import com.ssafy.eolssu.feature.guide.component.ReadOrderBoard
import com.ssafy.eolssu.feature.guide.component.SigimsaeBasicBlock
import com.ssafy.eolssu.feature.guide.component.blowShortLabel
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.core.model.fingering.image
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.catalog_consumer_loading
import eolssu.shared.generated.resources.catalog_consumer_failed
import eolssu.shared.generated.resources.catalog_consumer_retry
import eolssu.shared.generated.resources.learn_borrow_length
import eolssu.shared.generated.resources.learn_borrow_order
import eolssu.shared.generated.resources.learn_borrow_sigimsae
import eolssu.shared.generated.resources.learn_confirm_next
import eolssu.shared.generated.resources.learn_detail_failed
import eolssu.shared.generated.resources.learn_first_sound_note
import eolssu.shared.generated.resources.learn_first_step_guide
import eolssu.shared.generated.resources.learn_goal
import eolssu.shared.generated.resources.learn_hint_listen
import eolssu.shared.generated.resources.learn_hint_practice
import eolssu.shared.generated.resources.learn_hint_read
import eolssu.shared.generated.resources.learn_hint_song
import eolssu.shared.generated.resources.learn_listen_model
import eolssu.shared.generated.resources.learn_listen_note
import eolssu.shared.generated.resources.learn_mark_done
import eolssu.shared.generated.resources.learn_more_fingering
import eolssu.shared.generated.resources.learn_more_fingering_blow
import eolssu.shared.generated.resources.learn_more_fingering_detail
import eolssu.shared.generated.resources.learn_more_reading
import eolssu.shared.generated.resources.learn_nav_first
import eolssu.shared.generated.resources.learn_nav_last
import eolssu.shared.generated.resources.learn_nav_next
import eolssu.shared.generated.resources.learn_nav_prev
import eolssu.shared.generated.resources.learn_pass_example
import eolssu.shared.generated.resources.learn_pattern_ascending
import eolssu.shared.generated.resources.learn_pattern_ascending_note
import eolssu.shared.generated.resources.learn_pattern_descending
import eolssu.shared.generated.resources.learn_pattern_descending_note
import eolssu.shared.generated.resources.learn_pattern_skip_one
import eolssu.shared.generated.resources.learn_pattern_skip_two
import eolssu.shared.generated.resources.learn_pattern_three_note_song
import eolssu.shared.generated.resources.learn_pattern_three_note_song_note
import eolssu.shared.generated.resources.learn_range_first
import eolssu.shared.generated.resources.learn_scale_note
import eolssu.shared.generated.resources.learn_section_beat
import eolssu.shared.generated.resources.learn_section_first_sound
import eolssu.shared.generated.resources.learn_section_inspect
import eolssu.shared.generated.resources.learn_section_listen
import eolssu.shared.generated.resources.learn_section_new_notes
import eolssu.shared.generated.resources.learn_section_order
import eolssu.shared.generated.resources.learn_section_pass
import eolssu.shared.generated.resources.learn_section_patterns
import eolssu.shared.generated.resources.learn_section_range
import eolssu.shared.generated.resources.learn_section_read_order
import eolssu.shared.generated.resources.learn_section_scale
import eolssu.shared.generated.resources.learn_section_sigimsae
import eolssu.shared.generated.resources.learn_section_songs
import eolssu.shared.generated.resources.learn_section_tasks
import eolssu.shared.generated.resources.learn_songs_note
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
import eolssu.shared.generated.resources.learn_unmark_done
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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
 * 단계 상세 본문의 칸 수. 휴대폰은 모두 한 칸, 태블릿은 운지 카드·할 일 2칸 · 곡 3칸,
 * 시김새 목록은 태블릿 세로 2칸 · 가로 3칸(와이어프레임 `.tabapp .learn`).
 */
internal data class LearnStageColumns(val grid: Int, val songs: Int, val sigimsae: Int) {
    val wide: Boolean get() = grid > 1

    companion object {
        val Compact = LearnStageColumns(grid = 1, songs = 1, sigimsae = 1)
        val Portrait = LearnStageColumns(grid = 2, songs = 3, sigimsae = 2)
        val Landscape = LearnStageColumns(grid = 2, songs = 3, sigimsae = 3)
    }
}

/**
 * LEARN-02 단계 상세. 목표 · 단계별 내용 · 통과 기준 · 근거 · 이전/다음 단계, 아래에 완료 표시와 판정 방식별 버튼.
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
    EolssuScaffold(
        topBar = {
            GuideTopBar(learnStageTitle(stage), onBack = actions.onBack, widthClass = widthClass, subtitle = learnStageSubtitle(state, stage))
        },
        bottomBar = {
            if (tablet) GuideTabletBottomContent { LearnStageBottomBar(stage, state.isDone(stage.number), actions) }
            else LearnStageBottomBar(stage, state.isDone(stage.number), actions)
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
) {
    Column(
        modifier
            .verticalScroll(scrollState)
            .padding(contentPadding),
    ) {
        when (val load = state.detail(stage.number)) {
            is LearnDetailLoad.Loaded -> {
                val detail = load.detail
                GoalCard(stage, detail)
                StageBody(state, stage, detail, actions, columns, noteAudioContent)
                detail.pass?.let { PassSection(stage, it) }
            }
            else -> LearnLoadStatus(
                failed = load == LearnDetailLoad.Failed,
                failedMessage = Res.string.learn_detail_failed,
                onRetry = { actions.onRetryDetail(stage.number) },
            )
        }
        StageNav(stage, state, actions.onOpenStage)
    }
}

@Composable
private fun GoalCard(stage: LearnStage, detail: LearnStageDetail) {
    val colors = EolssuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp)
            .background(colors.surface, MaterialTheme.shapes.medium)
            .border(1.dp, colors.lineSoft, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.learn_goal), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
            JudgeTag(stringResource(stage.judge.label), stage.judge)
        }
        // `.lp-goal p`: 본문 글꼴 굵게 16(제목 글꼴 아님)
        Text(detail.goal, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold))
        if (stage.number != 1) {
            Text(
                detail.why,
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.textMuted,
            )
        }
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
) {
    val instrument = state.instrument
    val noteOf = { hanja: String -> instrument.noteOf(hanja) }
    val tasksTitle = stringResource(Res.string.learn_section_tasks)
    val fingeringMore: @Composable (StringResource, Blow) -> Unit = { label, blow ->
        MoreLink(stringResource(label), onClick = { actions.onOpenFingering(blow) })
    }
    val readingMore: (ReadingPage) -> @Composable () -> Unit = { page -> { MoreLink(stringResource(Res.string.learn_more_reading), onClick = { actions.onOpenReading(page) }) } }
    val grid = columns.grid

    when (stage.number) {
        1 -> {
            LearnNote(stringResource(Res.string.learn_first_step_guide), Modifier.padding(bottom = 14.dp))
            LearnSection(stringResource(Res.string.learn_section_inspect), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
                InstrumentFigure(instrument, detail.parts, imageMaxWidth = if (columns.wide) 560.dp else Dp.Unspecified)
            }
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks.take(1), grid) }
        }
        // 2단계 '악기 잡기' 설명은 서버가 악기별로 채워 준다.
        2 -> LearnSection(tasksTitle) { LearnTaskList(detail.tasks, grid) }
        3 -> {
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks, grid) }
            val first = detail.pitchNames.firstOrNull()?.let(noteOf)
            if (first != null) {
                LearnSection(stringResource(Res.string.learn_section_first_sound)) {
                    LearnNote(stringResource(Res.string.learn_first_sound_note, first.name, first.reading))
                }
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
            LearnSection(stringResource(Res.string.learn_section_order)) {
                LearnPatternBox {
                    NoteChips(detail.pitchNames.mapNotNull(noteOf))
                    detail.pitchNote?.let { LearnNote(it, caution = detail.pitchCaution?.let { caution -> "※ $caution" }) }
                }
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
                    )
                }
            }
        }
        5 -> LearnSection(stringResource(Res.string.learn_section_patterns), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnPattern.entries.forEach { pattern ->
                    LearnPatternBox {
                        PatternName(stringResource(pattern.label), pattern.note?.let { stringResource(it) })
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
                        PatternName(range.label, range.description)
                        if (first != null) NoteCard(instrument, first, noteAudioContent)
                        FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            LearnSmallNote(stringResource(Res.string.learn_range_first, first?.name ?: range.first) + " ")
                            MoreLink(
                                stringResource(Res.string.learn_more_fingering_blow, blowShortLabel(stringResource(range.blow.label))),
                                onClick = { actions.onOpenFingering(range.blow) },
                            )
                        }
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
                )
            }
        }
        7 -> {
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks, grid) }
            LearnSection(stringResource(Res.string.learn_section_beat), more = readingMore(ReadingPage.Length)) {
                BorrowCard(stringResource(Res.string.learn_borrow_length)) { LengthRow() }
            }
        }
        8 -> {
            LearnSection(stringResource(Res.string.learn_section_read_order), more = readingMore(ReadingPage.Order)) {
                BorrowCard(stringResource(Res.string.learn_borrow_order)) { ReadOrderBoard() }
            }
            LearnSection(stringResource(Res.string.learn_section_scale), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
                LearnPatternBox {
                    NoteChips(detail.pitchNames.mapNotNull(noteOf))
                    LearnSmallNote(stringResource(Res.string.learn_scale_note))
                }
            }
            LearnSection(stringResource(Res.string.learn_section_songs)) {
                when {
                    state.songsLoading -> Text(stringResource(Res.string.catalog_consumer_loading))
                    state.songsFailed -> {
                        Text(stringResource(Res.string.catalog_consumer_failed))
                        EolssuButton(stringResource(Res.string.catalog_consumer_retry), actions.onRetrySongs)
                    }
                    else -> GuideGrid(detail.songs, columns.songs, horizontalGap = 8.dp, verticalGap = if (columns.wide) 8.dp else 6.dp) { _, song, cell ->
                        SongRow(song, cell) { actions.onOpenSong(song) }
                    }
                }
                LearnSmallNote(stringResource(Res.string.learn_songs_note), Modifier.padding(top = 8.dp))
            }
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks, grid) }
        }
        9 -> {
            LearnSection(tasksTitle) { LearnTaskList(detail.tasks, grid) }
            LearnSection(stringResource(Res.string.learn_section_sigimsae), more = readingMore(ReadingPage.Sigimsae)) {
                BorrowCard(stringResource(Res.string.learn_borrow_sigimsae, MoreSigimsaeCount)) { SigimsaeBasicBlock(columns = columns.sigimsae) }
            }
            LearnSection(stringResource(Res.string.learn_section_listen)) {
                LearnNote(stringResource(Res.string.learn_listen_note))
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

private val LearnPattern.note: StringResource?
    get() = when (this) {
        LearnPattern.Ascending -> Res.string.learn_pattern_ascending_note
        LearnPattern.Descending -> Res.string.learn_pattern_descending_note
        LearnPattern.ThreeNoteSong -> Res.string.learn_pattern_three_note_song_note
        LearnPattern.SkipOne, LearnPattern.SkipTwo -> null
    }

@Composable
private fun NoteCard(
    instrument: GuideInstrument,
    note: FingeringNote,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    FingeringCard(instrument, note, audioContent = { noteAudioContent(note) }, modifier = modifier, inLearn = true)
}

/** 묶음 이름 + 회색 보충 설명(`.lp-pattern .name`). */
@Composable
private fun PatternName(name: String, note: String?) {
    Text(
        buildAnnotatedString {
            append(name)
            if (note != null) {
                append("  ")
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = EolssuTheme.colors.textMuted)) { append(note) }
            }
        },
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    )
}

/** 1단계 악기 그림과 부분 이름(`.lp-inst-fig`). 태블릿은 그림을 [imageMaxWidth]로 가운데에 둔다. */
@Composable
private fun InstrumentFigure(instrument: GuideInstrument, parts: List<String>, imageMaxWidth: Dp = Dp.Unspecified) {
    val colors = EolssuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painterResource(instrument.image),
            contentDescription = stringResource(instrument.label),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = imageMaxWidth)
                .fillMaxWidth()
                .aspectRatio(instrument.imageWidth.toFloat() / instrument.imageHeight),
            contentScale = ContentScale.Fit,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { part ->
                Box(
                    Modifier
                        .height(26.dp)
                        .background(colors.surface, RoundedCornerShape(13.dp))
                        .border(1.dp, colors.lineSoft, RoundedCornerShape(13.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(part, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                }
            }
        }
    }
}

/** 8단계 연습할 곡 한 줄(`.lp-song`): 흰 바탕에 옅은 테두리, 오른쪽 옅은 화살표. */
@Composable
private fun SongRow(song: LearnSong, modifier: Modifier, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Text(song.meta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = GuideFaint)
        }
    }
}

@Composable
private fun PassSection(stage: LearnStage, pass: LearnPass) {
    val colors = EolssuTheme.colors
    LearnSection(stringResource(Res.string.learn_section_pass)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(pass.rule, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(pass.note, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = colors.textMuted)
            Text(
                stringResource(Res.string.learn_pass_example, stringResource(stage.judge.label)),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = GuideFaint,
            )
        }
    }
}

/** 이전·다음 단계 버튼(`.lp-nav`). 처음·마지막에서는 흐리게 막는다. */
@Composable
private fun StageNav(stage: LearnStage, state: LearnUiState, onOpenStage: (Int) -> Unit) {
    val prev = state.stageOf(stage.number - 1)
    val next = state.stageOf(stage.number + 1)
    // 버튼마다 최대 절반 너비(와이어프레임 max-width 48%)이고 사이를 8dp 띄운다.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        NavButton(
            if (prev != null) stringResource(Res.string.learn_nav_prev, prev.number, prev.title) else stringResource(Res.string.learn_nav_first),
            enabled = prev != null,
            onClick = { prev?.let { onOpenStage(it.number) } },
            modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp),
        )
        NavButton(
            if (next != null) stringResource(Res.string.learn_nav_next, next.number, next.title) else stringResource(Res.string.learn_nav_last),
            enabled = next != null,
            onClick = { next?.let { onOpenStage(it.number) } },
            modifier = Modifier.weight(1f, fill = false).padding(start = 4.dp),
        )
    }
}

@Composable
private fun NavButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp).alpha(if (enabled) 1f else 0.35f),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 하단 고정 영역: 완료 표시 토글과 판정 방식별 주 버튼, 아래 안내 문구. */
@Composable
internal fun LearnStageBottomBar(stage: LearnStage, done: Boolean, actions: LearnStageActions) {
    val colors = EolssuTheme.colors
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
    val hint = when (stage.hint) {
        LearnStageHint.Read -> stringResource(Res.string.learn_hint_read)
        LearnStageHint.Listen -> stringResource(Res.string.learn_hint_listen)
        LearnStageHint.Song -> stringResource(Res.string.learn_hint_song)
        LearnStageHint.Practice -> stringResource(Res.string.learn_hint_practice)
    }
    if (stage.number != 1) {
        Text(
            hint,
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
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
