package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.FingeringNote
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.MoreSigimsaeCount
import com.ssafy.eolssu.feature.guide.component.BorrowCard
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideIcons
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
import com.ssafy.eolssu.feature.guide.component.image
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_borrow_length
import eolssu.shared.generated.resources.learn_borrow_order
import eolssu.shared.generated.resources.learn_borrow_sigimsae
import eolssu.shared.generated.resources.learn_confirm_next
import eolssu.shared.generated.resources.learn_first_sound_note
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
import eolssu.shared.generated.resources.learn_source
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

/** 단계 상세의 동작. 화면 이동은 Route가 이어 준다. */
class LearnStageActions(
    val onBack: () -> Unit,
    val onOpenStage: (Int) -> Unit,
    val onToggleDone: (Int) -> Unit,
    val onConfirm: (Int) -> Unit,
    val onStartPractice: (Int) -> Unit,
    val onListenModel: () -> Unit,
    val onListen: (FingeringNote) -> Unit,
    val onOpenFingering: (Blow) -> Unit,
    val onOpenReading: () -> Unit,
    val onOpenSong: (LearnSong) -> Unit,
)

/** LEARN-02 단계 상세. 목표 · 단계별 내용 · 통과 기준 · 근거 · 이전/다음 단계, 아래에 완료 표시와 판정 방식별 버튼. */
@Composable
fun LearnStageScreen(
    state: LearnUiState,
    stage: LearnStage,
    snackbarHostState: SnackbarHostState,
    actions: LearnStageActions,
) {
    val instrumentName = stringResource(state.instrument.label)
    EolssuScaffold(
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.learn_stage_title, stage.number, stage.title),
                onBack = actions.onBack,
                subtitle = if (stage.optional) stringResource(Res.string.learn_stage_subtitle_optional, instrumentName) else instrumentName,
            )
        },
        bottomBar = { StageBottomBar(stage, state.isDone(stage.number), actions) },
        snackbarHostState = snackbarHostState,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            GoalCard(stage)
            StageBody(state, stage, actions)
            stage.pass?.let { PassSection(stage, it) }
            // 바로 위 묶음의 아래 여백(16dp)이 와이어프레임의 위 여백(14dp)과 겹쳐 따로 띄우지 않는다.
            Text(
                stringResource(Res.string.learn_source, stage.source),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 17.sp),
                color = EolssuTheme.colors.textMuted,
            )
            StageNav(stage, actions.onOpenStage)
        }
    }
}

@Composable
private fun GoalCard(stage: LearnStage) {
    val colors = EolssuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp)
            .background(colors.surface, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.learn_goal), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
            JudgeTag(stringResource(stage.judge.label), stage.judge)
        }
        Text(stage.goal, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp))
        Text(
            stage.why,
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textMuted,
        )
    }
}

@Composable
private fun StageBody(state: LearnUiState, stage: LearnStage, actions: LearnStageActions) {
    val instrument = state.instrument
    val content = instrument.learnContent
    val noteOf = { hanja: String -> instrument.noteOf(hanja) }
    val tasksTitle = stringResource(Res.string.learn_section_tasks)
    val fingeringMore: @Composable (StringResource, Blow) -> Unit = { label, blow ->
        MoreLink(stringResource(label), onClick = { actions.onOpenFingering(blow) })
    }
    val readingMore: @Composable () -> Unit = { MoreLink(stringResource(Res.string.learn_more_reading), onClick = actions.onOpenReading) }

    when (stage.number) {
        1 -> {
            LearnSection(stringResource(Res.string.learn_section_inspect), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
                InstrumentFigure(instrument, content.parts)
            }
            LearnSection(tasksTitle) { LearnTaskList(stage.tasks) }
        }
        2 -> LearnSection(tasksTitle) {
            LearnTaskList(stage.tasks.map { if (it.title == HOLD_TASK_TITLE) it.copy(detail = content.hold) else it })
        }
        3 -> {
            LearnSection(tasksTitle) { LearnTaskList(stage.tasks) }
            val first = noteOf(content.basic.first())
            if (first != null) {
                LearnSection(stringResource(Res.string.learn_section_first_sound)) {
                    LearnNote(stringResource(Res.string.learn_first_sound_note, first.name, first.reading))
                }
            }
        }
        4 -> {
            val first = noteOf(content.basic[0])
            val second = noteOf(content.basic[1])
            LearnSection(stringResource(Res.string.learn_section_new_notes), more = { fingeringMore(Res.string.learn_more_fingering_detail, Blow.Mid) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOfNotNull(first, second).forEach { NoteCard(instrument, it, state.playing, actions.onListen) }
                }
            }
            LearnSection(stringResource(Res.string.learn_section_order)) {
                LearnPatternBox {
                    NoteChips(content.basic.mapNotNull(noteOf))
                    LearnNote(content.basicNote, caution = content.basicCaution?.let { "※ $it" })
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
                    )
                }
            }
        }
        5 -> LearnSection(stringResource(Res.string.learn_section_patterns), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnPattern.entries.forEach { pattern ->
                    LearnPatternBox {
                        PatternName(stringResource(pattern.label), pattern.note?.let { stringResource(it) })
                        NoteChips(pattern.notes(content.scale).mapNotNull(noteOf))
                    }
                }
            }
        }
        6 -> {
            LearnSection(stringResource(Res.string.learn_section_range)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content.ranges.forEach { range ->
                        val first = noteOf(range.first)
                        LearnPatternBox {
                            PatternName(range.label, range.description)
                            if (first != null) NoteCard(instrument, first, state.playing, actions.onListen)
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
            }
            LearnSection(tasksTitle) {
                LearnTaskList(
                    content.ranges.flatMap { range ->
                        val blowName = blowShortLabel(range.label)
                        val name = noteOf(range.first)?.name ?: range.first
                        listOf(
                            LearnTask(stringResource(Res.string.learn_task_range, blowName, name), stringResource(Res.string.learn_task_range_detail)),
                            LearnTask(stringResource(Res.string.learn_task_range_scale, blowName), stringResource(Res.string.learn_task_range_scale_detail)),
                        )
                    },
                )
            }
        }
        7 -> {
            LearnSection(tasksTitle) { LearnTaskList(stage.tasks) }
            LearnSection(stringResource(Res.string.learn_section_beat), more = readingMore) {
                BorrowCard(stringResource(Res.string.learn_borrow_length)) { LengthRow() }
            }
        }
        8 -> {
            LearnSection(stringResource(Res.string.learn_section_read_order), more = readingMore) {
                BorrowCard(stringResource(Res.string.learn_borrow_order)) { ReadOrderBoard() }
            }
            LearnSection(stringResource(Res.string.learn_section_scale), more = { fingeringMore(Res.string.learn_more_fingering, Blow.Mid) }) {
                LearnPatternBox {
                    NoteChips(content.scale.mapNotNull(noteOf))
                    LearnSmallNote(stringResource(Res.string.learn_scale_note))
                }
            }
            LearnSection(stringResource(Res.string.learn_section_songs)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LearnSongs.forEach { song -> SongRow(song) { actions.onOpenSong(song) } }
                }
                LearnSmallNote(stringResource(Res.string.learn_songs_note), Modifier.padding(top = 8.dp))
            }
            LearnSection(tasksTitle) { LearnTaskList(stage.tasks) }
        }
        9 -> {
            LearnSection(tasksTitle) { LearnTaskList(stage.tasks) }
            LearnSection(stringResource(Res.string.learn_section_sigimsae), more = readingMore) {
                BorrowCard(stringResource(Res.string.learn_borrow_sigimsae, MoreSigimsaeCount)) { SigimsaeBasicBlock() }
            }
            LearnSection(stringResource(Res.string.learn_section_listen)) {
                LearnNote(stringResource(Res.string.learn_listen_note))
            }
        }
    }
}

