package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetKind
import com.ssafy.eolssu.core.model.MySheetStatus
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuLevelBadge
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.jangdan_label
import eolssu.shared.generated.resources.mysongs_edit_jeongganbo
import eolssu.shared.generated.resources.mysongs_edit_midi
import eolssu.shared.generated.resources.mysongs_edit_pdf
import eolssu.shared.generated.resources.mysongs_evaluable_badge
import eolssu.shared.generated.resources.mysongs_kind_jeongganbo
import eolssu.shared.generated.resources.mysongs_kind_midi
import eolssu.shared.generated.resources.mysongs_kind_pdf
import eolssu.shared.generated.resources.mysongs_meta_jeongganbo
import eolssu.shared.generated.resources.mysongs_meta_jeongganbo_no_jangdan
import eolssu.shared.generated.resources.mysongs_meta_midi
import eolssu.shared.generated.resources.mysongs_meta_midi_done
import eolssu.shared.generated.resources.mysongs_meta_pdf
import eolssu.shared.generated.resources.mysongs_practice_only_badge
import eolssu.shared.generated.resources.mysongs_status_evaluable
import eolssu.shared.generated.resources.mysongs_status_needs_review
import eolssu.shared.generated.resources.mysongs_status_view_only
import eolssu.shared.generated.resources.song_difficulty_number
import org.jetbrains.compose.resources.stringResource

/** 제공 곡 줄 왼쪽 칸 글자: 제목의 첫 글자(앞 공백은 건너뜀). 제목이 비면 빈 문자열. */
internal fun songTileLetter(title: String): String = title.trimStart().firstOrNull()?.toString().orEmpty()

/** 제공 곡 배지: 평가 곡은 'AI 평가'(`.badge.play`), 아니면 '연습 전용'(`.badge.lite`). */
internal fun providedSongBadgeStyle(evaluable: Boolean): EolssuBadgeStyle =
    if (evaluable) EolssuBadgeStyle.Play else EolssuBadgeStyle.Outline

/** 내 악보 상태 배지: AI 평가 가능 `.badge.play` · 확인 후 평가 가능 `.badge` · 보기 전용 `.badge.lite`. */
internal fun sheetBadgeStyle(status: MySheetStatus): EolssuBadgeStyle = when (status) {
    MySheetStatus.Evaluable -> EolssuBadgeStyle.Play
    MySheetStatus.NeedsReview -> EolssuBadgeStyle.Soft
    MySheetStatus.ViewOnly -> EolssuBadgeStyle.Outline
}

/** SONG-03 줄 틀(`.srow`): 흰 바탕, 옅은 테두리, 모서리 14dp, 최소 64dp. [onClick]이 있으면 줄 전체를 누른다. */
@Composable
private fun PracticeRowFrame(modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.lineSoft, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** 44dp 연두 칸(`.tile`). */
@Composable
private fun RowTile(content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).background(EolssuTheme.colors.accentTint, RoundedCornerShape(12.dp)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 제목 16sp · 보조 13sp(`.srow .t`). */
@Composable
private fun RowScope.RowText(title: String, meta: String) {
    Column(Modifier.weight(1f)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = EolssuTheme.colors.textMuted)
    }
}

/**
 * SONG-03 제공 곡 줄: 첫 글자 칸 · 제목 · 장단(연습 전용이면 '· 연습 전용') · 'AI 평가'/'연습 전용'과 난이도 배지 · 화살표.
 * 누르면 [onClick]으로 평가 없는 연습 모드에 간다.
 */
@Composable
internal fun ProvidedSongRow(song: SongSummary, meta: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    PracticeRowFrame(modifier, onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowTile { Text(songTileLetter(song.title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp), color = colors.text) }
            RowText(song.title, meta)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EolssuBadge(
                    stringResource(if (song.evaluable) Res.string.mysongs_evaluable_badge else Res.string.mysongs_practice_only_badge),
                    style = providedSongBadgeStyle(song.evaluable),
                    small = true,
                )
                val level = song.level
                if (level != null) EolssuLevelBadge(level, small = true)
                else EolssuBadge(stringResource(Res.string.song_difficulty_number, song.difficulty), small = true)
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.textDisabled)
        }
    }
}

/**
 * SONG-03 내 악보 줄: 윗줄은 종류 칸 · 제목 · 보조 문구(누르면 [onOpen])와 편집 칩([onEdit]),
 * 아랫줄은 글자 줄에 맞춘 상태 배지다.
 */
@Composable
internal fun MySheetRow(sheet: MySheet, onOpen: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    PracticeRowFrame(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RowTile { KindLabel(kindLabel(sheet.kind)) }
                    RowText(sheet.title, sheetMeta(sheet))
                }
                EditChip(editLabel(sheet.kind), onEdit)
            }
            EolssuBadge(
                statusLabel(sheet.status),
                modifier = Modifier.padding(start = 56.dp),
                style = sheetBadgeStyle(sheet.status),
                small = true,
            )
        }
    }
}

/** 편집 칩(`.chip.sm`): 28dp, 12sp, 흰 바탕에 옅은 테두리. */
@Composable
private fun EditChip(text: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 28.dp),
        shape = CircleShape,
        color = colors.surface,
        contentColor = colors.textSub,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun statusLabel(status: MySheetStatus): String = stringResource(
    when (status) {
        MySheetStatus.Evaluable -> Res.string.mysongs_status_evaluable
        MySheetStatus.NeedsReview -> Res.string.mysongs_status_needs_review
        MySheetStatus.ViewOnly -> Res.string.mysongs_status_view_only
    },
)

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
