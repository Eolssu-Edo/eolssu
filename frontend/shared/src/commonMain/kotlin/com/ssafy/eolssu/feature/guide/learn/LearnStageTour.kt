package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_pattern_ascending_detail
import eolssu.shared.generated.resources.learn_pattern_descending_detail
import eolssu.shared.generated.resources.learn_task_note_detail
import eolssu.shared.generated.resources.learn_task_range_both
import eolssu.shared.generated.resources.learn_task_range_high
import eolssu.shared.generated.resources.learn_task_range_high_detail
import eolssu.shared.generated.resources.learn_task_range_low
import eolssu.shared.generated.resources.learn_task_range_low_detail
import eolssu.shared.generated.resources.learn_done_go_finish
import eolssu.shared.generated.resources.learn_tour_done_body_beat
import eolssu.shared.generated.resources.learn_tour_done_body_notes
import eolssu.shared.generated.resources.learn_tour_done_body_patterns
import eolssu.shared.generated.resources.learn_tour_done_body_range
import eolssu.shared.generated.resources.learn_tour_done_next
import eolssu.shared.generated.resources.learn_tour_tongue
import eolssu.shared.generated.resources.learn_tour_tongue_figure
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * 4~7단계 따라 배우기(S15P31E102-626). 2·3단계([LearnTaskTour])처럼 할 일 박스를 하나씩 펼쳐 그림과 한 줄 설명을 보이고(S15P31E102-688),
 * 모두 마치면 아래 '연습 시작'으로 단계 연습에 간다.
 * 4·5·6단계는 서버 할 일이 비어 있어 단계 상세가 보이던 음(4단계)·패턴(5단계)·음역(6단계)을 칸으로 쓰고, 7단계는 서버 할 일(혀치기)을 그대로 쓴다.
 * 율명을 처음 보는 사람도 따라 하도록 4단계는 음마다 운지 카드 칸을 두고, 5단계는 음 순서를 운지 그림과 함께 한 줄씩 보인다(S15P31E102-688).
 * 8·9단계(악보와 곡·시김새)는 곡 연습·정간보 쉽게 읽기와 겹쳐 서버가 숨긴다(S15P31E102-657·658).
 * 6단계 음계 오르내리기는 5단계와 반복되어 두지 않는다(S15P31E102-655).
 */

/** 4~7단계 따라 배우기 칸 수. 칸을 만들 수 없으면(내용이 모자람) 0이고, 단계 상세는 따라 배우기 없이 보인다. */
internal fun stageTourCount(stage: Int, detail: LearnStageDetail, instrument: GuideInstrument): Int = when (stage) {
    4 -> stageFourNotes(detail, instrument).size
    5 -> if (detail.pitchNames.size >= 5) LearnPattern.entries.size else 0
    // 새 음역은 박스 하나에서 음역마다 첫 음을 차례로 낸다(S15P31E102-749).
    6 -> if (detail.ranges.isEmpty()) 0 else 1
    else -> detail.tasks.size
}

/** 4단계에서 하나씩 짚어 볼 음. 운지표에 없는 음은 뺀다. */
internal fun stageFourNotes(detail: LearnStageDetail, instrument: GuideInstrument): List<FingeringNote> =
    detail.pitchNames.mapNotNull(instrument::noteOf)

/** 단계를 완료했을 때 '완료했어요!' 창의 한 줄. 7단계(마지막 단계)는 곡 연습으로 이어지는 안내를 붙인다. */
internal fun learnDoneBody(stage: Int): StringResource = when (stage) {
    4 -> Res.string.learn_tour_done_body_notes
    5 -> Res.string.learn_tour_done_body_patterns
    6 -> Res.string.learn_tour_done_body_range
    else -> Res.string.learn_tour_done_body_beat
}

/** '완료했어요!' 창의 버튼. 마지막 단계는 다음 단계 대신 곡 연습으로 간다. */
internal fun learnDoneAction(last: Boolean): StringResource =
    if (last) Res.string.learn_done_go_finish else Res.string.learn_tour_done_next