private val LearnPattern.label: StringResource
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
private fun NoteCard(instrument: GuideInstrument, note: FingeringNote, playing: String?, onListen: (FingeringNote) -> Unit) {
    FingeringCard(instrument, note, playing = playing == note.hanja, onListen = { onListen(note) }, inLearn = true)
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

/** 1단계 악기 그림과 부분 이름(`.lp-inst-fig`). */
@Composable
private fun InstrumentFigure(instrument: GuideInstrument, parts: List<String>) {
    val colors = EolssuTheme.colors
    Column(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painterResource(instrument.image),
            contentDescription = stringResource(instrument.label),
            modifier = Modifier.fillMaxWidth().aspectRatio(instrument.imageWidth.toFloat() / instrument.imageHeight),
            contentScale = ContentScale.Fit,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { part ->
                Box(
                    Modifier
                        .height(26.dp)
                        .background(colors.background, RoundedCornerShape(13.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(13.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(part, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                }
            }
        }
    }
}

/** 8단계 연습할 곡 한 줄(`.lp-song`). */
@Composable
private fun SongRow(song: LearnSong, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Text(song.meta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.lineStrong)
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
                .background(colors.background, RoundedCornerShape(14.dp))
                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(pass.rule, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(pass.note, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = colors.textMuted)
            Text(
                stringResource(Res.string.learn_pass_example, stringResource(stage.judge.label)),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.lineStrong,
            )
        }
    }
}

/** 이전·다음 단계 버튼(`.lp-nav`). 처음·마지막에서는 흐리게 막는다. */
@Composable
private fun StageNav(stage: LearnStage, onOpenStage: (Int) -> Unit) {
    val prev = learnStageOf(stage.number - 1)
    val next = learnStageOf(stage.number + 1)
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
        color = colors.background,
        border = BorderStroke(1.dp, colors.line),
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
private fun StageBottomBar(stage: LearnStage, done: Boolean, actions: LearnStageActions) {
    val colors = EolssuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DoneToggleButton(done, onClick = { actions.onToggleDone(stage.number) }, modifier = Modifier.weight(1f))
        val primary = Modifier.weight(1f)
        when (stage.judge) {
            LearnJudge.Read -> EolssuButton(stringResource(Res.string.learn_confirm_next), { actions.onConfirm(stage.number) }, primary)
            LearnJudge.Listen -> EolssuButton(stringResource(Res.string.learn_listen_model), actions.onListenModel, primary, icon = GuideIcons.Speaker)
            LearnJudge.Pitch, LearnJudge.Beat ->
                EolssuButton(stringResource(Res.string.learn_start_practice), { actions.onStartPractice(stage.number) }, primary, icon = EolssuIcons.Mic)
        }
    }
    val hint = when {
        stage.judge == LearnJudge.Read -> stringResource(Res.string.learn_hint_read)
        stage.judge == LearnJudge.Listen -> stringResource(Res.string.learn_hint_listen)
        stage.number == 8 -> stringResource(Res.string.learn_hint_song)
        else -> stringResource(Res.string.learn_hint_practice, stringResource(stage.judge.label))
    }
    Text(
        hint,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

/** '완료로 표시'·'완료 취소'(`.btn.secondary`, 완료면 `.done-on` 회색 바탕). */
@Composable
private fun DoneToggleButton(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        color = if (done) colors.surface else colors.background,
        border = BorderStroke(1.dp, if (done) colors.surface else colors.line),
    ) {
        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (done) Res.string.learn_unmark_done else Res.string.learn_mark_done),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}
