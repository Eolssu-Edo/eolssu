package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.MicLevelBadge
import com.ssafy.eolssu.feature.guide.component.LearnNote
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_first_sound_pass
import eolssu.shared.generated.resources.learn_pitch_hold_pass
import eolssu.shared.generated.resources.learn_pitch_hold_pass_plain
import eolssu.shared.generated.resources.learn_practice_blocked
import eolssu.shared.generated.resources.learn_practice_denied
import eolssu.shared.generated.resources.learn_practice_failed
import eolssu.shared.generated.resources.learn_practice_open_settings
import eolssu.shared.generated.resources.learn_practice_passed_title
import eolssu.shared.generated.resources.learn_practice_pitch_note_current
import eolssu.shared.generated.resources.learn_practice_pitch_note_hit
import eolssu.shared.generated.resources.learn_practice_pitch_note_miss
import eolssu.shared.generated.resources.learn_practice_pitch_note_waiting
import eolssu.shared.generated.resources.learn_practice_restart
import eolssu.shared.generated.resources.learn_practice_self_done
import eolssu.shared.generated.resources.learn_practice_stop
import eolssu.shared.generated.resources.learn_practice_unsupported
import eolssu.shared.generated.resources.learn_range_hold_pass
import eolssu.shared.generated.resources.learn_range_hold_pass_plain
import eolssu.shared.generated.resources.learn_section_pass
import eolssu.shared.generated.resources.learn_start_practice
import eolssu.shared.generated.resources.learn_step_try
import eolssu.shared.generated.resources.learn_tongue_pass
import eolssu.shared.generated.resources.learn_tour_tongue
import org.jetbrains.compose.resources.stringResource

/** 단계 안 연습 박스의 동작. 완료 뒤 화면 이동은 Route가 이어 준다. */
class LearnPracticeActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onRestart: () -> Unit,
    val onComplete: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** 판정을 [Int]번째 음으로 옮긴다. 저장된 진행으로 뒤 박스부터 이어 할 때 쓴다. */
    val onRewind: (Int) -> Unit = {},
)

/*
 * 기초부터 배우기 3~7단계의 소리 듣기(S15P31E102-688, 정리 S15P31E102-749).
 * 박스를 펼친 뒤 아래 가운데 '연습 시작'을 누르면 듣는다(S15P31E102-793). 5단계 정간보만 마이크 입력 뱃지([MicLevelBadge])를 두고,
 * 듣는 동안 안내 문장을 바꿔 띄우지 않는다. 진행은 소리 막대·낼 음·'투' 동그라미·정간보가 채워지는 것으로만 보인다.
 * 연습 시작·그만하기·다음 단계로 같은 주 동작은 단계 화면 아래 가운데 버튼([LearnPracticePrimary])이 맡고, 박스 안에는 보조 버튼만 둔다.
 */

/**
 * 3·4·7단계의 마지막 연습 박스 내용: 통과 기준 한 줄 · 소리 듣기 판 · 보조 버튼. 3단계는 0.3초 소리 한 번, 4단계는 낼 음,
 * 7단계는 '투' 횟수를 센다. 판정하지 않는 경우(음높이 관측이 없는 기기)는 마이크 뱃지만 보이고 '스스로 확인했어요'로 마친다.
 * 박스를 접거나 화면을 벗어나면 듣기를 멈춘다.
 */
@Composable
internal fun LearnPracticeBox(
    state: LearnPracticeUiState,
    stage: LearnStage,
    detail: LearnStageDetail?,
    instrument: GuideInstrument,
    actions: LearnPracticeActions,
    onSelfDone: () -> Unit = actions.onComplete,
) {
    AutoListen(state, actions)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 서버 단계 상세의 '2초 이상 유지'는 판정과 다른 초안이라, 초가 들어가는 줄은 실제 판정의 유지 시간으로 쓴다(S15P31E102-857).
        // 판정 기준을 받지 못해 스스로 확인할 때는 초를 빼고 쓴다.
        val holdSeconds = state.pitch?.holdTargetMs?.let { holdSecondsLabel(it.toDouble()) }
        val rule = when (stage.number) {
            3 -> stringResource(Res.string.learn_first_sound_pass, holdSecondsLabel(FIRST_SOUND_HOLD_MS))
            4 -> holdSeconds?.let { stringResource(Res.string.learn_pitch_hold_pass, it) } ?: stringResource(Res.string.learn_pitch_hold_pass_plain)
            6 -> holdSeconds?.let { stringResource(Res.string.learn_range_hold_pass, it) } ?: stringResource(Res.string.learn_range_hold_pass_plain)
            7 -> stringResource(Res.string.learn_tongue_pass)
            else -> detail?.pass?.rule?.plain()
        }
        if (rule != null) PassLine(rule)
        ListenPanel(state, instrument, 0 until (state.pitch?.notes?.size ?: 0))
        LearnPracticeSideButtons(state, actions, onSelfDone)
    }
}

