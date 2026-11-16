package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.feature.song.edit.component.sheetAddContentMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.song.SongLoadState
import com.ssafy.eolssu.feature.song.component.DeleteSheetDialog
import com.ssafy.eolssu.feature.song.component.MySheetRow
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.edit.component.KindCard
import com.ssafy.eolssu.feature.song.edit.component.LinkRow
import com.ssafy.eolssu.feature.song.edit.component.SheetEditIcons
import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblemDialog
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_sheet_count
import eolssu.shared.generated.resources.sheetedit_new_copy_title
import eolssu.shared.generated.resources.sheetedit_new_jg_caption
import eolssu.shared.generated.resources.sheetedit_new_staff_caption
import eolssu.shared.generated.resources.sheetedit_new_jg_title
import eolssu.shared.generated.resources.sheetedit_new_question
import eolssu.shared.generated.resources.sheetedit_new_staff_title
import eolssu.shared.generated.resources.sheetedit_new_title
import org.jetbrains.compose.resources.stringResource

/** EDIT-01 아래 내 악보 묶음의 사용자 행동. 줄 동작은 내 악보 탭과 같다. */
class EditNewSheetActions(
    val onOpen: (MySheet) -> Unit = {},
    val onEdit: (MySheet) -> Unit = {},
    val onDelete: (MySheet) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onRetryConversion: (MySheet) -> Unit = {},
    /** 변환에 실패한 악보의 '다시 불러오기'. 이 화면 안에서 안내 창 또는 파일 선택으로 이어진다. */
    val onReimport: (MySheet) -> Unit = {},
    /** 목록을 받지 못했을 때의 '다시 시도'. */
    val onRetryLoad: () -> Unit = {},
)

/**
 * EDIT-01 악보 추가: 같은 크기로 나란히 둔 정간보 가져오기 · 오선보 가져오기 카드와 그 아래 따로 둔 '내 악보·제공 곡 불러오기' 줄.
 * 카드를 누르면 확인 버튼 없이 바로 그 종류의 악보 올리기 화면으로 간다(S15P31E102-997). 파일은 그 화면에서 고르고, 음역은 가져온 뒤 편집기에서 조정한다.
 * 그 아래에 내 악보와 변환 진행 상황(대기·읽는 중·만드는 중·실패)을 내 악보 탭과 같은 줄로 보인다(S15P31E102-881).
 * 파일 형식·촬영 안내는 화면에 늘 두지 않고, 문제가 생겼을 때만 [EditNewUiState.problem] 안내 창으로 알린다.
 * 태블릿 가로에서는 본문을 [sheetAddContentMaxWidth]로 넓힌다(S15P31E102-978).
 */
@Composable
fun EditNewScreen(
    state: EditNewUiState,
    onBack: () -> Unit,
    /** 종류 카드를 눌렀다. 고른 종류의 악보 올리기 화면으로 간다. */
    onSelectKind: (ImportKind) -> Unit,
    onLoadExisting: () -> Unit,
    onOpenDevicePick: () -> Unit,
    onDismissProblem: () -> Unit = {},
    sheetActions: EditNewSheetActions = EditNewSheetActions(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val contentMaxWidth = sheetAddContentMaxWidth()
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = contentMaxWidth,
        containerColor = EolssuTheme.colors.groupedBackground,
        topBar = {
            EolssuTopBar(stringResource(Res.string.sheetedit_new_title), onBack = onBack, maxWidth = contentMaxWidth)
        },
        bottomDivider = false,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(Res.string.sheetedit_new_question))
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KindCard(
                    stringResource(Res.string.sheetedit_new_jg_title),
                    stringResource(Res.string.sheetedit_new_jg_caption),
                    SheetEditIcons.Jeongganbo,
                    onClick = { onSelectKind(ImportKind.Jeongganbo) },
                    modifier = Modifier.weight(1f).heightIn(min = KindCardMinHeight).fillMaxHeight(),
                )
                KindCard(
                    stringResource(Res.string.sheetedit_new_staff_title),
                    stringResource(Res.string.sheetedit_new_staff_caption),
                    SheetEditIcons.StaffNotes,
                    onClick = { onSelectKind(ImportKind.Staff) },
                    modifier = Modifier.weight(1f).heightIn(min = KindCardMinHeight).fillMaxHeight(),
                )
            }
            // 파일 업로드와 다른 기능이라 두 카드와 조금 떼어 따로 둔다.
            LinkRow(
                SheetEditIcons.MySheet,
                stringResource(Res.string.sheetedit_new_copy_title),
                onClick = onLoadExisting,
                modifier = Modifier.padding(top = 8.dp),
            )
            MySheetsSection(state, sheetActions)
        }
    }
    state.problem?.let { problem ->
        SheetFileProblemDialog(
            problem,
            onRepick = {
                onDismissProblem()
                onOpenDevicePick()
            },
            onDismiss = onDismissProblem,
        )
    }
    state.deleting?.let { DeleteSheetDialog(it, sheetActions.onConfirmDelete, sheetActions.onCancelDelete) }
}

/**
 * 내 악보 묶음: '내 악보 n개' 제목 · 악보 줄. 악보가 없으면 두지 않고, 목록을 받지 못했으면 '다시 시도' 안내를 둔다.
 * 처음 받는 동안에는 위 가져오기 칸이 흔들리지 않게 아무것도 두지 않는다.
 */
@Composable
private fun MySheetsSection(state: EditNewUiState, actions: EditNewSheetActions) {
    val sheets = state.mine.sheets.orEmpty()
    if (sheets.isEmpty()) {
        if (state.sheetLoad == SongLoadState.Failed) SongLoadNotice(SongLoadState.Failed, actions.onRetryLoad)
        return
    }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // '내 악보 n개'. 내 악보 탭의 개수 문구를 제목으로 쓴다.
        SectionLabel(stringResource(Res.string.mysongs_sheet_count, sheets.size))
        sheets.forEach { sheet ->
            MySheetRow(
                sheet,
                onOpen = { actions.onOpen(sheet) },
                onEdit = { actions.onEdit(sheet) },
                onDelete = { actions.onDelete(sheet) },
                conversion = state.mine.conversionOf(sheet),
                retrying = sheet.id in state.mine.retrying,
                onRetryConversion = { actions.onRetryConversion(sheet) },
                onReimport = { actions.onReimport(sheet) },
                bordered = false,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
}

/** 가져오기 종류 카드의 최소 높이. 아이콘 원과 이름·설명 두 줄 사이에 여백이 남게 한다. */
private val KindCardMinHeight = 150.dp
