package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.guide.component.BorrowCard
import com.ssafy.eolssu.feature.guide.component.LearnPatternBox
import com.ssafy.eolssu.feature.guide.component.NoteChips
import com.ssafy.eolssu.feature.guide.component.ReadOrderBoard
import com.ssafy.eolssu.feature.guide.component.blowShortLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_pattern_ascending_detail
import eolssu.shared.generated.resources.learn_pattern_descending_detail
import eolssu.shared.generated.resources.learn_task_move
import eolssu.shared.generated.resources.learn_task_move_detail
import eolssu.shared.generated.resources.learn_task_range
import eolssu.shared.generated.resources.learn_task_range_detail
import eolssu.shared.generated.resources.learn_task_rest
import eolssu.shared.generated.resources.learn_task_rest_detail
import eolssu.shared.generated.resources.learn_tour_done_body_beat
import eolssu.shared.generated.resources.learn_tour_done_body_notes
import eolssu.shared.generated.resources.learn_tour_done_body_patterns
import eolssu.shared.generated.resources.learn_tour_done_body_range
import eolssu.shared.generated.resources.learn_tour_done_body_song
import eolssu.shared.generated.resources.learn_tour_split_figure
import eolssu.shared.generated.resources.learn_tour_tongue
import eolssu.shared.generated.resources.learn_tour_tongue_figure
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * 4~8단계 따라 배우기(S15P31E102-626). 2·3단계([LearnTaskTour])처럼 단계 상세에서 '학습 시작'을 누르면 할 일을 한 칸씩 넘기며
 * 그림과 한 줄 설명을 보이고, 마지막 칸의 '연습 시작'으로 단계 연습에 간다. 8단계는 마지막 칸의 곡을 눌러 평가 없이
 * 운지법과 함께 연습하고, '다 익혔어요'로 스스로 마친다.
 * 4·5·6단계는 서버 할 일이 비어 있어 단계 상세가 보이던 할 일(4·6단계)·패턴(5단계)을 칸으로 쓰고, 7·8단계는 서버 할 일을 그대로 쓴다.
 * 앞 단계와 반복되는 칸은 두지 않는다(S15P31E102-655): 4단계 첫 음(3단계 첫 소리와 같은 음), 6단계 음계 오르내리기.
 */

/** 4~8단계 따라 배우기 칸 수. 칸을 만들 수 없으면(내용이 모자람) 0이고, 단계 상세는 따라 배우기 없이 보인다. */
internal fun stageTourCount(stage: Int, detail: LearnStageDetail, instrument: GuideInstrument): Int = when (stage) {
    4 -> if (detail.pitchNames.getOrNull(1)?.let(instrument::noteOf) != null) 2 else 0
    5 -> if (detail.pitchNames.size >= 5) LearnPattern.entries.size else 0
    6 -> detail.ranges.size
    else -> detail.tasks.size
}

/** 7단계 [index]번째 서버 할 일(혀치기 · 1정간을 2로 · 3으로)에 보일 박 나누기 수. 혀치기·그 밖은 null. */
internal fun beatSplitOf(index: Int): Int? = when (index) {
    1 -> 2
    2 -> 3
    else -> null
}

/** 단계를 완료했을 때 '완료했어요!' 창의 한 줄. */
internal fun learnDoneBody(stage: Int): StringResource = when (stage) {
    4 -> Res.string.learn_tour_done_body_notes
    5 -> Res.string.learn_tour_done_body_patterns
    6 -> Res.string.learn_tour_done_body_range
    7 -> Res.string.learn_tour_done_body_beat
    else -> Res.string.learn_tour_done_body_song
}

private val LearnPattern.detail: StringResource
    get() = when (this) {
        LearnPattern.Ascending -> Res.string.learn_pattern_ascending_detail
        LearnPattern.Descending -> Res.string.learn_pattern_descending_detail
    }

/**
 * 4~8단계 따라 배우기 진행 중 판. 시작 전에는 그리지 않는다(단계 상세가 기존 내용을 보인다).
 *
 * @param songs 8단계 '장단 반주에 맞춰 곡 전체 불기' 칸에 보일 연습할 곡(받는 중·실패 포함).
 */
