package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_practice_box_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 할 일 박스 안에서 판정하는 단계(S15P31E102-749). 5단계는 올라가기·내려오기, 6단계는 새 음역 박스에서 바로 분다. */
internal fun judgesInTasks(stage: Int): Boolean = stage == 5 || stage == 6

/** 할 일 박스의 완료를 먼저 남기고, 마지막 수동 확인이면 단계 완료 창으로 잇는다. */
internal fun completeLearnSegment(
    state: LearnPracticeUiState,
    box: Int,
    boxes: Int,
    onBoxDone: () -> Unit,
    onSelfDone: () -> Unit,
) {
    onBoxDone()
    if (box == boxes - 1 && (state.pitch == null || !state.canListen)) onSelfDone()
}

/**
 * 3~7단계 연습 연결(S15P31E102-688). 단계 화면을 옮기지 않고 박스에서 듣고 판정한다.
 * 3·4·7단계는 마지막 연습 박스, 5·6단계는 할 일 박스 안에서 듣는다(S15P31E102-749).
 * 단계마다 연습 상태([LearnPracticeViewModel])를 따로 두고, 화면을 벗어나면(ON_STOP) 듣기를 멈춘다.
 * 연습 상태는 [practiceOwner](단계 목록·상세가 함께 쓰는 기초부터 배우기 그래프)에 묶는다. 태블릿을 돌려 단계 화면과
 * 목록 2단 배치가 바뀌어도 통과 상태가 이어진다(S15P31E102-1030).
 * 4단계부터는 통과하거나 스스로 확인하면 '완료했어요!'를 띄우고, '다음 단계로'가 완료 표시 뒤 [onCompleted]로 다음 단계(마지막이면 null)를 넘긴다.
 * 연습 단계가 아니면 null이다.
 */
@Composable
internal fun rememberLearnStagePractice(
    stage: LearnStage?,
    learnState: LearnUiState,
    learnViewModel: LearnViewModel,
    practiceOwner: ViewModelStoreOwner,
    onSettingsFailed: () -> Unit,
    onCompleted: (Int?) -> Unit,
): StepPractice? {
    if (stage == null || stage.primaryAction != LearnStageAction.Practice) return null
    return key(stage.number) {
        val number = stage.number
        val viewModel: LearnPracticeViewModel = koinViewModel(viewModelStoreOwner = practiceOwner, key = "learn-practice-$number") { parametersOf(number) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val celebrating by viewModel.celebrating.collectAsStateWithLifecycle()
        val detail = (learnState.detail(number) as? LearnDetailLoad.Loaded)?.detail
        LaunchedEffect(learnState.instrument, detail) { detail?.let { viewModel.prepare(learnState.instrument, it) } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopListening() }
        // 다른 단계로 가거나 단계 화면을 닫으면 멈춘다(할 일 박스 안 듣기는 박스가 접혀도 이어 간다).
        DisposableEffect(Unit) { onDispose { viewModel.stopListening() } }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshPermission() }
        val next = learnState.stageAfterConfirm(number)
        val complete = {
            viewModel.stopListening()
            viewModel.dismissCelebration()
            if (next == null) learnViewModel.finishTrack(number) else learnViewModel.confirm(number)
            onCompleted(next)
        }
        // 한 번 통과에 한 번만 띄우고(닫은 뒤 화면을 돌려도 다시 뜨지 않음), 처음부터 다시 하면 다음 통과에 다시 띄운다.
        val celebrates = number >= 4
        val passed = state.phase == LearnPracticePhase.Passed
        LaunchedEffect(passed) { viewModel.onPassChanged(passed, celebrates) }
        val actions = LearnPracticeActions(
            onStart = viewModel::startListening,
            onStop = viewModel::stopListening,
            onRestart = viewModel::reset,
            onComplete = complete,
            onOpenSettings = { if (!viewModel.openMicSettings()) onSettingsFailed() },
            onRewind = viewModel::rewindTo,
        )
        val selfDone: () -> Unit = if (celebrates) viewModel::celebrate else complete
        val instrument = learnState.instrument
        val boxes = detail?.let { learnStepCount(number, it, instrument) } ?: 0
        // 5단계는 박스마다 올라가기·내려오기 음을 정간보 한 각으로 그린다.
        val score: (Int) -> List<FingeringNote>? = { box ->
            if (number == 5) detail?.let { d -> LearnPattern.entries.getOrNull(box)?.notes(d.pitchNames)?.mapNotNull(instrument::noteOf) } else null
        }
        val segmentJudging = judgesInTasks(number) && state.pitch != null && state.canListen
        // 듣기 주 버튼은 듣는 박스 안에 둔다(S15P31E102-877).
        val primary: @Composable (Modifier) -> Unit = { modifier -> LearnPracticePrimary(state, actions, selfDone, last = next == null, modifier) }
        StepPractice(
            title = stringResource(Res.string.learn_practice_box_title),
            segment = if (judgesInTasks(number)) { box, done ->
                LearnSegmentPractice(state, instrument, segmentNotes(number, box, state.pitch?.notes?.size ?: 0), actions, score(box)) {
                    completeLearnSegment(state, box, boxes, done, selfDone)
                }
            } else {
                null
            },
            segmentReplacesFigure = number == 5,
            // 할 일 박스 안에서 판정하지 못하면(판정 계획이 없거나 마이크를 쓸 수 없음) 박스의 '해 봤어요'로 마친다.
            primary = primary.takeIf { !judgesInTasks(number) || segmentJudging },
            passed = passed,
            // 5·6단계 할 일 박스의 체크를 풀면 그 박스의 첫 음부터 다시 판정한다.
            onUndo = { box -> if (judgesInTasks(number)) viewModel.rewindTo(segmentNotes(number, box, state.pitch?.notes?.size ?: 0).first) },
            overlay = {
                if (celebrating) {
                    LearnDoneDialog(learnDoneBody(number), onDismiss = viewModel::dismissCelebration, onNext = complete, action = learnDoneAction(next == null))
                }
            },
        ) {
            LearnPracticeBox(state, stage, detail, instrument, actions, selfDone, primary)
        }
    }
}
