package com.ssafy.eolssu.feature.song.component

import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.ssafy.eolssu.feature.song.ConversionReadingClock
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import com.ssafy.eolssu.feature.song.ConversionStep
import com.ssafy.eolssu.feature.song.conversionFailureRow
import com.ssafy.eolssu.feature.song.conversionProgress
import com.ssafy.eolssu.feature.song.conversionStep
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.ssafy.eolssu.data.song.SheetConversionStatus
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_conversion_building
import eolssu.shared.generated.resources.mysongs_conversion_failed
import eolssu.shared.generated.resources.mysongs_conversion_failure_reason
import eolssu.shared.generated.resources.mysongs_conversion_failure_reason_closed
import eolssu.shared.generated.resources.mysongs_conversion_failure_reason_open
import eolssu.shared.generated.resources.mysongs_conversion_issues
import eolssu.shared.generated.resources.mysongs_conversion_queued
import eolssu.shared.generated.resources.mysongs_conversion_reading
import eolssu.shared.generated.resources.mysongs_conversion_reading_pages
import eolssu.shared.generated.resources.mysongs_conversion_reading_total
import eolssu.shared.generated.resources.mysongs_conversion_reimport
import eolssu.shared.generated.resources.mysongs_conversion_retry
import eolssu.shared.generated.resources.jangdan_label
import eolssu.shared.generated.resources.mysongs_delete_label
import eolssu.shared.generated.resources.mysongs_edit
import eolssu.shared.generated.resources.mysongs_meta_updated
import eolssu.shared.generated.resources.mysongs_origin_copied
import eolssu.shared.generated.resources.mysongs_status_practice
import eolssu.shared.generated.resources.mysongs_status_review
import eolssu.shared.generated.resources.song_difficulty_number
import org.jetbrains.compose.resources.stringResource

/** 제공 곡 제목 밑 문구: 장단 이름. 목록 응답에 없으면 공통 '장단 미지정'을 옅은 회색으로 보인다([ProvidedSongRow]). */
@Composable
internal fun providedSongMeta(song: SongSummary): String =
    jangdanLabel(song.jangdanName)

/**
 * SONG-03 줄 틀(`.srow`): 흰 바탕, 옅은 테두리, 모서리 14dp, 최소 64dp. [onClick]이 있으면 줄 전체를 누른다.
 * [selected]면 테두리 없는 연한 연두 바탕이다(평가할 곡 고르기 태블릿 가로에서 오른쪽에 정보를 보이는 곡).
 */
@Composable
private fun PracticeRowFrame(
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    bordered: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (selected) colors.accentTint else colors.surface)
            .then(if (bordered && !selected) Modifier.border(1.dp, colors.lineSoft, shape) else Modifier)
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.CenterStart,
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
 * SONG-03 제공 곡 줄: 제목 · 장단(검은 Medium, 없으면 옅은 회색 '장단 미지정') · 세로 가운데의 난이도 배지 · 화살표.
 * 연습곡 고르기에서는 누르면 평가 없는 연습 모드에, 평가할 곡 고르기에서는 곡 상세(태블릿 가로는 오른쪽 칸)로 간다.
 */
@Composable
internal fun ProvidedSongRow(
    song: SongSummary,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    bordered: Boolean = true,
) {
    val colors = EolssuTheme.colors
    PracticeRowFrame(modifier, onClick, selected, bordered) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val noJangdan = song.jangdanName.isBlank()
            RowText(
                song.title,
                meta,
                metaColor = if (noJangdan) colors.textDisabled else colors.text,
                metaWeight = if (noJangdan) null else FontWeight.Medium,
            )
            val level = song.level
            if (level != null) EolssuLevelBadge(level, small = true)
            else EolssuBadge(stringResource(Res.string.song_difficulty_number, song.difficulty), small = true)
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.textDisabled)
        }
    }
}