@Composable
internal fun LearnStageTour(
    instrument: GuideInstrument,
    stage: Int,
    detail: LearnStageDetail,
    tour: LearnTour,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    songs: @Composable () -> Unit,
) {
    val noteOf = { hanja: String -> instrument.noteOf(hanja) }
    val tasks = stageTourTasks(instrument, stage, detail)
    if (tasks.isEmpty()) return
    val step = tour.step?.coerceIn(0, tasks.lastIndex) ?: return
    val noteCard: @Composable (FingeringNote?) -> Unit = { note ->
        if (note != null) NoteCard(instrument, note, noteAudioContent, Modifier.widthIn(max = 360.dp)) else StageArtFigure(stage)
    }
    TourPanel(tasks, step) { s ->
        when (stage) {
            4 -> if (s == 0) noteCard(detail.pitchNames.getOrNull(1)?.let(noteOf)) else LearnPatternBox { NoteChips(detail.pitchNames.mapNotNull(noteOf)) }
            5 -> LearnPatternBox { NoteChips(LearnPattern.entries[s].notes(detail.pitchNames).mapNotNull(noteOf)) }
            6 -> noteCard(detail.ranges.getOrNull(s)?.first?.let(noteOf))
            7 -> {
                val split = beatSplitOf(s)
                if (s == 0) TongueFigure() else if (split != null) SplitFigure(split) else StageArtFigure(stage)
            }
            else -> when (s) {
                0 -> BorrowCard { ReadOrderBoard() }
                1 -> LearnPatternBox { NoteChips(detail.pitchNames.mapNotNull(noteOf)) }
                2 -> Box(Modifier.fillMaxWidth()) { songs() }
                else -> StageArtFigure(stage)
            }
        }
    }
}

/** 4~8단계 칸의 이름과 한 줄 설명. 4·6단계는 단계 상세가 보이던 할 일에서 앞 단계와 반복되는 칸을 뺀 문구다. */
@Composable
internal fun stageTourTasks(instrument: GuideInstrument, stage: Int, detail: LearnStageDetail): List<LearnTask> {
    if (stageTourCount(stage, detail, instrument) == 0) return emptyList()
    return when (stage) {
        4 -> {
            val second = instrument.noteOf(detail.pitchNames[1]) ?: return emptyList()
            listOf(
                LearnTask(stringResource(Res.string.learn_task_move, second.name), stringResource(Res.string.learn_task_move_detail)),
                LearnTask(stringResource(Res.string.learn_task_rest), stringResource(Res.string.learn_task_rest_detail)),
            )
        }
        5 -> LearnPattern.entries.map { LearnTask(stringResource(it.label), stringResource(it.detail)) }
        6 -> detail.ranges.map { range ->
            val name = instrument.noteOf(range.first)?.name ?: range.first
            LearnTask(stringResource(Res.string.learn_task_range, blowShortLabel(range.label), name), stringResource(Res.string.learn_task_range_detail))
        }
        else -> detail.tasks.map { LearnTask(it.title.plain(), it.detail?.plain()) }
    }
}

/** 혀치기: 같은 음을 '투'로 네 번 끊어 내는 동그라미 넷. */
@Composable
private fun TongueFigure() {
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.learn_tour_tongue_figure)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) {
            Box(
                Modifier.size(52.dp).background(colors.accentSoft, CircleShape).border(2.dp, colors.play, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.learn_tour_tongue),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.accentText,
                )
            }
        }
    }
}

/** 박 나누기: 정간 한 칸을 [parts]개로 고르게 나누고 칸마다 '투'를 둔다. */
@Composable
private fun SplitFigure(parts: Int) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val description = stringResource(Res.string.learn_tour_split_figure, parts)
    Column(
        Modifier
            .width(96.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, colors.lineStrong, shape)
            .semantics { contentDescription = description },
    ) {
        repeat(parts) { i ->
            if (i > 0) HorizontalDivider(color = colors.line)
            Box(Modifier.fillMaxWidth().height(144.dp / parts), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.learn_tour_tongue),
                    Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
