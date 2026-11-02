package com.ssafy.eolssu.feature.guide

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_catalog_no_sound
import eolssu.shared.generated.resources.guide_catalog_link_failed
import com.ssafy.eolssu.feature.guide.component.FingeringCatalogDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.Blow

/** 기존 기초 학습 화면에서만 사용하는 미연결 안내 강조 시간. 운지 API 재생에는 사용하지 않는다. */
internal const val LISTEN_HIGHLIGHT_MILLIS = 1_000L

/**
 * GUIDE-01 연결. 기초부터 배우기에서 오면 [instrument]·[blow]로 시작하고, 뒤로 가기는 들어온 화면으로 돌아간다.
 * 등록된 음원이 있으면 외부 재생 앱을 열며, 없으면 그 사실을 안내한다. 재생한 척 강조하지 않는다.
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
    val noSound = stringResource(Res.string.guide_catalog_no_sound)
    val linkFailed = stringResource(Res.string.guide_catalog_link_failed)
    val uriHandler = LocalUriHandler.current
    var catalogOpen by rememberSaveable { mutableStateOf(false) }

    FingeringScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleDropdown = viewModel::toggleDropdown,
        onDismissDropdown = viewModel::dismissDropdown,
        onSelectInstrument = viewModel::selectInstrument,
        onSelectBlow = viewModel::selectBlow,
        onOpenCatalog = { catalogOpen = true },
        onListen = { note ->
            val sound = state.pitchFor(note.hanja)?.soundUrl
            val message = if (sound == null) noSound else if (runCatching { uriHandler.openUri(sound) }.isFailure) linkFailed else null
            if (message != null) scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        },
    )
    if (catalogOpen) FingeringCatalogDialog(state, viewModel::retryCatalog, onClose = { catalogOpen = false })
}
