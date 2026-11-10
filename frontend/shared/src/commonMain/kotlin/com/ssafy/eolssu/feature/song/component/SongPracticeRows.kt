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
import eolssu.shared.generated.resources.mysongs_no_jangdan
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuLevelBadge
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionFailure
import com.ssafy.eolssu.data.song.SheetConversionStage
import com.ssafy.eolssu.data.song.SheetConversionStatus
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_conversion_building
import eolssu.shared.generated.resources.mysongs_conversion_failed
import eolssu.shared.generated.resources.mysongs_conversion_failed_file
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_score
import eolssu.shared.generated.resources.mysongs_conversion_failed_server
import eolssu.shared.generated.resources.mysongs_conversion_failed_timeout
import eolssu.shared.generated.resources.mysongs_conversion_issues
import eolssu.shared.generated.resources.mysongs_conversion_queued
import eolssu.shared.generated.resources.mysongs_conversion_reading
import eolssu.shared.generated.resources.mysongs_conversion_reading_pages
import eolssu.shared.generated.resources.mysongs_conversion_reimport
import eolssu.shared.generated.resources.mysongs_conversion_retry
import eolssu.shared.generated.resources.jangdan_label
import eolssu.shared.generated.resources.mysongs_delete_label
import eolssu.shared.generated.resources.mysongs_edit
import eolssu.shared.generated.resources.mysongs_kind_jeongganbo
import eolssu.shared.generated.resources.mysongs_meta_updated
import eolssu.shared.generated.resources.mysongs_origin_copied
import eolssu.shared.generated.resources.mysongs_origin_imported
import eolssu.shared.generated.resources.mysongs_status_practice
import eolssu.shared.generated.resources.song_difficulty_number
import org.jetbrains.compose.resources.stringResource

/** 제공 곡 줄 왼쪽 칸 글자: 제목의 첫 글자(앞 공백은 건너뜀). 제목이 비면 빈 문자열. */
internal fun songTileLetter(title: String): String = title.trimStart().firstOrNull()?.toString().orEmpty()

/** 제공 곡 제목 밑 문구: 장단 이름. 목록 응답에 없으면 '장단 없음'이고, 줄에서 옅은 회색으로 보인다([ProvidedSongRow]). */
@Composable
internal fun providedSongMeta(song: SongSummary): String =
    if (song.jangdanName.isBlank()) stringResource(Res.string.mysongs_no_jangdan) else jangdanLabel(song.jangdanName)

/**
 * SONG-03 줄 틀(`.srow`): 흰 바탕, 옅은 테두리, 모서리 14dp, 최소 64dp. [onClick]이 있으면 줄 전체를 누른다.
 * [selected]면 옅은 연두 바탕·연두 테두리다(평가할 곡 고르기 태블릿 가로에서 오른쪽에 정보를 보이는 곡).
 */
@Composable
private fun PracticeRowFrame(modifier: Modifier, onClick: (() -> Unit)? = null, selected: Boolean = false, content: @Composable () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.lineSoft, shape)
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
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

/**
 * 제목 16sp · 보조 13sp(`.srow .t`). 제목이 길면 [songTitleMarquee]로 흘려 보인다.
 * 보조 문구는 [metaColor]·[metaWeight]로 그리며 기본은 회색 보통 굵기다.
 */
@Composable
private fun RowScope.RowText(
    title: String,
    meta: String,
    metaColor: Color = EolssuTheme.colors.textMuted,
    metaWeight: FontWeight? = null,
) {
    Column(Modifier.weight(1f)) {
        Text(
            title,
            modifier = Modifier.songTitleMarquee(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.text,
            maxLines = 1,
        )
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = metaWeight),
            color = metaColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * SONG-03 제공 곡 줄: 첫 글자 칸 · 제목 · 장단(검은 Medium, 없으면 옅은 회색 '장단 없음') · 줄 아래쪽에 맞춘 난이도 배지 · 화살표.
 * 연습곡 고르기에서는 누르면 평가 없는 연습 모드에, 평가할 곡 고르기에서는 곡 상세(태블릿 가로는 오른쪽 칸)로 간다.
 */
@Composable
internal fun ProvidedSongRow(song: SongSummary, meta: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = EolssuTheme.colors
    PracticeRowFrame(modifier, onClick, selected) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowTile { Text(songTileLetter(song.title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp), color = colors.text) }
            val noJangdan = song.jangdanName.isBlank()
            RowText(
                song.title,
                meta,
                metaColor = if (noJangdan) colors.textDisabled else colors.text,
                metaWeight = if (noJangdan) null else FontWeight.Medium,
            )
            Box(Modifier.align(Alignment.Bottom)) {
                val level = song.level
                if (level != null) EolssuLevelBadge(level, small = true)
                else EolssuBadge(stringResource(Res.string.song_difficulty_number, song.difficulty), small = true)
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.textDisabled)
        }
    }
}

