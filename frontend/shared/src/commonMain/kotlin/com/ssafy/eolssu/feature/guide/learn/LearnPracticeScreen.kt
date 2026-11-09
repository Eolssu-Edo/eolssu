package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.guide.component.GuideTabletBottomContent
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.JudgeTag
import com.ssafy.eolssu.feature.guide.component.LearnNote
import com.ssafy.eolssu.feature.guide.component.LearnPatternBox
import com.ssafy.eolssu.feature.guide.component.LearnSection
import com.ssafy.eolssu.feature.guide.component.LearnSmallNote
import com.ssafy.eolssu.feature.guide.component.LearnTaskList
import com.ssafy.eolssu.feature.guide.component.NoteChips
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.feature.guide.component.label
import com.ssafy.eolssu.core.model.fingering.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_detail_failed
import eolssu.shared.generated.resources.learn_first_sound_note
import eolssu.shared.generated.resources.learn_practice_badge_listening
import eolssu.shared.generated.resources.learn_practice_badge_passed
import eolssu.shared.generated.resources.learn_practice_badge_ready
import eolssu.shared.generated.resources.learn_practice_blocked
import eolssu.shared.generated.resources.learn_practice_count
import eolssu.shared.generated.resources.learn_practice_cue_counted
import eolssu.shared.generated.resources.learn_practice_cue_too_short
import eolssu.shared.generated.resources.learn_practice_denied
import eolssu.shared.generated.resources.learn_practice_failed
import eolssu.shared.generated.resources.learn_practice_finish_next
import eolssu.shared.generated.resources.learn_practice_hold
import eolssu.shared.generated.resources.learn_practice_level
import eolssu.shared.generated.resources.learn_practice_listen_title
import eolssu.shared.generated.resources.learn_practice_open_settings
import eolssu.shared.generated.resources.learn_practice_passed_body
import eolssu.shared.generated.resources.learn_practice_passed_title
import eolssu.shared.generated.resources.learn_practice_privacy
import eolssu.shared.generated.resources.learn_practice_ready
import eolssu.shared.generated.resources.learn_practice_restart
import eolssu.shared.generated.resources.learn_practice_section_how
import eolssu.shared.generated.resources.learn_practice_section_notes
import eolssu.shared.generated.resources.learn_practice_self_done
import eolssu.shared.generated.resources.learn_practice_self_note
import eolssu.shared.generated.resources.learn_practice_sounding
import eolssu.shared.generated.resources.learn_practice_start
import eolssu.shared.generated.resources.learn_practice_stop
import eolssu.shared.generated.resources.learn_practice_title
import eolssu.shared.generated.resources.learn_practice_unsupported
import eolssu.shared.generated.resources.learn_practice_waiting
import eolssu.shared.generated.resources.learn_section_pass
import eolssu.shared.generated.resources.learn_section_tasks
import org.jetbrains.compose.resources.stringResource

/** 단계 연습의 동작. 화면 이동은 Route가 이어 준다. */
class LearnPracticeActions(
    val onBack: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onRestart: () -> Unit,
    val onComplete: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** 단계 내용을 받지 못했을 때 '다시 시도'. */
    val onRetryDetail: () -> Unit = {},
)

/**
 * 기초부터 배우기 단계 연습(3~7단계). 보드가 없어 단계 상세의 조각(목표 카드 · 흰 묶음 · 하단 버튼 두 개)으로 맞췄다.
 * 위에서부터 통과 기준, 소리 듣기 판, 연습할 내용. 3단계는 듣기 판에서 횟수와 이번 소리 길이를 세고,
 * 4~7단계는 소리 크기만 보여 주고 '스스로 확인했어요'로 완료를 표시한다.
 */
@Composable
fun LearnPracticeScreen(
    state: LearnPracticeUiState,
    stage: LearnStage,
    detail: LearnDetailLoad,
    instrument: GuideInstrument,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: LearnPracticeActions,
) {
    val tablet = widthClass != WindowWidthClass.Compact
    EolssuScaffold(
        topBar = {
            GuideTopBar(
                stringResource(Res.string.learn_practice_title, stage.number, stage.title),
                onBack = actions.onBack,
                widthClass = widthClass,
                subtitle = stringResource(instrument.label),
            )
        },
        bottomBar = {
            if (tablet) GuideTabletBottomContent { LearnPracticeBottomBar(state, actions) }
            else LearnPracticeBottomBar(state, actions)
        },
        snackbarHostState = snackbarHostState,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        val side = widthClass.guideSidePadding
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = side, end = side, bottom = 24.dp)),
        ) {
            // 듣기 판은 단계 내용 없이도 쓸 수 있어, 내용을 받는 중·실패여도 그대로 둔다.
            val loaded = (detail as? LearnDetailLoad.Loaded)?.detail
            loaded?.pass?.let { PassCard(stage, it) }
            if (!state.judgesBySound) {
                LearnNote(stringResource(Res.string.learn_practice_self_note), Modifier.padding(bottom = 14.dp))
            }
            ListenPanel(state)
            if (loaded != null) {
                PracticeMaterial(stage, loaded, instrument, micUsable = state.mic != LearnMicStatus.Unsupported)
            } else {
                LearnLoadStatus(failed = detail == LearnDetailLoad.Failed, failedMessage = Res.string.learn_detail_failed, onRetry = actions.onRetryDetail)
            }
        }
    }
}

