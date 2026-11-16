package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.storage.SheetFileSource
import com.ssafy.eolssu.core.storage.rememberSheetFilePicker
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SheetImportRoute(kind: ImportKind, hwang: HwangPitch, onBack: () -> Unit, onList: () -> Unit,
    viewModel: SheetImportViewModel = koinViewModel { parametersOf(kind, hwang) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberSheetFilePicker(viewModel::selectFiles)
    DisposableEffect(viewModel) {
        onDispose { viewModel.selectFiles(com.ssafy.eolssu.core.storage.SheetFileSelection.Cancelled) }
    }
    val pick = { source: SheetFileSource -> if (viewModel.beginPicking()) picker(source) }
    SheetImportScreen(state, kind, onBack, onList, viewModel::title, viewModel::instrument,
        viewModel::jangdan, pick, viewModel::submit, viewModel::reload, viewModel::dismissError)
}
