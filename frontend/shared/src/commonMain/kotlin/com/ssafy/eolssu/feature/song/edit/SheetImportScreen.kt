package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import com.ssafy.eolssu.core.model.InputLimit
import com.ssafy.eolssu.core.model.instrumentOfServerCode
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
import com.ssafy.eolssu.feature.song.edit.component.PillChipTouchInset
import com.ssafy.eolssu.feature.song.edit.component.SheetJangdanSelect
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
import com.ssafy.eolssu.feature.song.edit.component.SheetUploadInfoButton
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
fun SheetImportScreen(state: SheetImportUiState, onBack: () -> Unit, onList: () -> Unit,
    onTitle: (String) -> Unit, onKind: (ImportKind) -> Unit, onInstrument: (String) -> Unit, onJangdan: (String) -> Unit,
    onPick: (SheetFileSource) -> Unit, onSubmit: () -> Unit, onReload: () -> Unit, onDismissError: () -> Unit = {},
    sources: List<SheetFileSource> = sheetFileSources, onJangdanUnsure: () -> Unit = {}) {
    BackHandler(enabled = state.busy) { }
    val editable = !state.busy && !state.picking && !state.expired && state.acceptedId == null
    val colors = EolssuTheme.colors
    // 가져올 곳이 여럿이면 버튼 아래로 메뉴(파일·갤러리)를 펼치고, 하나뿐이면 바로 연다(S15P31E102-997).
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val startPick = { sources.singleOrNull()?.let(onPick) ?: run { sourceMenuOpen = true } }
    val kind = state.kind
    val inputErrors = state.inputErrors()
    val feedback = importFeedback(state.error, inputErrors)?.let { stringResource(it) }
    val showListAction = state.error in setOf("CONVERSION_IN_PROGRESS", "SHEET_LIMIT_EXCEEDED", "IDEMPOTENCY_CONFLICT")
    // 악보 추가 화면처럼 회색 바탕 위 흰 카드로 묶는다(S15P31E102-768).
    val contentMaxWidth = sheetAddContentMaxWidth()
    EolssuScaffold(
        contentMaxWidth = contentMaxWidth,
        containerColor = colors.groupedBackground,
        bottomDivider = false,
        topBar = { EolssuTopBar(stringResource(Res.string.sheet_upload_title), onBack = { if (!state.busy) onBack() }, maxWidth = contentMaxWidth) },
        bottomBar = sheetAddBottomBar(contentMaxWidth) {
            if (state.acceptedId != null) EolssuButton(stringResource(Res.string.sheet_upload_list), onList, qaTarget = QaTarget.SHEET_IMPORT_SCREEN_BUTTON_01)
            else {
                feedback?.let { UploadError(it) }
                EolssuButton(stringResource(Res.string.sheet_upload_send), onSubmit,
                    enabled = editable && !state.loading && !state.catalogFailed, busy = state.busy, qaTarget = QaTarget.SHEET_IMPORT_SCREEN_BUTTON_02)
                if (showListAction) EolssuButton(stringResource(Res.string.sheet_upload_list), onList,
                    style = EolssuButtonStyle.Secondary, borderColor = Color.Transparent, qaTarget = QaTarget.SHEET_IMPORT_SCREEN_BUTTON_03)
            }
        },
    ) {
        val choosingKind = !state.expired && state.acceptedId == null
        // 제목을 입력하다 여백을 누르면 키보드를 내린다(S15P31E102-1079).
        Column(Modifier.fillMaxSize().hideKeyboardOnTapOutside()) {
            // 내 악보 가져오기에서 바로 들어오므로 종류는 여기서 고른다. 정간보일 때만 장단 칸을 보인다(S15P31E102-997).
            // 종류 카드는 위에 고정하고 그 아래 카드만 스크롤한다(S15P31E102-1243).
            if (choosingKind) Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
                UploadCard {
                    UploadLabel(stringResource(Res.string.sheet_upload_kind))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImportKind.entries.forEach { item ->
                            PillChip(stringResource(if (item == ImportKind.Jeongganbo) Res.string.sheetedit_new_kind_jeongganbo else Res.string.sheetedit_new_kind_staff),
                                selected = kind == item, onClick = UserActionLog.click(QaTarget.SHEET_IMPORT_SCREEN_01, { onKind(item) }), modifier = Modifier.weight(1f), enabled = editable)
                        }
                    }
                }
            }
            Column(
                // 위 여백을 스크롤 안에 두어, 올라간 카드가 종류 카드와의 틈에서 잘리게 한다.
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = if (choosingKind) 12.dp else 4.dp, bottom = 24.dp),
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
                        EolssuTextField(qaTarget = QaTarget.SHEET_IMPORT_SCREEN_02,
                            value = state.title,
                            onValueChange = UserActionLog.change(QaTarget.SHEET_IMPORT_SCREEN_02, { if (editable) onTitle(it) }),
                            label = stringResource(Res.string.sheet_upload_name),
                            placeholder = stringResource(Res.string.sheet_upload_name_placeholder),
                            maxLength = InputLimit.SHEET_TITLE,
                            bordered = false,
                            containerColor = colors.groupedBackground,
                            imeAction = ImeAction.Done,
                            error = if (SheetImportField.Title in inputErrors) stringResource(fieldErrorText(SheetImportField.Title)) else null,
                            errorColor = colors.recText,
                        )
                    }
                    if (state.loading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.text) }
                    if (state.catalogFailed) {
                        UploadCard {
                            Text(stringResource(Res.string.sheet_upload_catalog_failed), style = MaterialTheme.typography.bodyMedium, color = colors.textSub)
                            EolssuButton(stringResource(Res.string.sheet_upload_reload), onReload, style = EolssuButtonStyle.Secondary,
                                containerColor = colors.groupedBackground, borderColor = Color.Transparent, qaTarget = QaTarget.SHEET_IMPORT_SCREEN_BUTTON_04)
                        }
                    }
                    if (state.instruments.isNotEmpty()) UploadCard {
                        UploadLabel(stringResource(Res.string.sheet_upload_instrument))
                        // 앱의 다른 악기 선택처럼 이름 앞에 악기 그림을 둔다. 모르는 악기 코드는 그림 없이 이름만 보인다(S15P31E102-1079).
                        // 그림이 들어가 휴대폰 폭에 네 칩이 한 줄에 들어가지 않으므로, 한 칩만 다음 줄에 남지 않게 두 칸씩 같은 폭으로 놓는다.
                        // 칩의 보이지 않는 터치 여백만큼 줄 간격을 줄여 위아래도 좌우와 같은 8dp로 보이게 한다(S15P31E102-1233).
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp - PillChipTouchInset * 2)) {
                            state.instruments.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { item ->
                                        val picture = instrumentOfServerCode(item.code)
                                        PillChip(item.name, selected = state.instrumentId == item.instrumentId, onClick = UserActionLog.click(QaTarget.SHEET_IMPORT_SCREEN_03, { onInstrument(item.instrumentId) }),
                                            modifier = Modifier.weight(1f), enabled = editable,
                                            leading = picture?.let { { InstrumentPicture(it) } })
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        if (SheetImportField.Instrument in inputErrors) UploadError(stringResource(fieldErrorText(SheetImportField.Instrument)))
                    }
                    if (kind == ImportKind.Jeongganbo && state.jangdans.isNotEmpty()) UploadCard {
                        UploadLabel(stringResource(Res.string.sheet_upload_jangdan))
                        Text(stringResource(Res.string.sheet_upload_jangdan_help), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                        SheetJangdanSelect(state.jangdans, state.jangdanId, onJangdan, enabled = editable,
                            unsure = state.jangdanUnsure, onUnsure = onJangdanUnsure.takeIf { state.canChooseJangdanUnsure },
                            isError = SheetImportField.Jangdan in inputErrors)
                        if (SheetImportField.Jangdan in inputErrors) UploadError(stringResource(fieldErrorText(SheetImportField.Jangdan)))
                    }
                    UploadCard {
                        // 잘 읽히는 악보 안내와 파일 제한은 카드 본문 대신 ⓘ 말풍선으로 보인다(S15P31E102-1230·1231).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UploadLabel(stringResource(Res.string.sheet_upload_files))
                            SheetUploadInfoButton()
                        }
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
                            // 다시 고르면 기존 파일을 바꾸므로, 이미 고른 파일이 있으면 '추가'처럼 보이는 + 대신 교체 문구를 보인다.
                            val replacing = state.files.isNotEmpty()
                            val pickLabel = when {
                                state.picking -> Res.string.sheet_upload_reading
                                replacing -> Res.string.sheet_upload_replace
                                else -> Res.string.sheet_upload_pick
                            }
                            EolssuButton(stringResource(pickLabel), startPick,
                                style = EolssuButtonStyle.Secondary, enabled = editable, icon = if (replacing) EolssuIcons.Restart else EolssuIcons.Plus,
                                containerColor = colors.accentTint, contentColor = colors.accentText, borderColor = Color.Transparent, qaTarget = QaTarget.SHEET_IMPORT_SCREEN_BUTTON_05)
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

/**
 * 업로드 버튼 위 하단 안내. 칸에 속하지 않는 서버 오류만 보이고, 칸 입력 오류는 해당 칸 아래에서만 한 번 보인다(S15P31E102-1232).
 * 안내 창으로 보이는 파일 오류도 뺀다. 입력 오류인데 표시할 칸이 없을 때만 일반 입력 안내를 보인다.
 */
internal fun importFeedback(error: String?, inputErrors: Set<SheetImportField>): StringResource? = when {
    error == null || fileProblem(error) != null -> null
    error == "INPUT_REQUIRED" -> if (inputErrors.isEmpty()) importErrorText(error) else null
    else -> importErrorText(error)
}

/** 카드 안 칸 아래에 보이는 빨간 입력 오류 안내. */
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