/** 통과 기준 카드. 단계 상세의 목표 카드(`.lp-goal`)와 같은 모양에 기준 문장을 굵게 둔다. */
@Composable
private fun PassCard(stage: LearnStage, pass: LearnPass) {
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
            Text(
                stringResource(Res.string.learn_section_pass),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            JudgeTag(stringResource(stage.judge.label), stage.judge)
        }
        Text(pass.rule, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold))
        Text(
            pass.note,
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textMuted,
        )
    }
}

/**
 * 소리 듣기 판. 상태 배지 · 소리 크기 막대 · (3단계) 횟수 점과 이번 소리 길이 · 한 줄 안내.
 * 통과하면 같은 자리에 통과 안내를, 마이크를 쓸 수 없으면 까닭을 보여 준다.
 */
@Composable
private fun ListenPanel(state: LearnPracticeUiState) {
    val colors = EolssuTheme.colors
    LearnSection(stringResource(Res.string.learn_practice_listen_title)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, if (state.listening) colors.accentTintLine else colors.lineSoft, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.phase == LearnPracticePhase.Passed) {
                PassedMessage()
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(state)
                Spacer(Modifier.weight(1f))
                if (state.judgesBySound && state.mic != LearnMicStatus.Unsupported) {
                    Text(
                        stringResource(Res.string.learn_practice_count, state.successes, FIRST_SOUND_TARGET),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            // 마이크를 쓸 수 없으면 움직이지 않을 막대·점 대신 까닭만 보인다.
            if (state.mic == LearnMicStatus.Available) {
                LevelMeter(state.level, sounding = state.sounding)
                if (state.judgesBySound) {
                    CountDots(state.successes)
                    HoldBar(state)
                }
            }
            val micMessage = micMessage(state)
            if (micMessage != null) {
                LearnNote(micMessage)
            } else {
                Text(
                    listenMessage(state),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = if (state.cue == LearnPracticeCue.TooShort) colors.recText else colors.text,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(state: LearnPracticeUiState) {
    if (state.listening) {
        EolssuBadge(stringResource(Res.string.learn_practice_badge_listening), style = EolssuBadgeStyle.Recording, icon = EolssuIcons.Mic)
    } else {
        EolssuBadge(stringResource(Res.string.learn_practice_badge_ready), style = EolssuBadgeStyle.Outline)
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

/** 듣는 동안의 한 줄 안내. 방금 센 소리·끊긴 소리 안내가 소리가 다시 날 때까지 남는다. */
@Composable
private fun listenMessage(state: LearnPracticeUiState): String = when {
    !state.listening -> stringResource(Res.string.learn_practice_ready)
    state.sounding -> stringResource(Res.string.learn_practice_sounding)
    state.cue == LearnPracticeCue.Counted -> stringResource(Res.string.learn_practice_cue_counted, state.successes)
    state.cue == LearnPracticeCue.TooShort -> stringResource(Res.string.learn_practice_cue_too_short)
    else -> stringResource(Res.string.learn_practice_waiting)
}

/** 소리 크기 막대. 판정 기준([LEARN_SOUND_LEVEL]) 위치에 눈금을 두고, 넘으면 연두로 채운다. */
@Composable
private fun LevelMeter(level: Float, sounding: Boolean) {
    val colors = EolssuTheme.colors
    val shown by animateFloatAsState(level.coerceIn(0f, 1f), label = "learn-level")
    val description = stringResource(Res.string.learn_practice_level)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            EolssuIcons.Mic,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (sounding) colors.text else colors.textMuted,
        )
        Box(
            Modifier
                .weight(1f)
                .height(12.dp)
                .background(colors.background, RoundedCornerShape(6.dp))
                .border(1.dp, colors.lineSoft, RoundedCornerShape(6.dp))
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(level.coerceIn(0f, 1f), 0f..1f)
                },
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(shown)
                    .background(if (sounding) colors.accent else colors.accentTintStrong, RoundedCornerShape(6.dp)),
            )
            // 기준 눈금
            Box(
                Modifier
                    .fillMaxWidth(LEARN_SOUND_LEVEL)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(colors.lineStrong))
            }
        }
    }
}

/** 3단계 횟수 점. 센 만큼 연두에 체크를 채운다. */
@Composable
private fun CountDots(successes: Int) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        repeat(FIRST_SOUND_TARGET) { i ->
            val done = i < successes
            Box(
                Modifier
                    .size(44.dp)
                    .background(if (done) colors.accent else colors.background, CircleShape)
                    .border(1.dp, if (done) colors.accent else colors.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.text)
                } else {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = colors.textMuted)
                }
            }
        }
    }
}

