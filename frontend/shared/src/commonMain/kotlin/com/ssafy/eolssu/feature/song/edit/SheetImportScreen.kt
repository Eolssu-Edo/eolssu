package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.feature.song.edit.component.sheetAddBottomBar
import com.ssafy.eolssu.feature.song.edit.component.sheetAddContentMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.data.song.SheetImportField
import com.ssafy.eolssu.feature.song.edit.component.PillChip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ssafy.eolssu.feature.song.edit.component.SheetFileSourceMenu
import androidx.compose.runtime.setValue
import com.ssafy.eolssu.core.storage.SheetFileSource
import com.ssafy.eolssu.core.storage.sheetFileSources
import com.ssafy.eolssu.feature.song.edit.component.SheetUploadGuideCard
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
    onPick: (SheetFileSource) -> Unit, onSubmit: () -> Unit, onReload: () -> Unit, onDismissError: () -> Unit = {},
    sources: List<SheetFileSource> = sheetFileSources) {
    BackHandler(enabled = state.busy) { }
    val editable = !state.busy && !state.picking && !state.expired && state.acceptedId == null
    val colors = EolssuTheme.colors
    // 가져올 곳이 여럿이면 버튼 아래로 메뉴(파일·갤러리)를 펼치고, 하나뿐이면 바로 연다(S15P31E102-997).
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val startPick = { sources.singleOrNull()?.let(onPick) ?: run { sourceMenuOpen = true } }
    val inputErrors = state.inputErrors(kind)
    // 서버/파일 실패는 누락 필드로 추측하지 않고, 입력 오류는 첫 미해결 항목을 안내한다.
    val feedback = state.error?.takeIf { it != "INPUT_REQUIRED" && fileProblem(it) == null }
        ?.let { stringResource(importErrorText(it)) }
        ?: SheetImportField.entries.firstOrNull { it in inputErrors }?.let { stringResource(fieldErrorText(it)) }
        ?: state.error?.takeIf { it == "INPUT_REQUIRED" }?.let { stringResource(importErrorText(it)) }
    val showListAction = state.error in setOf("CONVERSION_IN_PROGRESS", "SHEET_LIMIT_EXCEEDED", "IDEMPOTENCY_CONFLICT")
    // 악보 추가 화면처럼 회색 바탕 위 흰 카드로 묶는다(S15P31E102-768).
    val contentMaxWidth = sheetAddContentMaxWidth()
    EolssuScaffold(
        contentMaxWidth = contentMaxWidth,
        containerColor = colors.groupedBackground,
        bottomDivider = false,
        topBar = { EolssuTopBar(stringResource(Res.string.sheet_upload_title), onBack = { if (!state.busy) onBack() }, maxWidth = contentMaxWidth) },
        bottomBar = sheetAddBottomBar(contentMaxWidth) {
            if (state.acceptedId != null) EolssuButton(stringResource(Res.string.sheet_upload_list), onList)
            else {
                feedback?.let { UploadError(it) }
                EolssuButton(stringResource(Res.string.sheet_upload_send), onSubmit,
                    enabled = editable && !state.loading && !state.catalogFailed, busy = state.busy)
                if (showListAction) EolssuButton(stringResource(Res.string.sheet_upload_list), onList,
                    style = EolssuButtonStyle.Secondary, borderColor = Color.Transparent)
            }
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
                SheetUploadGuideCard(kind)
                UploadCard {
                    EolssuTextField(
                        value = state.title,
                        onValueChange = { if (editable) onTitle(it) },
                        label = stringResource(Res.string.sheet_upload_name),
                        placeholder = stringResource(Res.string.sheet_upload_name_placeholder),
                        bordered = false,
                        containerColor = colors.groupedBackground,
                        imeAction = ImeAction.Done,
                        error = if (SheetImportField.Title in inputErrors) stringResource(fieldErrorText(SheetImportField.Title)) else null,
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
                    if (SheetImportField.Instrument in inputErrors) UploadError(stringResource(fieldErrorText(SheetImportField.Instrument)))
                }
                if (kind == ImportKind.Jeongganbo && state.jangdans.isNotEmpty()) UploadCard {
                    UploadLabel(stringResource(Res.string.sheet_upload_jangdan))
                    Text(stringResource(Res.string.sheet_upload_jangdan_help), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.jangdans.forEach { item ->
                            PillChip(item.name, selected = state.jangdanId == item.jangdanId, onClick = { onJangdan(item.jangdanId) }, enabled = editable)
                        }
                    }
                    if (SheetImportField.Jangdan in inputErrors) UploadError(stringResource(fieldErrorText(SheetImportField.Jangdan)))
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
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.accentText)
                            Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box {
                        EolssuButton(stringResource(if (state.picking) Res.string.sheet_upload_reading else Res.string.sheet_upload_pick), startPick,
                            style = EolssuButtonStyle.Secondary, enabled = editable, icon = EolssuIcons.Plus,
                            containerColor = colors.accentTint, contentColor = colors.accentText, borderColor = Color.Transparent)
                        if (sourceMenuOpen) SheetFileSourceMenu(sources,
                            onPick = { source -> sourceMenuOpen = false; onPick(source) },
                            onDismiss = { sourceMenuOpen = false })
                    }
                    if (SheetImportField.Files in inputErrors) UploadError(stringResource(fieldErrorText(SheetImportField.Files)))
                }
                if (state.busy) Text(stringResource(Res.string.sheet_upload_sending), style = MaterialTheme.typography.bodyMedium, color = colors.textSub)
            }
        }
    }
    state.error?.let(::fileProblem)?.let { problem ->
        SheetFileProblemDialog(problem, onRepick = { onDismissError(); startPick() }, onDismiss = onDismissError)
    }
}

/** 파일 형식·읽기 실패는 안내 창, 나머지는 고정 하단 오류 요약으로 알린다. */
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

private fun fieldErrorText(field: SheetImportField): StringResource = when (field) {
    SheetImportField.Title -> Res.string.sheet_details_title_error
    SheetImportField.Instrument -> Res.string.sheet_upload_choose_instrument
    SheetImportField.Jangdan -> Res.string.sheet_upload_choose_jangdan
    SheetImportField.Files -> Res.string.sheet_upload_choose_files
}

/** 스크롤과 무관한 하단 요약과 필드 아래에서 같은 오류 안내를 사용한다. */
@Composable
private fun UploadError(message: String) {
    val color = EolssuTheme.colors.recText
    Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite; error(message) },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(EolssuIcons.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
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
