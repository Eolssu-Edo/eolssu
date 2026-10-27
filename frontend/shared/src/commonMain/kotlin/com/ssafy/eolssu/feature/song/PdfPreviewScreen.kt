package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.song.component.PdfJangdanPicker
import com.ssafy.eolssu.feature.song.component.PdfPagePreview
import com.ssafy.eolssu.feature.song.component.PdfTitleField
import com.ssafy.eolssu.feature.song.component.PdfViewOnlyRow
import com.ssafy.eolssu.feature.song.component.UploadStepBar
import com.ssafy.eolssu.feature.song.layout.PdfPreviewTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.upload_preview_title
import eolssu.shared.generated.resources.upload_save
import eolssu.shared.generated.resources.upload_step_preview
import org.jetbrains.compose.resources.stringResource

/** UPLOAD-02 화면의 사용자 행동. */
class PdfPreviewActions(
    val onBack: () -> Unit,
    val onSelectPage: (Int) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onSelectJangdan: (String?) -> Unit,
    val onSave: () -> Unit,
)

/**
 * UPLOAD-02 PDF 미리보기·저장(2/2): 쪽 미리보기 · 악보 이름 · 장단 · 보기 전용 안내.
 * 태블릿 세로([WindowWidthClass.Medium])는 같은 순서를 크게, 가로([WindowWidthClass.Expanded])는 왼쪽 쪽 미리보기 · 오른쪽 이름·장단·저장 두 칸이다.
 */
@Composable
fun PdfPreviewScreen(state: PdfPreviewUiState, widthClass: WindowWidthClass, actions: PdfPreviewActions) {
    if (widthClass != WindowWidthClass.Compact) {
        PdfPreviewTabletLayout(state, widthClass == WindowWidthClass.Expanded, actions)
        return
    }
    EolssuScaffold(
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.upload_preview_title),
                onBack = actions.onBack,
                subtitle = pdfPreviewSubtitle(state),
            )
        },
        bottomBar = {
            EolssuButton(stringResource(Res.string.upload_save), actions.onSave, enabled = state.canSave || state.saving, busy = state.saving)
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UploadStepBar(step = 2)
            PdfPagePreview(state, actions.onSelectPage)
            PdfTitleField(state.title, actions.onTitleChange)
            PdfJangdanPicker(state.jangdanName, actions.onSelectJangdan)
            PdfViewOnlyRow()
        }
    }
}

/** 상단 바 보조 문구 '2 / 2 · 파일 이름'. 파일을 받기 전에는 없다. */
@Composable
internal fun pdfPreviewSubtitle(state: PdfPreviewUiState): String? =
    state.upload?.let { stringResource(Res.string.upload_step_preview, it.fileName) }
