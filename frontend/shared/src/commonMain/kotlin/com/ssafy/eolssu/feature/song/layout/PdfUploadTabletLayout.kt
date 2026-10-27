package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.song.PdfUploadUiState
import com.ssafy.eolssu.feature.song.component.PdfFileCard
import com.ssafy.eolssu.feature.song.component.PdfPickArea
import com.ssafy.eolssu.feature.song.component.PdfViewOnlyNotice
import com.ssafy.eolssu.feature.song.component.UploadStepBar
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.upload_next
import eolssu.shared.generated.resources.upload_step_pick
import eolssu.shared.generated.resources.upload_title
import org.jetbrains.compose.resources.stringResource

/**
 * UPLOAD-01 태블릿 배치(와이어프레임 `Tab-PdfUpload`). 세로는 휴대폰과 같은 순서로 쌓고 파일 고르기 칸을 360dp로 키우며 하단에 '다음 · 미리보기'를 둔다.
 * 가로·넓은 웹 창([expanded])은 왼쪽에 파일 고르기 칸, 오른쪽 칸에 올린 파일·안내와 맨 아래 '다음 · 미리보기'를 둔다.
 */
@Composable
internal fun PdfUploadTabletLayout(
    state: PdfUploadUiState,
    expanded: Boolean,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveFile: () -> Unit,
    onNext: () -> Unit,
) {
    val nextButton: @Composable () -> Unit = {
        EolssuButton(
            stringResource(Res.string.upload_next),
            onNext,
            modifier = Modifier.height(TabletButtonHeight),
            enabled = state.canProceed,
        )
    }
    SongTabletScaffold(
        expanded = expanded,
        title = stringResource(Res.string.upload_title),
        onBack = onBack,
        subtitle = stringResource(Res.string.upload_step_pick),
        bottomBar = if (expanded) null else ({ nextButton() }),
    ) { gutter ->
        val file = state.file
        if (expanded) {
            UploadTwoPaneBody(gutter, step = 1) { sideWidth ->
                PdfPickArea(onPickFile, Modifier.weight(1f).fillMaxHeight(), large = true)
                Column(Modifier.width(sideWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (file != null) PdfFileCard(state, file, onRemoveFile, large = true)
                    PdfViewOnlyNotice(large = true)
                    Spacer(Modifier.weight(1f))
                    nextButton()
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
                UploadStepBar(step = 1)
                PdfPickArea(onPickFile, Modifier.height(360.dp), large = true)
                if (file != null) PdfFileCard(state, file, onRemoveFile, large = true)
                PdfViewOnlyNotice(large = true)
            }
        }
    }
}

/** 가로 두 칸이 이보다 낮아지면 창 대신 본문을 세로로 스크롤한다(납작한 웹 창·화면 분할). */
private val UploadPaneMinHeight: Dp = 520.dp

/**
 * PDF 올리기·미리보기 가로 본문: 진행 막대 아래에 왼쪽 칸(남는 너비)과 오른쪽 칸([content]가 받는 너비, 와이어프레임 440dp)을 24dp 띄워 둔다.
 * 두 칸은 남은 높이를 채우고, 창이 [UploadPaneMinHeight]보다 낮으면 그 높이를 지킨 채 세로로 스크롤한다.
 */
@Composable
internal fun UploadTwoPaneBody(gutter: Dp, step: Int, content: @Composable RowScope.(sideWidth: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxOf(maxHeight, UploadPaneMinHeight)
        // 와이어프레임은 1120dp 너비에 오른쪽 칸 440dp. 좁은 가로 창에서는 왼쪽 칸이 너무 좁아지지 않게 줄인다.
        val sideWidth = ((maxWidth - gutter * 2) * 0.4f).coerceIn(340.dp, 440.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(horizontal = gutter)
                    .padding(top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UploadStepBar(step = step)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    content(sideWidth)
                }
            }
        }
    }
}
