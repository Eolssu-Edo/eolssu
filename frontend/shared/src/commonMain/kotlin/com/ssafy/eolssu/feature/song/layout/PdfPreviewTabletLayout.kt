package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.song.PdfPreviewActions
import com.ssafy.eolssu.feature.song.PdfPreviewUiState
import com.ssafy.eolssu.feature.song.component.PdfJangdanPicker
import com.ssafy.eolssu.feature.song.component.PdfPagePreview
import com.ssafy.eolssu.feature.song.component.PdfPageSpec
import com.ssafy.eolssu.feature.song.component.PdfTitleField
import com.ssafy.eolssu.feature.song.component.PdfViewOnlyRow
import com.ssafy.eolssu.feature.song.component.UploadStepBar
import com.ssafy.eolssu.feature.song.pdfPreviewSubtitle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.upload_preview_title
import eolssu.shared.generated.resources.upload_save
import org.jetbrains.compose.resources.stringResource

/**
 * UPLOAD-02 태블릿 배치(와이어프레임 `Tab-PdfPreview`). 세로는 쪽 미리보기(380×538dp) 아래에 이름·장단·안내를 두고 하단에 저장 버튼.
 * 가로·넓은 웹 창([expanded])은 왼쪽 칸에 쪽 미리보기(336×476dp)와 그 왼쪽 세로 줄의 쪽 고르기, 오른쪽 칸에 이름·장단·안내와 맨 아래 저장 버튼.
 */
@Composable
internal fun PdfPreviewTabletLayout(state: PdfPreviewUiState, expanded: Boolean, actions: PdfPreviewActions) {
    val saveButton: @Composable () -> Unit = {
        EolssuButton(
            stringResource(Res.string.upload_save),
            actions.onSave,
            modifier = Modifier.height(TabletButtonHeight),
            enabled = state.canSave || state.saving,
            busy = state.saving,
        )
    }
    val form: @Composable () -> Unit = {
        PdfTitleField(state.title, actions.onTitleChange, large = true)
        PdfJangdanPicker(state.jangdanName, actions.onSelectJangdan, large = true)
        PdfViewOnlyRow(large = true)
    }
    SongTabletScaffold(
        expanded = expanded,
        title = stringResource(Res.string.upload_preview_title),
        onBack = actions.onBack,
        subtitle = pdfPreviewSubtitle(state),
        bottomBar = if (expanded) null else ({ saveButton() }),
    ) { gutter ->
        if (expanded) {
            UploadTwoPaneBody(gutter, step = 2) { sideWidth ->
                PdfPagePreview(state, actions.onSelectPage, Modifier.weight(1f).fillMaxHeight(), PdfPageSpec.TabletLandscape)
                Column(Modifier.width(sideWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    form()
                    Spacer(Modifier.weight(1f))
                    saveButton()
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UploadStepBar(step = 2)
                PdfPagePreview(state, actions.onSelectPage, spec = PdfPageSpec.TabletPortrait)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { form() }
            }
        }
    }
}
