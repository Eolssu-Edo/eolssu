package com.ssafy.eolssu.feature.guide

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_listen_pending
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** '음 듣기'를 누른 버튼을 강조하는 시간. 실제 음원이 연결되면 재생 길이로 바꾼다. */
internal const val LISTEN_HIGHLIGHT_MILLIS = 1_000L

/**
 * GUIDE-01 연결. 기초부터 배우기에서 오면 [instrument]·[blow]로 시작하고, 뒤로 가기는 들어온 화면으로 돌아간다.
 * '음 듣기'는 음원이 없어 버튼만 잠시 강조하고 안내를 띄운다.
 */
@Composable
fun FingeringRoute(
    onBack: () -> Unit,
    instrument: GuideInstrument = GuideInstrument.Default,
    blow: Blow = Blow.Mid,
    viewModel: FingeringViewModel = koinViewModel { parametersOf(instrument, blow) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pending = stringResource(Res.string.guide_listen_pending)

    LaunchedEffect(state.playing) {
        val playing = state.playing ?: return@LaunchedEffect
        delay(LISTEN_HIGHLIGHT_MILLIS)
        viewModel.finishListening(playing)
    }

    FingeringScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleDropdown = viewModel::toggleDropdown,
        onDismissDropdown = viewModel::dismissDropdown,
        onSelectInstrument = viewModel::selectInstrument,
        onSelectBlow = viewModel::selectBlow,
        onListen = { note ->
            viewModel.listen(note.hanja)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(pending)
            }
        },
    )
}
