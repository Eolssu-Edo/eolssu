package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.storage.rememberSheetFilePicker
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SheetImportRoute(kind: ImportKind, hwang: HwangPitch, onBack: () -> Unit, onList: () -> Unit,
    pickOnStart: Boolean = false,
    viewModel: SheetImportViewModel = koinViewModel { parametersOf(kind, hwang) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberSheetFilePicker(viewModel::selectFiles)
    DisposableEffect(viewModel) {
        onDispose { viewModel.selectFiles(com.ssafy.eolssu.core.storage.SheetFileSelection.Cancelled) }
    }
    val pick = { if (viewModel.beginPicking()) picker() }
    // 악보 추가의 '사진·PDF 선택하기'로 왔으면 들어오자마자 한 번만 기기 파일 선택 창을 연다.
    var autoPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (pickOnStart && !autoPicked) {
            autoPicked = true
            pick()
        }
    }
    SheetImportScreen(state, kind, onBack, onList, viewModel::title, viewModel::instrument,
        viewModel::jangdan, pick, viewModel::submit, viewModel::reload, viewModel::dismissError)
}
