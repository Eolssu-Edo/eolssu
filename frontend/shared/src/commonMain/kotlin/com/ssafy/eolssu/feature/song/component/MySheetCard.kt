package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetKind
import com.ssafy.eolssu.core.model.MySheetStatus
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.jangdan_label
import eolssu.shared.generated.resources.mysongs_edit_jeongganbo
import eolssu.shared.generated.resources.mysongs_edit_midi
import eolssu.shared.generated.resources.mysongs_edit_pdf
import eolssu.shared.generated.resources.mysongs_kind_jeongganbo
import eolssu.shared.generated.resources.mysongs_kind_midi
import eolssu.shared.generated.resources.mysongs_kind_pdf
import eolssu.shared.generated.resources.mysongs_meta_jeongganbo
import eolssu.shared.generated.resources.mysongs_meta_jeongganbo_no_jangdan
import eolssu.shared.generated.resources.mysongs_meta_midi
import eolssu.shared.generated.resources.mysongs_meta_midi_done
import eolssu.shared.generated.resources.mysongs_meta_pdf
import eolssu.shared.generated.resources.mysongs_status_evaluable
import eolssu.shared.generated.resources.mysongs_status_needs_review
import eolssu.shared.generated.resources.mysongs_status_view_only
import org.jetbrains.compose.resources.stringResource

/**
 * 내 악보 카드: 종류 칸 · 제목 · 보조 문구 · 화살표(누르면 [onOpen]), 아래 줄에 상태 배지와 편집 버튼([onEdit]).
 */
@Composable
fun MySheetCard(sheet: MySheet, onOpen: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, MaterialTheme.shapes.medium)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindTile { KindLabel(kindLabel(sheet.kind)) }
            Column(Modifier.weight(1f)) {
                Text(
                    sheet.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    sheetMeta(sheet),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.lineStrong)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusBadge(sheet.status)
            EditButton(editLabel(sheet.kind), onEdit)
        }
    }
}

/** 상태 배지(AI 평가 가능 · 확인 후 평가 가능 · 보기 전용). */
@Composable
private fun StatusBadge(status: MySheetStatus) {
    val colors = EolssuTheme.colors
    when (status) {
        MySheetStatus.Evaluable -> SheetBadge(stringResource(Res.string.mysongs_status_evaluable), colors.primary, colors.onPrimary)
        MySheetStatus.NeedsReview -> SheetBadge(stringResource(Res.string.mysongs_status_needs_review), colors.accentSoft, colors.text)
        MySheetStatus.ViewOnly -> SheetBadge(stringResource(Res.string.mysongs_status_view_only), colors.surface, colors.textMuted)
    }
}

@Composable
private fun EditButton(text: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        shape = CircleShape,
        color = colors.background,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(EolssuIcons.Pencil, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun kindLabel(kind: MySheetKind): String = stringResource(
    when (kind) {
        MySheetKind.Jeongganbo -> Res.string.mysongs_kind_jeongganbo
        MySheetKind.Midi -> Res.string.mysongs_kind_midi
        MySheetKind.Pdf -> Res.string.mysongs_kind_pdf
    },
)

@Composable
private fun editLabel(kind: MySheetKind): String = stringResource(
    when (kind) {
        MySheetKind.Jeongganbo -> Res.string.mysongs_edit_jeongganbo
        MySheetKind.Midi -> Res.string.mysongs_edit_midi
        MySheetKind.Pdf -> Res.string.mysongs_edit_pdf
    },
)

/** '직접 만듦 · 세마치장단 · 10월 19일 수정' · '음원 변환 · 확인이 필요한 칸 5개' · 'PDF · 3쪽 · 10월 17일 올림'. */
@Composable
private fun sheetMeta(sheet: MySheet): String {
    val date = monthDayLabel(sheet.updatedAt)
    return when (sheet.kind) {
        MySheetKind.Jeongganbo -> sheet.jangdanName
            ?.let { stringResource(Res.string.mysongs_meta_jeongganbo, stringResource(Res.string.jangdan_label, it), date) }
            ?: stringResource(Res.string.mysongs_meta_jeongganbo_no_jangdan, date)
        MySheetKind.Midi -> sheet.pendingCells?.takeIf { it > 0 }
            ?.let { stringResource(Res.string.mysongs_meta_midi, it) }
            ?: stringResource(Res.string.mysongs_meta_midi_done, date)
        MySheetKind.Pdf -> stringResource(Res.string.mysongs_meta_pdf, sheet.pageCount ?: 0, date)
    }
}
