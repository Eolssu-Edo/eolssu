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
import com.ssafy.eolssu.feature.song.component.PdfFileCard
import com.ssafy.eolssu.feature.song.component.PdfPickArea
import com.ssafy.eolssu.feature.song.component.PdfViewOnlyNotice
import com.ssafy.eolssu.feature.song.component.UploadStepBar
import com.ssafy.eolssu.feature.song.layout.PdfUploadTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.upload_next
import eolssu.shared.generated.resources.upload_step_pick
import eolssu.shared.generated.resources.upload_title
import org.jetbrains.compose.resources.stringResource

/**
 * UPLOAD-01 PDF 악보 올리기(1/2): 파일 고르기 · 올리기 진행 · 보기 전용 안내.
 * 태블릿 세로([WindowWidthClass.Medium])는 같은 순서를 크게, 가로([WindowWidthClass.Expanded])는 왼쪽 파일 고르기 · 오른쪽 올린 파일·안내·다음 버튼 두 칸이다.
 */
@Composable
fun PdfUploadScreen(
    state: PdfUploadUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveFile: () -> Unit,
    onNext: () -> Unit,
) {
    if (widthClass != WindowWidthClass.Compact) {
        PdfUploadTabletLayout(state, widthClass == WindowWidthClass.Expanded, onBack, onPickFile, onRemoveFile, onNext)
        return
    }
    EolssuScaffold(
        topBar = {
            EolssuTopBar(stringResource(Res.string.upload_title), onBack = onBack, subtitle = stringResource(Res.string.upload_step_pick))
        },
        bottomBar = {
            EolssuButton(stringResource(Res.string.upload_next), onNext, enabled = state.canProceed)
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UploadStepBar(step = 1)
            PdfPickArea(onPickFile)
            val file = state.file
            if (file != null) PdfFileCard(state, file, onRemoveFile)
            PdfViewOnlyNotice()
        }
    }
}
