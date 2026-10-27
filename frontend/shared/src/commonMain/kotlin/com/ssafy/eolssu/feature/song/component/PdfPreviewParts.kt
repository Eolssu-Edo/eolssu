package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuChip
import com.ssafy.eolssu.feature.song.PdfJangdanChoices
import com.ssafy.eolssu.feature.song.PdfPreviewUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.upload_jangdan_label
import eolssu.shared.generated.resources.upload_jangdan_optional
import eolssu.shared.generated.resources.upload_jangdan_unknown
import eolssu.shared.generated.resources.upload_page_caption
import eolssu.shared.generated.resources.upload_page_preview
import eolssu.shared.generated.resources.upload_page_thumb
import eolssu.shared.generated.resources.upload_title_label
import eolssu.shared.generated.resources.upload_view_only_badge
import eolssu.shared.generated.resources.upload_view_only_hint
import org.jetbrains.compose.resources.stringResource

/**
 * 쪽 미리보기 크기. 휴대폰은 [Phone], 태블릿 세로는 [TabletPortrait], 가로는 [TabletLandscape](쪽 고르기를 미리보기 왼쪽 세로 줄로).
 * 값은 와이어프레임 `PdfPreview`·`Tab-PdfPreview`를 따른다.
 */
internal class PdfPageSpec(
    val pageWidth: Dp,
    val pageHeight: Dp,
    val large: Boolean,
    val thumbsBeside: Boolean,
) {
    companion object {
        val Phone = PdfPageSpec(190.dp, 268.dp, large = false, thumbsBeside = false)
        val TabletPortrait = PdfPageSpec(380.dp, 538.dp, large = true, thumbsBeside = false)
        val TabletLandscape = PdfPageSpec(336.dp, 476.dp, large = true, thumbsBeside = true)
    }
}

/** 고른 쪽의 미리보기 자리와 쪽 고르기 버튼. 실제 쪽 이미지는 PDF 보기 API가 생기면 붙인다. */
@Composable
internal fun PdfPagePreview(
    state: PdfPreviewUiState,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    spec: PdfPageSpec = PdfPageSpec.Phone,
) {
    val colors = EolssuTheme.colors
    val gap = if (spec.large) 16.dp else 12.dp
    val boxModifier = modifier
        .fillMaxWidth()
        .background(colors.surface, RoundedCornerShape(if (spec.large) 20.dp else 18.dp))
        .padding(if (spec.large) 20.dp else 16.dp)
    if (spec.thumbsBeside) {
        Row(
            boxModifier,
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PageThumbs(state, onSelectPage, spec) }
            PageSheet(state.page, spec)
        }
    } else {
        Column(
            boxModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
        ) {
            PageSheet(state.page, spec)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PageThumbs(state, onSelectPage, spec) }
        }
    }
}

@Composable
private fun PageSheet(page: Int, spec: PdfPageSpec) {
    val colors = EolssuTheme.colors
    val large = spec.large
    val pageLabel = stringResource(Res.string.upload_page_preview, page)
    Column(
        Modifier
            .size(width = spec.pageWidth, height = spec.pageHeight)
            .shadow(8.dp, RectangleShape, ambientColor = PageShadow, spotColor = PageShadow)
            .background(colors.background)
            .border(1.dp, colors.line)
            .padding(horizontal = if (large) 20.dp else 16.dp, vertical = if (large) 22.dp else 18.dp)
            .semantics { contentDescription = pageLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else 10.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.6f).height(if (large) 9.dp else 8.dp).background(colors.surfaceStrong, RoundedCornerShape(4.dp)))
        val columnStep = if (large) 32.dp else 26.dp
        val rowStep = if (large) 24.dp else 20.dp
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, colors.line)
                .drawBehind {
                    val line = 1.dp.toPx()
                    var x = (columnStep - 1.dp).toPx()
                    while (x < size.width) {
                        drawLine(colors.line, Offset(x, 0f), Offset(x, size.height), line)
                        x += columnStep.toPx()
                    }
                    var y = size.height - rowStep.toPx()
                    while (y > 0f) {
                        drawLine(colors.surfaceStrong, Offset(0f, y), Offset(size.width, y), line)
                        y -= rowStep.toPx()
                    }
                },
        )
        Text(
            stringResource(Res.string.upload_page_caption, page),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (large) 11.sp else 10.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageThumbs(state: PdfPreviewUiState, onSelectPage: (Int) -> Unit, spec: PdfPageSpec) {
    val colors = EolssuTheme.colors
    for (page in 1..state.pageCount) {
        val selected = page == state.page
        val thumbLabel = stringResource(Res.string.upload_page_thumb, page)
        Surface(
            onClick = { onSelectPage(page) },
            modifier = Modifier
                .size(width = if (spec.large) 48.dp else 44.dp, height = if (spec.large) 66.dp else 60.dp)
                .semantics {
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

private val PageShadow = Color.Black.copy(alpha = 0.08f)

/** '악보 이름' 입력칸. [large]는 태블릿(높이 52dp)이다. */
@Composable
internal fun PdfTitleField(title: String, onChange: (String) -> Unit, large: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.upload_title_label),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        FilledTextField(title, onChange, if (large) Modifier.height(52.dp) else Modifier)
    }
}

/** 장단 고르기(선택). '모름'은 null. [large]는 태블릿(칩 높이 38dp, 제목과 칩 사이 8dp)이다. */
@Composable
internal fun PdfJangdanPicker(selected: String?, onSelect: (String?) -> Unit, large: Boolean = false) {
    val label = stringResource(Res.string.upload_jangdan_label)
    val optional = stringResource(Res.string.upload_jangdan_optional)
    val chipModifier = if (large) Modifier.height(38.dp) else Modifier
    val chipGap = if (large) 8.dp else 6.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (large) 8.dp else 6.dp)) {
        Text(
            buildAnnotatedString {
                append(label)
                append(' ')
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = EolssuTheme.colors.textMuted)) { append(optional) }
            },
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(chipGap), verticalArrangement = Arrangement.spacedBy(chipGap)) {
            EolssuChip(stringResource(Res.string.upload_jangdan_unknown), selected = selected == null, onClick = { onSelect(null) }, modifier = chipModifier)
            PdfJangdanChoices.forEach { name ->
                EolssuChip(name, selected = selected == name, onClick = { onSelect(name) }, modifier = chipModifier)
            }
        }
    }
}

/** '보기 전용' 배지와 평가 안내 한 줄. [large]는 태블릿(13sp)이다. */
@Composable
internal fun PdfViewOnlyRow(large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetBadge(stringResource(Res.string.upload_view_only_badge), EolssuTheme.colors.surface, EolssuTheme.colors.text)
        Text(
            stringResource(Res.string.upload_view_only_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = if (large) 13.sp else 12.sp),
            color = EolssuTheme.colors.textMuted,
        )
    }
}