/**
 * 5·6단계 할 일 박스 안의 소리 듣기(S15P31E102-749). 이 박스가 맡은 음([notes], 판정 계획의 순서)만 보이고
 * 한 음씩 맞을 때까지 기다린다. 맡은 음을 다 내면 [onDone]으로 박스를 마친다. 음높이를 판정할 수 없으면 '해 봤어요'로 마친다.
 * [score]가 있으면(5단계) 낼 음을 정간보 한 각([LearnScoreColumn])으로 그려 연주 평가처럼 지금 낼 정간이 따라 움직인다.
 */
@Composable
internal fun LearnSegmentPractice(
    state: LearnPracticeUiState,
    instrument: GuideInstrument,
    notes: IntRange,
    actions: LearnPracticeActions,
    score: List<FingeringNote>? = null,
    onDone: () -> Unit,
) {
    val pitch = state.pitch
    val segmentDone = pitch != null && (pitch.index > notes.last || state.phase == LearnPracticePhase.Passed)
    val currentDone by rememberUpdatedState(onDone)
    LaunchedEffect(segmentDone) { if (segmentDone) currentDone() }
    // 나갔다 돌아와 앞 박스를 이미 마친 채 이 박스부터 하면, 판정을 이 박스의 첫 음으로 옮긴다(S15P31E102-749).
    val behind = pitch != null && pitch.index < notes.first
    val rewind by rememberUpdatedState(actions.onRewind)
    LaunchedEffect(behind) { if (behind) rewind(notes.first) }
    // 올라가기를 마치고 내려오기 박스로 넘어갈 때 듣기를 끊지 않도록, 박스가 접혀도 멈추지 않는다(단계를 벗어나면 멈춘다).
    if (!segmentDone) AutoListen(state, actions, stopOnLeave = false)
    val judging = pitch != null && state.canListen
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            score != null && judging && micMessage(state) == null -> ScorePanel(state, instrument, score, notes)
            score != null -> {
                micMessage(state)?.let { LearnNote(it) }
                LearnScoreColumn(instrument, score)
            }
            else -> ListenPanel(state.takeUnless { it.phase == LearnPracticePhase.Passed } ?: state.copy(phase = LearnPracticePhase.Ready), instrument, notes)
        }
        if (!judging) EolssuButton(stringResource(Res.string.learn_step_try), onDone, Modifier.fillMaxWidth(), icon = EolssuIcons.Check)
    }
}

/** 5단계 정간보 듣기 판: 오른쪽 위 마이크 뱃지, 아래 정간보 한 각. 지금 낼 정간은 듣는 중이면 녹음 빨강으로 강조한다. */
@Composable
private fun ScorePanel(state: LearnPracticeUiState, instrument: GuideInstrument, score: List<FingeringNote>, notes: IntRange) {
    val pitch = state.pitch ?: return
    val states = score.indices.map { k ->
        val i = notes.first + k
        when {
            pitch.results.getOrNull(i) == LearnNoteResult.Hit || i < pitch.index -> LearnCellState.Hit
            i == pitch.index && !pitch.finished -> LearnCellState.Current
            else -> LearnCellState.Waiting
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MicRow(state)
        LearnScoreColumn(instrument, score, states, hold = pitch.holdProgress, recording = state.listening)
    }
}

/** 듣기 판 맨 위 줄: 오른쪽에 마이크 입력 뱃지. */
@Composable
private fun MicRow(state: LearnPracticeUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        MicLevelBadge(if (state.listening) state.level else 0f)
    }
}

/**
 * 듣기는 아래 가운데 '연습 시작'을 눌러야 시작한다(S15P31E102-793, 전에는 박스를 펼치면 바로 들었다).
 * [stopOnLeave]면 이 칸이 사라질 때 듣기를 멈춘다.
 */
@Composable
private fun AutoListen(state: LearnPracticeUiState, actions: LearnPracticeActions, stopOnLeave: Boolean = true) {
    val stop by rememberUpdatedState(actions.onStop)
    DisposableEffect(Unit) { onDispose { if (stopOnLeave) stop() } }
}

/** 5단계는 올라가기·내려오기 박스가 판정 계획의 앞 절반·뒤 절반을, 6단계는 박스 하나가 모든 음을 맡는다. */
internal fun segmentNotes(stage: Int, box: Int, count: Int): IntRange = when {
    stage == 5 && count >= 2 && count % 2 == 0 -> if (box == 0) 0 until count / 2 else count / 2 until count
    else -> 0 until count
}