/**
 * SONG-03 내 악보 줄: 윗줄은 '정간보' 칸 · 제목 · 보조 문구(누르면 [onOpen])와 '편집' 칩([onEdit]),
 * 아랫줄은 글자 줄에 맞춘 상태 배지와 오른쪽 끝 휴지통([onDelete])이다.
 *
 * 불러오기로 만든 악보는 [conversion] 상태를 따른다. 변환 중이면 '변환 중' 배지만 두고 열기·편집을 막으며,
 * 실패하면 원인과 함께 '다시 시도'([onRetryConversion], 서버가 다시 시도할 수 있다고 할 때만) 또는 '다시 불러오기'([onReimport])를 둔다.
 */
@Composable
internal fun MySheetRow(
    sheet: MySheet,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    conversion: SheetConversion? = null,
    retrying: Boolean = false,
    onRetryConversion: () -> Unit = {},
    onReimport: () -> Unit = {},
) {
    val status = conversion?.status
    val usable = status == null || status == SheetConversionStatus.Done
    PracticeRowFrame(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.weight(1f).clickable(enabled = usable, role = Role.Button, onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RowTile {
                        Text(
                            stringResource(Res.string.mysongs_kind_jeongganbo),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = EolssuTheme.colors.text,
                        )
                    }
                    RowText(sheet.title, sheetMeta(sheet))
                }
                when {
                    usable -> EditChip(stringResource(Res.string.mysongs_edit), onEdit)
                    status == SheetConversionStatus.Failed && conversion?.retryable == true ->
                        EditChip(stringResource(Res.string.mysongs_conversion_retry), onRetryConversion, enabled = !retrying)
                    status == SheetConversionStatus.Failed -> EditChip(stringResource(Res.string.mysongs_conversion_reimport), onReimport)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        conversion == null || usable -> {
                            EolssuBadge(stringResource(Res.string.mysongs_status_practice), style = EolssuBadgeStyle.Play, small = true)
                            val issues = conversion?.issueCount ?: 0
                            if (issues > 0) EolssuBadge(stringResource(Res.string.mysongs_conversion_issues, issues), small = true)
                        }
                        status == SheetConversionStatus.Failed -> {
                            EolssuBadge(stringResource(Res.string.mysongs_conversion_failed), small = true)
                            Text(
                                conversionFailureText(conversion?.failure),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = EolssuTheme.colors.textMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        else -> EolssuBadge(conversionProgressText(conversion ?: SheetConversion(sheet.id, SheetConversionStatus.Queued)), style = EolssuBadgeStyle.Soft, small = true)
                    }
                }
                DeleteButton(stringResource(Res.string.mysongs_delete_label, sheet.title), onDelete)
            }
        }
    }
}

/** '변환 대기 중' · '악보 읽는 중 1/3쪽' · '악보 만드는 중'. 쪽 수를 모르면 쪽 수 없이 쓴다. */
@Composable
private fun conversionProgressText(conversion: SheetConversion): String = when {
    conversion.status == SheetConversionStatus.Queued -> stringResource(Res.string.mysongs_conversion_queued)
    conversion.stage == SheetConversionStage.BuildingScore -> stringResource(Res.string.mysongs_conversion_building)
    conversion.pageCount > 0 -> stringResource(Res.string.mysongs_conversion_reading_pages, conversion.processedPageCount, conversion.pageCount)
    else -> stringResource(Res.string.mysongs_conversion_reading)
}

@Composable
private fun conversionFailureText(failure: SheetConversionFailure?): String = stringResource(
    when (failure) {
        SheetConversionFailure.NoScoreFound -> Res.string.mysongs_conversion_failed_no_score
        SheetConversionFailure.UnsupportedFile -> Res.string.mysongs_conversion_failed_file
        SheetConversionFailure.Timeout -> Res.string.mysongs_conversion_failed_timeout
        SheetConversionFailure.ServerError, null -> Res.string.mysongs_conversion_failed_server
    },
)

/**
 * 휴지통(`.ibtn` 40dp, 아이콘 20dp). 와이어프레임처럼 위아래 9dp · 오른쪽 8dp를 줄 밖으로 내밀어
 * 배지 줄 높이는 그대로 두고 누를 자리만 넓힌다.
 */
@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val height = placeable.height - 18.dp.roundToPx()
                layout(placeable.width - 8.dp.roundToPx(), height) { placeable.place(0, -9.dp.roundToPx()) }
            }
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(EolssuIcons.Trash, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.textMuted)
    }
}

/** 편집 칩(`.chip.sm`): 28dp, 12sp, 흰 바탕에 옅은 테두리. */
@Composable
private fun EditChip(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
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

/** '제공 곡에서 고침 · 세마치장단 · 10월 19일 수정' · '정간보 가져옴 · 영산회상 · 10월 17일 수정'. 장단이 없으면 곡 묶음을 쓴다. */
@Composable
private fun sheetMeta(sheet: MySheet): String {
    val origin = stringResource(
        when (sheet.origin) {
            MySheetOrigin.CopiedSong -> Res.string.mysongs_origin_copied
            MySheetOrigin.Imported -> Res.string.mysongs_origin_imported
        },
    )
    val group = sheet.jangdanName?.let { stringResource(Res.string.jangdan_label, it) } ?: sheet.suiteName
    return listOfNotNull(origin, group, stringResource(Res.string.mysongs_meta_updated, monthDayLabel(sheet.updatedAt))).joinToString(" · ")
}
