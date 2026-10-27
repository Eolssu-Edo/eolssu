package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.PdfFile
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.song.PdfUploadUiState
import com.ssafy.eolssu.feature.song.formatFileSize
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_kind_pdf
import eolssu.shared.generated.resources.upload_done
import eolssu.shared.generated.resources.upload_file_meta
import eolssu.shared.generated.resources.upload_notice_body
import eolssu.shared.generated.resources.upload_notice_bold
import eolssu.shared.generated.resources.upload_notice_title
import eolssu.shared.generated.resources.upload_pick
import eolssu.shared.generated.resources.upload_pick_hint
import eolssu.shared.generated.resources.upload_pick_title
import eolssu.shared.generated.resources.upload_progress
import eolssu.shared.generated.resources.upload_remove
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 파일 고르기 칸(점선). [large]는 태블릿 크기(모서리 20dp, 아이콘 칸 64dp, 제목 18sp, 버튼 48dp)이고,
 * 높이는 [modifier]로 정한다(세로 360dp, 가로는 왼쪽 칸 전체). 내용은 가운데에 둔다.
 */
@Composable
internal fun PdfPickArea(onPick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .dashedBorder(colors.lineStrong, if (large) 20.dp else 18.dp)
            .padding(horizontal = if (large) 24.dp else 20.dp, vertical = if (large) 32.dp else 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (large) Arrangement.spacedBy(12.dp, Alignment.CenterVertically) else Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(if (large) 64.dp else 56.dp).background(colors.surface, RoundedCornerShape(if (large) 18.dp else 16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EolssuIcons.FileUpload, contentDescription = null, modifier = Modifier.size(26.dp), tint = colors.text)
        }
        Text(
            stringResource(Res.string.upload_pick_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = if (large) 18.sp else 16.sp, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.upload_pick_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = if (large) 13.sp else 12.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        EolssuButton(
            stringResource(Res.string.upload_pick),
            onPick,
            modifier = Modifier.height(if (large) 48.dp else 44.dp),
            style = EolssuButtonStyle.Secondary,
            small = true,
        )
    }
}

/** 고른 파일: 이름 · 크기(다 올리면 쪽 수) · 진행 막대 · 상태 · 빼기 버튼. [large]는 태블릿 여백(16dp)이다. */
@Composable
internal fun PdfFileCard(state: PdfUploadUiState, file: PdfFile, onRemove: () -> Unit, large: Boolean = false) {
    val colors = EolssuTheme.colors
    val uploaded = state.uploaded
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, MaterialTheme.shapes.medium)
            .padding(if (large) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindTile { KindLabel(stringResource(Res.string.mysongs_kind_pdf)) }
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val size = formatFileSize(file.sizeBytes)
            Text(
                if (uploaded != null) stringResource(Res.string.upload_file_meta, size, uploaded.pageCount) else size,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colors.surfaceStrong, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress)
                        .fillMaxHeight()
                        .background(colors.primary, RoundedCornerShape(2.dp)),
                )
            }
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val statusStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                if (uploaded != null) {
                    Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.text)
                    Text(stringResource(Res.string.upload_done), style = statusStyle)
                } else {
                    Text(stringResource(Res.string.upload_progress, "${(state.progress * 100).roundToInt()}%"), style = statusStyle, color = colors.textMuted)
                }
            }
        }
        Surface(onClick = onRemove, modifier = Modifier.size(32.dp), shape = CircleShape, color = colors.surface) {
            Box(contentAlignment = Alignment.Center) {
                Icon(EolssuIcons.Close, stringResource(Res.string.upload_remove), Modifier.size(14.dp), tint = colors.text)
            }
        }
    }
}

/** 노란 안내: PDF는 보기·넘김 전용이라 AI 평가가 되지 않는다. [large]는 태블릿 크기(14sp, 여백 16dp)다. */
@Composable
internal fun PdfViewOnlyNotice(large: Boolean = false) {
    val colors = EolssuTheme.colors
    val bold = stringResource(Res.string.upload_notice_bold)
    val body = stringResource(Res.string.upload_notice_body, bold)
    val text = buildAnnotatedString {
        val start = body.indexOf(bold)
        if (start < 0) {
            append(body)
        } else {
            append(body.substring(0, start))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            append(body.substring(start + bold.length))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.accentSoft, RoundedCornerShape(14.dp))
            .padding(if (large) 16.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EolssuIcons.Info, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(18.dp), tint = colors.text)
        Column {
            val style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = if (large) 14.sp else 13.sp,
                lineHeight = if (large) 22.4.sp else 20.sp,
            )
            Text(
                stringResource(Res.string.upload_notice_title),
                modifier = Modifier.padding(bottom = 2.dp),
                style = style.copy(fontWeight = FontWeight.Bold),
            )
            Text(text, style = style)
        }
    }
}