/** 통과 기준 한 줄: 연두 체크와 기준 문장. 화면 읽기에는 '통과 기준, …'으로 읽힌다. */
@Composable
private fun PassLine(rule: String) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val label = stringResource(Res.string.learn_section_pass)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.accentSoft, shape)
            .border(1.dp, colors.accentTintLine, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = "$label, $rule" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(26.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onPrimary)
        }
        Text(rule, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

/**
 * 소리 듣기 판: 판정 그림(3단계 소리 길이 막대·낼 음 동그라미·'투' 동그라미)만 할 일 박스 안에 바로 놓는다.
 * 통과하면 같은 자리에 통과 표시를, 마이크를 쓸 수 없으면 까닭 한 줄을 보인다. 그릴 것이 없으면(판정하지 않는 6단계 등) 아무것도 두지 않는다.
 * 따로 테두리 친 판과 마이크 입력 뱃지는 두지 않는다(S15P31E102-793). 듣는 중인지는 아래 '그만하기' 버튼으로 안다.
 */
@Composable
private fun ListenPanel(state: LearnPracticeUiState, instrument: GuideInstrument, notes: IntRange) {
    val empty = state.phase != LearnPracticePhase.Passed && micMessage(state) == null &&
        state.pitch == null && !state.judgesBySound && !state.judgesByTongue
    if (empty) return
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.phase == LearnPracticePhase.Passed) {
            PassedMark()
            return@Column
        }
        val micMessage = micMessage(state)
        if (micMessage != null) {
            LearnNote(micMessage)
            return@Column
        }
        val pitch = state.pitch
        when {
            pitch != null -> {
                // 낼 음이 하나뿐이면(6단계) 위 운지 카드와 이름이 겹치므로 동그라미 없이 이어 분 길이 막대만 둔다(S15P31E102-793).
                if (notes.count { it in pitch.notes.indices } > 1) PitchNotes(pitch, instrument, notes)
                HoldBar(pitch.holdProgress)
            }
            state.judgesBySound -> HoldBar(state.holdProgress, height = 12.dp)
            state.judgesByTongue -> TongueDots(state.tongues)
        }
    }
}

/** 마이크를 쓸 수 없을 때의 안내. 쓸 수 있으면 null. */
@Composable
private fun micMessage(state: LearnPracticeUiState): String? = when (state.mic) {
    LearnMicStatus.Available -> null
    LearnMicStatus.Unsupported -> stringResource(Res.string.learn_practice_unsupported)
    LearnMicStatus.Denied -> stringResource(Res.string.learn_practice_denied)
    LearnMicStatus.Blocked -> stringResource(Res.string.learn_practice_blocked)
    LearnMicStatus.Failed -> stringResource(Res.string.learn_practice_failed)
}

/** 7단계 '투' 동그라미 넷. 끊어 낸 소리를 셀 때마다 하나씩 연두로 채워진다. 읽기 프로그램에는 위의 횟수가 읽힌다. */
@Composable
private fun TongueDots(tongues: Int) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        repeat(TONGUE_TARGET) { i ->
            val done = i < tongues
            Box(
                Modifier
                    .size(52.dp)
                    .background(if (done) colors.accent else colors.accentSoft, CircleShape)
                    .border(2.dp, if (done) colors.accent else colors.accentTintLine, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.learn_tour_tongue),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = if (done) colors.text else colors.accentText,
                )
            }
        }
    }
}

/** 이번 소리·음을 이어 낸 길이 막대. 글자 없이 차오르기만 한다. */
@Composable
private fun HoldBar(progress: Float, height: Dp = 6.dp) {
    val colors = EolssuTheme.colors
    val shown by animateFloatAsState(progress, label = "learn-hold")
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.background, RoundedCornerShape(height / 2))
            .clearAndSetSemantics {},
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(colors.playText, RoundedCornerShape(height / 2)))
    }
}

/**
 * 음높이 판정에서 차례로 낼 음 가운데 [range]. 맞은 음은 연두, 지금 낼 음은 굵은 테두리다.
 * 5단계(한 줄에 이어 부는 음)는 음 사이에 '>'를 넣어 부는 차례를 보인다. 읽기 프로그램에는 음마다 이름과 상태를 알린다.
 */
@Composable
private fun PitchNotes(pitch: LearnPitchProgress, instrument: GuideInstrument, range: IntRange) {
    val indices = range.filter { it in pitch.notes.indices }
    if (indices.isEmpty()) return
    val colors = EolssuTheme.colors
    if (indices.size <= 5) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            indices.forEach { i ->
                if (i != indices.first()) {
                    Text(">", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = colors.textMuted, modifier = Modifier.clearAndSetSemantics { })
                }
                PitchNote(pitch, instrument, i, 44.dp)
            }
        }
        return
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        indices.forEach { i -> PitchNote(pitch, instrument, i, 44.dp) }
    }
}