/** 이번 소리 길이(2초까지). */
@Composable
private fun HoldBar(state: LearnPracticeUiState) {
    val colors = EolssuTheme.colors
    val shown by animateFloatAsState(state.holdProgress, label = "learn-hold")
    val tenths = (state.holdMs.coerceAtMost(FIRST_SOUND_HOLD_MS) / 100).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.background, RoundedCornerShape(3.dp)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(colors.playText, RoundedCornerShape(3.dp)))
        }
        Text(
            stringResource(Res.string.learn_practice_hold, tenths / 10, tenths % 10),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
    }
}

@Composable
private fun PassedMessage() {
    val colors = EolssuTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(28.dp), tint = colors.text)
        }
        EolssuBadge(stringResource(Res.string.learn_practice_badge_passed), style = EolssuBadgeStyle.Soft)
        Text(
            stringResource(Res.string.learn_practice_passed_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            stringResource(Res.string.learn_practice_passed_body),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 단계별로 연습할 내용. 단계 상세와 같은 데이터로, 듣는 동안 볼 것만 추린다.
 * 마이크를 쓸 수 없는 기기([micUsable]이 false)는 듣기 판이 이미 스스로 확인하라고 알려 판정·녹음 안내를 되풀이하지 않는다.
 */
@Composable
private fun PracticeMaterial(stage: LearnStage, detail: LearnStageDetail, instrument: GuideInstrument, micUsable: Boolean) {
    val noteOf = { hanja: String -> instrument.noteOf(hanja) }
    when (stage.number) {
        3 -> {
            LearnSection(stringResource(Res.string.learn_practice_section_how)) { LearnTaskList(detail.tasks) }
            detail.pitchNames.firstOrNull()?.let(noteOf)?.let { first ->
                LearnNote(stringResource(Res.string.learn_first_sound_note, first.name, first.reading), Modifier.padding(bottom = 16.dp))
            }
        }
        4 -> LearnSection(stringResource(Res.string.learn_practice_section_notes)) {
            LearnPatternBox {
                NoteChips(detail.pitchNames.mapNotNull(noteOf))
                detail.pitchNote?.let { LearnSmallNote(it) }
            }
        }
        5 -> LearnSection(stringResource(Res.string.learn_practice_section_notes)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LearnPattern.entries.forEach { pattern ->
                    LearnPatternBox {
                        Text(
                            stringResource(pattern.label),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        )
                        NoteChips(pattern.notes(detail.pitchNames).mapNotNull(noteOf))
                    }
                }
            }
        }
        6 -> LearnSection(stringResource(Res.string.learn_practice_section_notes)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.ranges.forEach { range ->
                    LearnPatternBox {
                        Text(range.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                        NoteChips(listOfNotNull(noteOf(range.first)))
                        LearnSmallNote(range.description)
                    }
                }
            }
        }
        else -> LearnSection(stringResource(Res.string.learn_section_tasks)) { LearnTaskList(detail.tasks) }
    }
    if (!micUsable) return
    LearnSmallNote(stringResource(Res.string.learn_practice_privacy))
}

/**
 * 하단 고정 버튼. 통과: 처음부터 · 완료하고 다음 단계. 듣는 중: 그만하기(4~7단계는 스스로 확인 함께).
 * 준비: 3단계는 듣기 시작 하나, 4~7단계는 스스로 확인 · 듣기 시작. 마이크를 쓸 수 없으면 듣기 대신 스스로 확인(다시 묻지 못하면 설정 열기).
 */
@Composable
internal fun LearnPracticeBottomBar(state: LearnPracticeUiState, actions: LearnPracticeActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val half = Modifier.weight(1f)
        // 완료 버튼은 문구가 길어 반 너비에서 아이콘을 넣으면 휴대폰(360dp)에서 두 줄이 된다(Galaxy S10 확인). 아이콘 없이 둔다.
        val selfDone: @Composable (Modifier, EolssuButtonStyle) -> Unit = { modifier, style ->
            EolssuButton(stringResource(Res.string.learn_practice_self_done), actions.onComplete, modifier, style = style)
        }
        when {
            state.phase == LearnPracticePhase.Passed -> {
                EolssuButton(stringResource(Res.string.learn_practice_restart), actions.onRestart, half, style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Restart)
                EolssuButton(stringResource(Res.string.learn_practice_finish_next), actions.onComplete, half)
            }
            state.listening -> {
                EolssuButton(stringResource(Res.string.learn_practice_stop), actions.onStop, half, style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Stop)
                if (!state.judgesBySound) selfDone(half, EolssuButtonStyle.Primary)
            }
            state.mic == LearnMicStatus.Blocked -> {
                selfDone(half, EolssuButtonStyle.Secondary)
                EolssuButton(stringResource(Res.string.learn_practice_open_settings), actions.onOpenSettings, half, icon = EolssuIcons.Settings)
            }
            !state.canListen -> selfDone(Modifier.weight(1f), EolssuButtonStyle.Primary)
            else -> {
                if (!state.judgesBySound) selfDone(half, EolssuButtonStyle.Secondary)
                EolssuButton(stringResource(Res.string.learn_practice_start), actions.onStart, half, icon = EolssuIcons.Mic)
            }
        }
    }
}
