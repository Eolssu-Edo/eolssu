package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.storage.SheetFileSource
import com.ssafy.eolssu.core.storage.rememberSheetFilePicker
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SheetImportRoute(kind: ImportKind, hwang: HwangPitch, onBack: () -> Unit, onList: () -> Unit,
    onAccepted: ((String) -> Unit)? = null,
    replacing: String? = null,
    viewModel: SheetImportViewModel = koinViewModel { parametersOf(kind, hwang, replacing.orEmpty()) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 접수되면 변환 대기 화면으로 넘긴다(S15P31E102-998). 콜백이 없으면 이 화면의 접수 안내를 그대로 보인다.
    LaunchedEffect(state.acceptedId) { state.acceptedId?.let { id -> onAccepted?.invoke(id) } }
    val picker = rememberSheetFilePicker(viewModel::selectFiles)
    DisposableEffect(viewModel) {
        onDispose { viewModel.selectFiles(com.ssafy.eolssu.core.storage.SheetFileSelection.Cancelled) }
    }
    val pick = { source: SheetFileSource -> if (viewModel.beginPicking()) picker(source) }
    SheetImportScreen(state, onBack, onList, viewModel::title, viewModel::kind, viewModel::instrument,
        viewModel::jangdan, pick, viewModel::submit, viewModel::reload, viewModel::dismissError)
}
