package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_practice_box_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 3~7단계 연습 박스 연결(S15P31E102-688). 단계 화면을 옮기지 않고 마지막 할 일 박스에서 듣고 판정한다.
 * 단계마다 연습 상태([LearnPracticeViewModel])를 따로 두고, 화면을 벗어나면(ON_STOP) 듣기를 멈춘다.
 * 완료로 표시하면 [onCompleted]로 다음 단계(마지막이면 null)를 넘긴다. 마지막 단계면 결승 축하를 켠다. 연습 단계가 아니면 null이다.
 */
@Composable
internal fun rememberLearnStagePractice(
    stage: LearnStage?,
    learnState: LearnUiState,
    learnViewModel: LearnViewModel,
    onSettingsFailed: () -> Unit,
    onCompleted: (Int?) -> Unit,
): StepPractice? {
    if (stage == null || stage.primaryAction != LearnStageAction.Practice) return null
    return key(stage.number) {
        val number = stage.number
        val viewModel: LearnPracticeViewModel = koinViewModel(key = "learn-practice-$number") { parametersOf(number) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val detail = (learnState.detail(number) as? LearnDetailLoad.Loaded)?.detail
        LaunchedEffect(learnState.instrument, detail) { detail?.let { viewModel.prepare(learnState.instrument, it) } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopListening() }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshPermission() }
        val next = learnState.stageAfterConfirm(number)
        val actions = LearnPracticeActions(
            onStart = viewModel::startListening,
            onStop = viewModel::stopListening,
            onRestart = viewModel::reset,
            onComplete = {
                viewModel.stopListening()
                if (next == null) learnViewModel.finishTrack(number) else learnViewModel.confirm(number)
                onCompleted(next)
            },
            onOpenSettings = { if (!viewModel.openMicSettings()) onSettingsFailed() },
        )
        val instrument = learnState.instrument
        StepPractice(stringResource(Res.string.learn_practice_box_title)) {
            LearnPracticeBox(state, stage, detail, instrument, actions, last = next == null)
        }
    }
}