@Composable
private fun PitchNote(pitch: LearnPitchProgress, instrument: GuideInstrument, i: Int, size: Dp) {
    val colors = EolssuTheme.colors
    val hanja = pitch.notes[i]
    val reading = instrument.noteOf(hanja)?.reading ?: hanja
    val result = pitch.results.getOrNull(i)
    val current = i == pitch.index && !pitch.finished
    val description = when {
        result == LearnNoteResult.Hit -> stringResource(Res.string.learn_practice_pitch_note_hit, reading)
        result == LearnNoteResult.Miss -> stringResource(Res.string.learn_practice_pitch_note_miss, reading)
        current -> stringResource(Res.string.learn_practice_pitch_note_current, reading)
        else -> stringResource(Res.string.learn_practice_pitch_note_waiting, reading)
    }
    val line = when {
        result == LearnNoteResult.Hit -> colors.accent
        result == LearnNoteResult.Miss -> colors.recText
        current -> colors.text
        else -> colors.line
    }
    Box(
        Modifier
            .size(size)
            .background(if (result == LearnNoteResult.Hit) colors.accent else colors.background, CircleShape)
            .border(if (current) 2.dp else 1.dp, line, CircleShape)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            reading,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal),
            color = when {
                result == LearnNoteResult.Miss -> colors.recText
                result == LearnNoteResult.Hit || current -> colors.text
                else -> colors.textMuted
            },
            maxLines = 1,
        )
    }
}

/** 통과 표시: 연두 체크와 '통과했어요!' 한 줄. */
@Composable
private fun PassedMark() {
    val colors = EolssuTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(28.dp), tint = colors.text)
        }
        Text(
            stringResource(Res.string.learn_practice_passed_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * 연습 박스 안 보조 버튼. 통과하면 '처음부터', 앱이 판정하지 않으면(음높이 관측이 없는 4단계, 권한이 꺼진 경우) '스스로 확인했어요'.
 * 주 동작은 단계 화면 아래 가운데 버튼([LearnPracticePrimary])이 맡는다.
 */
@Composable
private fun LearnPracticeSideButtons(state: LearnPracticeUiState, actions: LearnPracticeActions, onSelfDone: () -> Unit) {
    when {
        state.phase == LearnPracticePhase.Passed ->
            EolssuButton(stringResource(Res.string.learn_practice_restart), actions.onRestart, Modifier.fillMaxWidth(), style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Restart)
        state.mic == LearnMicStatus.Unsupported -> Unit
        !state.autoJudged || state.mic == LearnMicStatus.Blocked ->
            EolssuButton(stringResource(Res.string.learn_practice_self_done), onSelfDone, Modifier.fillMaxWidth(), style = EolssuButtonStyle.Secondary)
    }
}

/**
 * 듣는 박스가 펼쳐졌을 때 단계 화면 아래 가운데 버튼(S15P31E102-749). 그 순간 할 일을 맡는다: 듣기 전 '연습 시작',
 * 듣는 중 '그만하기', 멈춘 뒤·'처음부터' 뒤에도 '연습 시작'(S15P31E102-845), 통과 뒤 '다음 단계로'(마지막 단계는 끝내기 문구).
 * 권한이 꺼져 다시 묻지 못하면 '설정에서 허용하기', 마이크가 없는 기기는 '스스로 확인했어요'다.
 */
@Composable
internal fun LearnPracticePrimary(
    state: LearnPracticeUiState,
    actions: LearnPracticeActions,
    onSelfDone: () -> Unit,
    last: Boolean,
    modifier: Modifier,
) {
    when {
        state.phase == LearnPracticePhase.Passed -> EolssuButton(stringResource(learnDoneAction(last)), actions.onComplete, modifier)
        state.listening ->
            EolssuButton(stringResource(Res.string.learn_practice_stop), actions.onStop, modifier, style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Stop)
        state.mic == LearnMicStatus.Blocked ->
            EolssuButton(stringResource(Res.string.learn_practice_open_settings), actions.onOpenSettings, modifier, icon = EolssuIcons.Settings)
        state.mic == LearnMicStatus.Unsupported -> EolssuButton(stringResource(Res.string.learn_practice_self_done), onSelfDone, modifier)
        // 듣기 전·멈춘 뒤·'처음부터' 뒤 모두 '연습 시작' 한 문구로 둔다(S15P31E102-845, 전에는 들은 적이 있으면 '다시 시작').
        else -> EolssuButton(stringResource(Res.string.learn_start_practice), actions.onStart, modifier, icon = EolssuIcons.Mic)
    }
}
