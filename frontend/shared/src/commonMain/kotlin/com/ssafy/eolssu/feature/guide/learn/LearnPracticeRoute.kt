package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_practice_settings_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 기초부터 배우기 단계 연습(3~7단계) 연결. 단계 목록·상세와 같은 [learnViewModel]로 악기를 보이고 완료를 표시한다.
 * [onCompleted]는 완료로 표시한 뒤 갈 단계(마지막이면 null)를 받는다. 화면을 벗어나면(ON_STOP) 듣기를 멈춘다.
 */
@Composable
fun LearnPracticeRoute(
    stageNumber: Int,
    learnViewModel: LearnViewModel,
    onBack: () -> Unit,
    onCompleted: (Int?) -> Unit,
    viewModel: LearnPracticeViewModel = koinViewModel { parametersOf(stageNumber) },
) {
    val stage = learnStageOf(stageNumber) ?: return
    val learnState by learnViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopListening() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshPermission() }

    val back = {
        viewModel.stopListening()
        onBack()
    }
    LearnPracticeScreen(
        state = state,
        stage = stage,
        instrument = learnState.instrument,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = LearnPracticeActions(
            onBack = back,
            onStart = viewModel::startListening,
            onStop = viewModel::stopListening,
            onRestart = viewModel::reset,
            onComplete = {
                viewModel.stopListening()
                learnViewModel.confirm(stage.number)
                onCompleted(stageAfterConfirm(stage.number))
            },
            onOpenSettings = {
                if (!viewModel.openMicSettings()) {
                    scope.launch { snackbarHostState.showSnackbar(getString(Res.string.learn_practice_settings_failed)) }
                }
            },
        ),
    )
}
