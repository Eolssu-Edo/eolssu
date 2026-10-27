package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuChip
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.song.component.FilledTextField
import com.ssafy.eolssu.feature.song.component.SheetBadge
import com.ssafy.eolssu.feature.song.component.UploadStepBar
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.upload_jangdan_label
import eolssu.shared.generated.resources.upload_jangdan_optional
import eolssu.shared.generated.resources.upload_jangdan_unknown
import eolssu.shared.generated.resources.upload_page_caption
import eolssu.shared.generated.resources.upload_page_preview
import eolssu.shared.generated.resources.upload_page_thumb
import eolssu.shared.generated.resources.upload_preview_title
import eolssu.shared.generated.resources.upload_save
import eolssu.shared.generated.resources.upload_step_preview
import eolssu.shared.generated.resources.upload_title_label
import eolssu.shared.generated.resources.upload_view_only_badge
import eolssu.shared.generated.resources.upload_view_only_hint
import org.jetbrains.compose.resources.stringResource

/** UPLOAD-02 화면의 사용자 행동. */
class PdfPreviewActions(
    val onBack: () -> Unit,
    val onSelectPage: (Int) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onSelectJangdan: (String?) -> Unit,
    val onSave: () -> Unit,
)

/** UPLOAD-02 PDF 미리보기·저장(2/2): 쪽 미리보기 · 악보 이름 · 장단 · 보기 전용 안내. */
@Composable
fun PdfPreviewScreen(state: PdfPreviewUiState, actions: PdfPreviewActions) {
    EolssuScaffold(
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.upload_preview_title),
                onBack = actions.onBack,
                subtitle = state.upload?.let { stringResource(Res.string.upload_step_preview, it.fileName) },
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
            PagePreview(state, actions.onSelectPage)
            TitleField(state.title, actions.onTitleChange)
            JangdanPicker(state.jangdanName, actions.onSelectJangdan)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetBadge(stringResource(Res.string.upload_view_only_badge), EolssuTheme.colors.surface, EolssuTheme.colors.text)
                Text(
                    stringResource(Res.string.upload_view_only_hint),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                )
            }
        }
    }
}

/** 고른 쪽의 미리보기 자리와 쪽 고르기 버튼. 실제 쪽 이미지는 PDF 보기 API가 생기면 붙인다. */
@Composable
private fun PagePreview(state: PdfPreviewUiState, onSelectPage: (Int) -> Unit) {
    val colors = EolssuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val pageLabel = stringResource(Res.string.upload_page_preview, state.page)
        Column(
            Modifier
                .size(width = 190.dp, height = 268.dp)
                .shadow(8.dp, RectangleShape, ambientColor = PageShadow, spotColor = PageShadow)
                .background(colors.background)
                .border(1.dp, colors.line)
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .semantics { contentDescription = pageLabel },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.6f).height(8.dp).background(colors.surfaceStrong, RoundedCornerShape(4.dp)))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, colors.line)
                    .drawBehind {
                        val line = 1.dp.toPx()
                        var x = 25.dp.toPx()
                        while (x < size.width) {
                            drawLine(colors.line, Offset(x, 0f), Offset(x, size.height), line)
                            x += 26.dp.toPx()
                        }
                        var y = size.height - 20.dp.toPx()
                        while (y > 0f) {
                            drawLine(colors.surfaceStrong, Offset(0f, y), Offset(size.width, y), line)
                            y -= 20.dp.toPx()
                        }
                    },
            )
            Text(
                stringResource(Res.string.upload_page_caption, state.page),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (page in 1..state.pageCount) {
                val selected = page == state.page
                val thumbLabel = stringResource(Res.string.upload_page_thumb, page)
                Surface(
                    onClick = { onSelectPage(page) },
                    modifier = Modifier.size(width = 44.dp, height = 60.dp).semantics {
                        contentDescription = thumbLabel
                        this.selected = selected
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = colors.background,
                    contentColor = colors.text,
                    border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.line),
                ) {
                    Box(Modifier.padding(bottom = 4.dp), contentAlignment = Alignment.BottomCenter) {
                        Text("$page", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

private val PageShadow = Color.Black.copy(alpha = 0.08f)

@Composable
private fun TitleField(title: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.upload_title_label),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        FilledTextField(title, onChange)
    }
}

@Composable
private fun JangdanPicker(selected: String?, onSelect: (String?) -> Unit) {
    val label = stringResource(Res.string.upload_jangdan_label)
    val optional = stringResource(Res.string.upload_jangdan_optional)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            buildAnnotatedString {
                append(label)
                append(' ')
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = EolssuTheme.colors.textMuted)) { append(optional) }
            },
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EolssuChip(stringResource(Res.string.upload_jangdan_unknown), selected = selected == null, onClick = { onSelect(null) })
            PdfJangdanChoices.forEach { name ->
                EolssuChip(name, selected = selected == name, onClick = { onSelect(name) })
            }
        }
    }
}