/**
 * SONG-03 내 악보 줄: 윗줄은 제목 · 보조 문구(누르면 [onOpen])와 '편집' 칩([onEdit]),
 * 아랫줄은 글자 줄에 맞춘 상태 배지와 오른쪽 끝 휴지통([onDelete])이다.
 *
 * 불러오기로 만든 악보는 [conversion] 상태를 따른다. 변환 중이면 '변환 중' 배지만 두고 열기·편집을 막으며,
 * 실패하면 원인·할 일 한 줄과 함께 '다시 시도'([onRetryConversion], 서버가 다시 시도할 수 있다고 할 때만) 또는 '다시 불러오기'([onReimport])를 둔다.
 * [onOpenConversion]이 있으면 변환 중·실패한 줄을 눌러 변환 대기 화면을 다시 연다(S15P31E102-998).
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
    bordered: Boolean = true,
    onOpenConversion: (() -> Unit)? = null,
) {
    val status = conversion?.status
    val usable = status == null || status == SheetConversionStatus.Done
    // 실패 사유는 처음엔 접어 두고 '실패 사유'를 누를 때만 아래에 펼친다(S15P31E102-1052).
    var reasonOpen by rememberSaveable(sheet.id) { mutableStateOf(false) }
    // 제공 곡 줄처럼 카드 전체가 둥글게 눌려 악보를 연다. 편집·휴지통은 각자의 모양으로 따로 눌린다(S15P31E102-968).
    PracticeRowFrame(modifier, onClick = if (usable) onOpen else onOpenConversion, bordered = bordered) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 제목 줄 오른쪽에 편집(연두 채움)과 휴지통을 나란히 두고, 아래 줄에는 상태 배지만 둔다(S15P31E102-768).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowText(sheet.title, sheetMeta(sheet))
                // 편집 칩·휴지통의 48dp 누르는 영역끼리 맞닿게 간격 없이 둔다. 휴지통 영역의 여백 7dp가 보이는 간격이 된다(S15P31E102-971).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        usable -> EditChip(stringResource(Res.string.mysongs_edit), onEdit)
                        status == SheetConversionStatus.Failed && conversion?.retryable == true ->
                            EditChip(stringResource(Res.string.mysongs_conversion_retry), onRetryConversion, enabled = !retrying)
                        status == SheetConversionStatus.Failed -> EditChip(stringResource(Res.string.mysongs_conversion_reimport), onReimport)
                    }
                    DeleteButton(stringResource(Res.string.mysongs_delete_label, sheet.title), onDelete)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        usable -> {
                            if (sheet.practiceReady) {
                                EolssuBadge(stringResource(Res.string.mysongs_status_practice), style = EolssuBadgeStyle.Play, small = true)
                            } else {
                                EolssuBadge(stringResource(Res.string.mysongs_status_review), small = true)
                            }
                            val issues = conversion?.issueCount ?: 0
                            if (!sheet.practiceReady && issues > 0) EolssuBadge(stringResource(Res.string.mysongs_conversion_issues, issues), small = true)
                        }
                        status == SheetConversionStatus.Failed -> {
                            EolssuBadge(stringResource(Res.string.mysongs_conversion_failed), style = EolssuBadgeStyle.Failed, small = true)
                            FailureReasonToggle(open = reasonOpen, onToggle = { reasonOpen = !reasonOpen })
                        }
                        else -> EolssuBadge(conversionProgressText(conversion ?: SheetConversion(sheet.id, SheetConversionStatus.Queued)), style = EolssuBadgeStyle.Soft, small = true)
                    }
                }
            }
            if (status == SheetConversionStatus.Failed) {
                AnimatedVisibility(reasonOpen) {
                    Text(
                        stringResource(conversionFailureRow(conversion)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EolssuTheme.colors.recSoft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = EolssuTheme.colors.recText,
                    )
                }
            }
            if (!usable && status != SheetConversionStatus.Failed) {
                ConversionProgressBar(conversion ?: SheetConversion(sheet.id, SheetConversionStatus.Queued))
            }
        }
    }
}

/** '실패 사유 ▾' 글자 버튼. 누를 때마다 아래 사유 칸을 펼치고 접으며, 화살표가 펼침 상태를 따라 돈다. 누르는 영역은 48dp. */
@Composable
private fun FailureReasonToggle(open: Boolean, onToggle: () -> Unit) {
    val colors = EolssuTheme.colors
    val turn by animateFloatAsState(if (open) 180f else 0f, animationSpec = tween(200))
    val state = stringResource(if (open) Res.string.mysongs_conversion_failure_reason_open else Res.string.mysongs_conversion_failure_reason_closed)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(Res.string.mysongs_conversion_failure_reason),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.recText,
        )
        Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp).rotate(turn), tint = colors.recText)
    }
}

