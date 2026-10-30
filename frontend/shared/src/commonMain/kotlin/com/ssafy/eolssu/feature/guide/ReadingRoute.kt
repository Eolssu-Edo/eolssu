package com.ssafy.eolssu.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.feature.guide.component.ReadingCatalogDialog
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReadingRoute(onBack: () -> Unit, initialPage: ReadingPage = ReadingPage.Intro,
    viewModel: ReadingCatalogViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    ReadingScreen(onBack, initialPage, onOpenCatalog = { catalogOpen = true })
    if (catalogOpen) ReadingCatalogDialog(state, viewModel::retryJangdans, viewModel::retrySymbols,
        viewModel::selectJangdan, viewModel::retryDetail, onClose = { catalogOpen = false })
}
