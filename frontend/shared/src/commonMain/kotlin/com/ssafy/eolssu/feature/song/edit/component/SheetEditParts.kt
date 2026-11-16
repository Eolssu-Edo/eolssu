package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ssafy.eolssu.core.ui.component.CenteredTitleRow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons

/**
 * 칸 나눈 탭(`role=tablist`). 테두리 없는 흰 틀 안에서 고른 칸만 연두 바탕이다.
 * [large]는 목록 탭(틀 14 · 칸 40dp · 15sp), 아니면 편집기 도구 탭(틀 10 · 칸 34dp · 13sp)이다.
 */
@Composable
internal fun SegmentTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    counts: List<Int>? = null,
    fill: Boolean = large,
    frameColor: Color = EolssuTheme.colors.surfaceStrong,
) {
    val colors = EolssuTheme.colors
    val frame = RoundedCornerShape(14.dp)
    Row(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .background(frameColor, frame)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (large) 0.dp else 4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            val pill = RoundedCornerShape(10.dp)
            // Surface(onClick)는 48dp 최소 터치 칸을 강제해 틀 안의 알약이 작아 보인다. 알약 크기 그대로 누르게 한다(S15P31E102-768).
            Row(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .height(if (large) 48.dp else 40.dp)
                    .clip(pill)
                    .background(if (on) colors.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (large) 15.sp else 14.sp)
                Text(label, style = style.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1,
                    color = if (on) colors.text else colors.textSub)
                counts?.getOrNull(index)?.let { Text("$it", style = style.copy(fontWeight = FontWeight.Medium), color = colors.textMuted) }
            }
        }
    }
}

/**
 * EDIT-01 가져오기 종류 카드(모서리 16 · 테두리 2). 두 장을 같은 줄에 같은 크기로 놓고,
 * 왼쪽 위에 아이콘 원, 아래 왼쪽에 이름과 한 줄 설명([caption])을 둔다.
 * 고르면 옅은 연두 바탕·연두 테두리, 아니면 흰 바탕이라 고름 표시 없이 카드 색으로 고른 상태를 보인다.
 */
@Composable
internal fun KindCard(title: String, caption: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.RadioButton },
        // 회색 바탕 위 테두리 없는 흰 카드(모서리 20). 고르면 옅은 연두 바탕에 연두 테두리(S15P31E102-849, 연두 채움은 주 행동 버튼에만).
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.accentTint else colors.surface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        contentColor = colors.text,
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(42.dp).background(if (selected) colors.surface else colors.accentTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.text)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Text(
                caption,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
                maxLines = 2,
            )
        }
    }
}

/**
 * EDIT-01 '내 악보·제공 곡 불러오기' 줄: 왼쪽 둥근 네모 아이콘, 이름, 오른쪽 화살표. 줄 전체를 누르면 다른 화면으로 넘어간다.
 */
@Composable
internal fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(20.dp), color = colors.surface, contentColor = colors.text) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(colors.accentTint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.text)
            }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.textDisabled)
        }
    }
}

/** 고름 표시(18dp 원): 고르면 먹색 굵은 테두리, 아니면 옅은 회색 테두리. */
@Composable
internal fun RadioDot(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 18.dp, background: Color = Color.Transparent) {
    val colors = EolssuTheme.colors
    Box(
        modifier
            .size(size)
            .background(background, CircleShape)
            .border(if (selected) size * 0.3f else 1.5.dp, if (selected) colors.text else colors.textDisabled, CircleShape),
    )
}

/**
 * 알약 칩(34dp · 13sp 600): 테두리 없이 고르면 연두, 아니면 [containerColor](기본 회색 채움, 흰 카드 안용)(S15P31E102-768).
 * 회색 바탕 위에 둘 때는 [containerColor]로 흰색을 준다.
 */
@Composable
internal fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color? = null,
) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 34.dp).semantics { this.selected = selected },
        shape = CircleShape,
        color = if (selected) colors.primary else containerColor ?: colors.groupedBackground,
        contentColor = if (selected) colors.onPrimary else if (enabled) colors.text else colors.textDisabled,
    ) {
        // [compact]: 한 줄에 여러 칩을 나눠 넣을 때 좌우 여백·글자를 줄이고 줄바꿈 없이 가운데에 둔다.
        Box(Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = !compact,
            )
        }
    }
}

/** 작은 회색 안내 문구(11~12sp). */
@Composable
internal fun HintText(text: String, modifier: Modifier = Modifier, size: Int = 11) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = size.sp, lineHeight = (size * 1.45f).sp),
        color = EolssuTheme.colors.textMuted,
    )
}

/** 옅은 바탕 안내 상자(`background` · 모서리 12). */
@Composable
internal fun NoticeBox(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .background(EolssuTheme.colors.background, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 19.sp),
        color = EolssuTheme.colors.textSub,
    )
}

/**
 * 편집기 상단 바(EDIT-02·03): 왼쪽 닫기 원형 버튼 · 가운데 제목과 보조 문구([CenteredTitleRow]) · 오른쪽 연두 알약 버튼.
 */
@Composable
internal fun EditorTopBar(
    title: String,
    subtitle: String,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    right: @Composable RowScope.() -> Unit = {},
) {
    // 넓은 창에서도 본문([com.ssafy.eolssu.core.ui.component.EolssuScaffold])과 같은 너비([maxWidth])로 가운데에 둔다.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        CenteredTitleRow(
            modifier.widthIn(max = maxWidth).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            start = { EolssuIconButton(EolssuIcons.Close, closeLabel, onClose, bordered = false) },
            end = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { right() } },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 연두 알약 버튼(높이 40 · 14sp 600). 상단 바 오른쪽 '미리보기'·'선택'에 쓴다. 48dp 터치 영역을 유지한다.
 * [compact]면 입력판 안에 두는 작은 버튼(높이 32 · 13sp)이다.
 */
@Composable
internal fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    square: Boolean = true,
    secondary: Boolean = false,
    compact: Boolean = false,
) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .height(if (compact) 32.dp else 40.dp)
                    .widthIn(min = if (compact) 48.dp else 64.dp)
                    // 다른 작은 버튼과 같은 모서리 12dp. [secondary]는 회색 채움(편집처럼 보조 동작)(S15P31E102-768).
                    .background(if (!enabled) colors.surfaceStrong else if (secondary) colors.groupedBackground else colors.primary, if (square) RoundedCornerShape(12.dp) else CircleShape)
                    .padding(horizontal = if (compact) 12.dp else 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold),
                    color = if (!enabled) colors.textDisabled else if (secondary) colors.text else colors.onPrimary,
                )
            }
        }
    }
}

/** 네모 아이콘 버튼(40dp · 모서리 10, 48dp 터치 영역). 실행 취소·다시 실행, 음 높이 올리기·내리기에 쓴다. */
@Composable
internal fun SquareIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp), color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(size)
                    .alpha(if (enabled) 1f else 0.4f)
                    .background(colors.surface, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = colors.text)
            }
        }
    }
}