/**
 * 변환 진행 막대: 옅은 선 위에 연두로 채운 6dp 막대. 서버가 퍼센트를 주지 않아 숫자는 쓰지 않고 [conversionProgress]의 어림값만 그린다.
 * '읽는 중'에는 서버 응답이 바뀌지 않아도 읽기 시작부터 지난 시간([ConversionReadingClock])으로 1초마다 조금씩 채운다(S15P31E102-1064).
 * 어림이 잠깐 줄어도 뒤로 가지 않고, 채운 부분 위로 밝은 빛이 지나가 멈추지 않았음을 보인다.
 */
@Composable
private fun ConversionProgressBar(conversion: SheetConversion) {
    val colors = EolssuTheme.colors
    val clock = ConversionReadingClock.Shared
    val reading = conversionStep(conversion) is ConversionStep.Reading
    var elapsed by remember(conversion.sheetId) { mutableLongStateOf(clock.elapsedMs(conversion)) }
    LaunchedEffect(conversion) {
        elapsed = clock.elapsedMs(conversion)
        while (reading) {
            delay(PROGRESS_TICK_MS)
            elapsed = clock.elapsedMs(conversion)
        }
    }
    val target = (conversionProgress(conversion, elapsed) ?: 0f).coerceIn(0f, 1f)
    var peak by remember(conversion.sheetId) { mutableFloatStateOf(0f) }
    if (target > peak) peak = target
    val shown by animateFloatAsState(peak, animationSpec = tween(PROGRESS_TICK_MS.toInt(), easing = LinearEasing))
    val sweep by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)),
    )
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(colors.lineSoft)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(peak, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(6.dp)
                .clip(shape)
                .background(colors.primary)
                .drawBehind {
                    // 빛 띠(막대 너비의 40%)가 채운 부분을 왼쪽 밖에서 오른쪽 밖까지 지나간다.
                    val band = size.width * 0.4f
                    val x = -band + (size.width + band) * sweep
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), startX = x, endX = x + band))
                },
        )
    }
}

private const val PROGRESS_TICK_MS = 1_000L
private const val SWEEP_MS = 1_400

/** '변환 대기 중' · '악보 3쪽 읽는 중'(서버가 읽은 쪽 수를 주면 '악보 읽는 중 1/3쪽') · '악보 만드는 중'. 쪽 수를 모르면 쪽 수 없이 쓴다. */
@Composable
private fun conversionProgressText(conversion: SheetConversion): String = when (val step = conversionStep(conversion)) {
    ConversionStep.Building -> stringResource(Res.string.mysongs_conversion_building)
    is ConversionStep.Reading -> when {
        step.pagesRead != null -> stringResource(Res.string.mysongs_conversion_reading_pages, step.pagesRead, step.pageCount)
        step.pageCount > 0 -> stringResource(Res.string.mysongs_conversion_reading_total, step.pageCount)
        else -> stringResource(Res.string.mysongs_conversion_reading)
    }
    ConversionStep.Queued, null -> stringResource(Res.string.mysongs_conversion_queued)
}

/** 휴지통: 편집 칩과 같은 높이(34dp)의 회색 네모 버튼(모서리 10), 아이콘 18dp. 누르는 영역은 48dp. */
@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.groupedBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(EolssuIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textSub)
    }
}

/** 편집 칩: 34dp · 모서리 10 · 연한 연두 채움에 짙은 연두 글자(13sp 600)로 눈에 띄게 한다(S15P31E102-768). 누르는 영역은 48dp. */
@Composable
private fun EditChip(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .height(34.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.accentTint)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.accentText, maxLines = 1)
    }
}

/**
 * '제공 곡에서 고침 · 세마치장단 · 10월 19일 수정' · '영산회상 · 10월 17일 수정'. 장단이 없으면 곡 묶음을 쓴다.
 * 가져온 정간보는 모든 줄이 같은 '정간보 가져옴'이라 출처를 적지 않는다(S15P31E102-768).
 */
@Composable
private fun sheetMeta(sheet: MySheet): String {
    val origin = when (sheet.origin) {
        MySheetOrigin.CopiedSong -> stringResource(Res.string.mysongs_origin_copied)
        MySheetOrigin.Imported -> null
    }
    val group = sheet.jangdanName?.let { stringResource(Res.string.jangdan_label, it) } ?: sheet.suiteName
    return listOfNotNull(origin, group, stringResource(Res.string.mysongs_meta_updated, monthDayLabel(sheet.updatedAt))).joinToString(" · ")
}