private val LearnPattern.detail: StringResource
    get() = when (this) {
        LearnPattern.Ascending -> Res.string.learn_pattern_ascending_detail
        LearnPattern.Descending -> Res.string.learn_pattern_descending_detail
    }

/** 4~7단계 할 일 박스. 펼친 박스에 그 칸의 그림(음마다 운지 카드 · 음 순서와 운지 · 혀치기)을 보인다. */
@Composable
internal fun LearnStageTour(
    instrument: GuideInstrument,
    stage: Int,
    detail: LearnStageDetail,
    tour: LearnTour,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    onAllDone: () -> Unit = {},
    practice: StepPractice? = null,
) {
    val noteOf = { hanja: String -> instrument.noteOf(hanja) }
    val tasks = stageTourTasks(instrument, stage, detail)
    if (tasks.isEmpty()) return
    val noteCard: @Composable (FingeringNote?) -> Unit = { note ->
        if (note != null) NoteCard(instrument, note, noteAudioContent, Modifier.widthIn(max = 360.dp)) else StageArtFigure(stage)
    }
    TourPanel(tasks, tour, onAllDone, practice) { s ->
        when (stage) {
            4 -> noteCard(stageFourNotes(detail, instrument).getOrNull(s))
            5 -> LearnScoreColumn(instrument, LearnPattern.entries[s].notes(detail.pitchNames).mapNotNull(noteOf))
            6 -> RangeNotes(detail.ranges.mapNotNull { noteOf(it.first) }, noteCard)
            else -> if (s == 0) TongueFigure() else StageArtFigure(stage)
        }
    }
}

/** 4~7단계 칸의 이름과 한 줄 설명. 4·6단계는 단계 상세가 보이던 할 일에서 앞 단계와 반복되는 칸을 뺀 문구다. */
@Composable
internal fun stageTourTasks(instrument: GuideInstrument, stage: Int, detail: LearnStageDetail): List<LearnTask> {
    if (stageTourCount(stage, detail, instrument) == 0) return emptyList()
    return when (stage) {
        // 율명만 제목으로 두고 설명은 운지 그림 읽는 법 한 줄만 둔다(S15P31E102-749).
        4 -> stageFourNotes(detail, instrument).map { note -> LearnTask(note.name, stringResource(Res.string.learn_task_note_detail)) }
        5 -> LearnPattern.entries.map { LearnTask(stringResource(it.label), stringResource(it.detail)) }
        6 -> listOf(rangeTask(detail.ranges))
        else -> detail.tasks.map { LearnTask(it.title.plain(), it.detail?.plain()) }
    }
}

/**
 * 6단계 박스의 이름과 설명. '역취'·'저취' 같은 말을 몰라도 따라 하도록 입김을 세게·약하게 부는 말로 쓴다(S15P31E102-749).
 * 높은 음역만 있으면 높은 음, 낮은 음역만 있으면 낮은 음, 둘 다 있으면 두 줄 설명이다.
 */
@Composable
internal fun rangeTask(ranges: List<LearnRange>): LearnTask {
    val high = ranges.any { it.blow == Blow.High }
    val low = ranges.any { it.blow == Blow.Low }
    val highLine = stringResource(Res.string.learn_task_range_high_detail)
    val lowLine = stringResource(Res.string.learn_task_range_low_detail)
    return when {
        high && low -> LearnTask(stringResource(Res.string.learn_task_range_both), listOf(highLine, lowLine).joinToString("\n"))
        low -> LearnTask(stringResource(Res.string.learn_task_range_low), lowLine)
        else -> LearnTask(stringResource(Res.string.learn_task_range_high), highLine)
    }
}

/** 6단계 새 음역 첫 음의 운지 카드들(음역이 둘이면 위아래로). */
@Composable
private fun RangeNotes(notes: List<FingeringNote>, noteCard: @Composable (FingeringNote?) -> Unit) {
    if (notes.isEmpty()) {
        noteCard(null)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        notes.forEach { noteCard(it) }
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
