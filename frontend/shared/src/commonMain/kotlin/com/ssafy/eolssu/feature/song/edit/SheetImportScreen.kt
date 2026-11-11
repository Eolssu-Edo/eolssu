package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.song.edit.component.PillChip
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
import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblem
import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblemDialog
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetImportScreen(state: SheetImportUiState, kind: ImportKind, onBack: () -> Unit, onList: () -> Unit,
    onTitle: (String) -> Unit, onInstrument: (String) -> Unit, onJangdan: (String) -> Unit,
    onPick: () -> Unit, onSubmit: () -> Unit, onReload: () -> Unit, onDismissError: () -> Unit = {}) {
    BackHandler(enabled = state.busy) { }
    val editable = !state.busy && !state.picking && !state.expired && state.acceptedId == null
    val colors = EolssuTheme.colors
    // 악보 추가 화면처럼 회색 바탕 위 흰 카드로 묶는다(S15P31E102-768).
    EolssuScaffold(
        containerColor = colors.groupedBackground,
        bottomDivider = false,
        topBar = { EolssuTopBar(stringResource(Res.string.sheet_upload_title), onBack = { if (!state.busy) onBack() }) },
        bottomBar = {
            if (state.acceptedId != null) EolssuButton(stringResource(Res.string.sheet_upload_list), onList)
            else EolssuButton(stringResource(Res.string.sheet_upload_send), onSubmit,
                enabled = editable && !state.loading && !state.catalogFailed, busy = state.busy)
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.expired) {
                UploadCard { Text(stringResource(Res.string.sheet_upload_expired), style = MaterialTheme.typography.bodyMedium) }
            } else if (state.acceptedId != null) {
                UploadCard {
                    Text(stringResource(Res.string.sheet_upload_accepted), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(stringResource(Res.string.sheet_upload_accepted_help), style = MaterialTheme.typography.bodyMedium, color = colors.textSub)
                }
            } else {
                UploadCard {
                    EolssuTextField(
                        value = state.title,
                        onValueChange = { if (editable) onTitle(it) },
                        label = stringResource(Res.string.sheet_upload_name),
                        placeholder = stringResource(Res.string.sheet_upload_name_placeholder),
                        bordered = false,
                        containerColor = colors.groupedBackground,
                        imeAction = ImeAction.Done,
                    )
                }
                if (state.loading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.text) }
                if (state.catalogFailed) {
                    UploadCard {
                        Text(stringResource(Res.string.sheet_upload_catalog_failed), style = MaterialTheme.typography.bodyMedium, color = colors.textSub)
                        EolssuButton(stringResource(Res.string.sheet_upload_reload), onReload, style = EolssuButtonStyle.Secondary,
                            containerColor = colors.groupedBackground, borderColor = Color.Transparent)
                    }
                }
                if (state.instruments.isNotEmpty()) UploadCard {
                    UploadLabel(stringResource(Res.string.sheet_upload_instrument))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.instruments.forEach { item ->
                            PillChip(item.name, selected = state.instrumentId == item.instrumentId, onClick = { onInstrument(item.instrumentId) }, enabled = editable)
                        }
                    }
                }
                if (kind == ImportKind.Jeongganbo && state.jangdans.isNotEmpty()) UploadCard {
                    UploadLabel(stringResource(Res.string.sheet_upload_jangdan))
                    Text(stringResource(Res.string.sheet_upload_jangdan_help), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.jangdans.forEach { item ->
                            PillChip(item.name, selected = state.jangdanId == item.jangdanId, onClick = { onJangdan(item.jangdanId) }, enabled = editable)
                        }
                    }
                }
                UploadCard {
                    UploadLabel(stringResource(Res.string.sheet_upload_files))
                    Text(stringResource(Res.string.sheet_upload_limits), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                    state.files.forEachIndexed { index, file ->
                        Row(
                            Modifier.fillMaxWidth().background(colors.groupedBackground, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = colors.accentText)
                            Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    EolssuButton(stringResource(if (state.picking) Res.string.sheet_upload_reading else Res.string.sheet_upload_pick), onPick,
                        style = EolssuButtonStyle.Secondary, enabled = editable, icon = EolssuIcons.Plus,
                        containerColor = colors.accentTint, contentColor = colors.accentText, borderColor = Color.Transparent)
                }
                if (state.busy) Text(stringResource(Res.string.sheet_upload_sending), style = MaterialTheme.typography.bodyMedium, color = colors.textSub)
                state.error?.takeIf { fileProblem(it) == null }?.let { error ->
                    Text(stringResource(importErrorText(error)), color = colors.recText, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (state.error in setOf("CONVERSION_IN_PROGRESS", "SHEET_LIMIT_EXCEEDED", "IDEMPOTENCY_CONFLICT")) {
                    EolssuButton(stringResource(Res.string.sheet_upload_list), onList, style = EolssuButtonStyle.Secondary, borderColor = Color.Transparent)
                }
            }
        }
    }
    state.error?.let(::fileProblem)?.let { problem ->
        SheetFileProblemDialog(problem, onRepick = { onDismissError(); onPick() }, onDismiss = onDismissError)
    }
}

/** 화면 안 문구 대신 안내 창으로 알릴 파일 오류. 나머지는 화면 안 문구로 둔다. */
private fun fileProblem(code: String): SheetFileProblem? = when (code) {
    "UNSUPPORTED_FILE_TYPE" -> SheetFileProblem.Format
    "PICK_FAILED", "UNREADABLE_FILE" -> SheetFileProblem.Unreadable
    else -> null
}

private fun importErrorText(code: String): StringResource = when (code) {
    "INPUT_REQUIRED", "INVALID_REQUEST", "RESOURCE_NOT_FOUND" -> Res.string.sheet_upload_input_required
    "PICK_FAILED" -> Res.string.sheet_upload_pick_failed
    "FILE_TOO_LARGE" -> Res.string.sheet_upload_too_large
    "UNSUPPORTED_FILE_TYPE", "INVALID_FILE_SET" -> Res.string.sheet_upload_format
    "TOO_MANY_PAGES" -> Res.string.sheet_upload_pages
    "UNREADABLE_FILE" -> Res.string.sheet_upload_unreadable
    "CONVERSION_IN_PROGRESS" -> Res.string.sheet_upload_in_progress
    "SHEET_LIMIT_EXCEEDED" -> Res.string.sheet_upload_limit
    "CONVERSION_UNAVAILABLE", "SERVICE_UNAVAILABLE" -> Res.string.sheet_upload_unavailable
    "IDEMPOTENCY_CONFLICT" -> Res.string.sheet_upload_conflict
    else -> Res.string.sheet_upload_failed
}

/** 회색 바탕 위 테두리 없는 흰 카드(모서리 20, 안쪽 16). */
@Composable
private fun UploadCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(EolssuTheme.colors.surface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun UploadLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = EolssuTheme.colors.text)
}
