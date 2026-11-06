package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetImportScreen(state: SheetImportUiState, kind: ImportKind, onBack: () -> Unit, onList: () -> Unit,
    onTitle: (String) -> Unit, onInstrument: (String) -> Unit, onJangdan: (String) -> Unit,
    onPick: () -> Unit, onSubmit: () -> Unit, onReload: () -> Unit) {
    BackHandler(enabled = state.busy) { }
    val editable = !state.busy && !state.picking && !state.expired && state.acceptedId == null
    EolssuScaffold(
        topBar = { EolssuTopBar(stringResource(Res.string.sheet_upload_title), onBack = { if (!state.busy) onBack() }) },
        bottomBar = {
            if (state.acceptedId != null) EolssuButton(stringResource(Res.string.sheet_upload_list), onList)
            else EolssuButton(stringResource(Res.string.sheet_upload_send), onSubmit,
                enabled = editable && !state.loading && !state.catalogFailed, busy = state.busy)
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.expired) {
                Text(stringResource(Res.string.sheet_upload_expired))
            } else if (state.acceptedId != null) {
                Text(stringResource(Res.string.sheet_upload_accepted), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(stringResource(Res.string.sheet_upload_accepted_help))
            } else {
                OutlinedTextField(state.title, onTitle, label = { Text(stringResource(Res.string.sheet_upload_name)) },
                    enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (state.loading) CircularProgressIndicator()
                if (state.catalogFailed) {
                    Text(stringResource(Res.string.sheet_upload_catalog_failed))
                    EolssuButton(stringResource(Res.string.sheet_upload_reload), onReload, style = EolssuButtonStyle.Secondary)
                }
                Text(stringResource(Res.string.sheet_upload_instrument), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.instruments.forEach { item ->
                        FilterChip(selected = state.instrumentId == item.instrumentId, onClick = { onInstrument(item.instrumentId) },
                            label = { Text(item.name) }, enabled = editable)
                    }
                }
                if (kind == ImportKind.Jeongganbo) {
                    Text(stringResource(Res.string.sheet_upload_jangdan), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(Res.string.sheet_upload_jangdan_help), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.jangdans.forEach { item ->
                            FilterChip(selected = state.jangdanId == item.jangdanId, onClick = { onJangdan(item.jangdanId) },
                                label = { Text(item.name) }, enabled = editable)
                        }
                    }
                }
                Text(stringResource(Res.string.sheet_upload_limits), style = MaterialTheme.typography.bodySmall)
                state.files.forEachIndexed { index, file -> Text("${index + 1}. ${file.name}") }
                EolssuButton(stringResource(if (state.picking) Res.string.sheet_upload_reading else Res.string.sheet_upload_pick), onPick,
                    style = EolssuButtonStyle.Secondary, enabled = editable)
                if (state.busy) Text(stringResource(Res.string.sheet_upload_sending))
                state.error?.let { error ->
                    Text(stringResource(importErrorText(error)), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (state.error in setOf("CONVERSION_IN_PROGRESS", "SHEET_LIMIT_EXCEEDED", "IDEMPOTENCY_CONFLICT")) {
                    EolssuButton(stringResource(Res.string.sheet_upload_list), onList, style = EolssuButtonStyle.Secondary)
                }
            }
        }
    }
}

private fun importErrorText(code: String): StringResource = when (code) {
    "INPUT_REQUIRED", "INVALID_REQUEST", "RESOURCE_NOT_FOUND" -> Res.string.sheet_upload_input_required
    "PICK_FAILED" -> Res.string.sheet_upload_pick_failed
    "FILE_TOO_LARGE" -> Res.string.sheet_upload_too_large
    "UNSUPPORTED_FILE_TYPE" -> Res.string.sheet_upload_format
    "TOO_MANY_PAGES" -> Res.string.sheet_upload_pages
    "UNREADABLE_FILE" -> Res.string.sheet_upload_unreadable
    "CONVERSION_IN_PROGRESS" -> Res.string.sheet_upload_in_progress
    "SHEET_LIMIT_EXCEEDED" -> Res.string.sheet_upload_limit
    "CONVERSION_UNAVAILABLE", "SERVICE_UNAVAILABLE" -> Res.string.sheet_upload_unavailable
    "IDEMPOTENCY_CONFLICT" -> Res.string.sheet_upload_conflict
    else -> Res.string.sheet_upload_failed
}
