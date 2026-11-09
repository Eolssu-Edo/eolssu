package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * 칸 나눈 탭(`role=tablist`). 흰 틀 안에서 고른 칸만 연두 바탕이다.
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
    frameColor: Color = EolssuTheme.colors.surface,
) {
    val colors = EolssuTheme.colors
    val frame = RoundedCornerShape(if (large) 14.dp else 10.dp)
    Row(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .background(frameColor, frame)
            .then(if (frameColor == colors.surface) Modifier.border(1.dp, colors.lineSoft, frame) else Modifier)
            .padding(if (large) 4.dp else 3.dp),
        horizontalArrangement = Arrangement.spacedBy(if (large) 0.dp else 3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Surface(
                onClick = { onSelect(index) },
                modifier = (if (fill) Modifier.weight(1f) else Modifier)
                    .heightIn(min = if (large) 40.dp else 34.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = on
                    },
                shape = RoundedCornerShape(if (large) 11.dp else 8.dp),
                color = if (on) colors.primary else Color.Transparent,
                contentColor = if (on) colors.onPrimary else if (large) colors.textSub else colors.textMuted,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (large) 15.sp else 13.sp)
                    Text(label, style = style.copy(fontWeight = if (on || !large) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1)
                    counts?.getOrNull(index)?.let { Text("$it", style = style.copy(fontWeight = FontWeight.Medium), color = colors.textMuted) }
                }
            }
        }
    }
}

/**
 * EDIT-01 가져오기 종류 카드(모서리 14 · 테두리 2). 두 장을 같은 줄에 같은 크기의 세로형으로 놓고,
 * 홈 기능 카드처럼 위에 그림([art]), 아래 가운데에 이름을 둔다.
 * 고르면 연두 바탕·연두 테두리, 아니면 흰 바탕·옅은 테두리라 고름 표시 없이 카드 색으로 고른 상태를 보인다.
 */
@Composable
internal fun KindCard(title: String, art: DrawableResource, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primary else colors.surface,
        contentColor = colors.text,
        border = BorderStroke(2.dp, if (selected) colors.primary else colors.lineSoft),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Image(painterResource(art), contentDescription = null, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 110.dp))
            Text(
                title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** EDIT-01 '내 악보·제공 곡 불러오기'처럼 다른 화면으로 넘어가는 카드. 제목과 오른쪽 화살표만 두고 카드 전체를 누른다. */
@Composable
internal fun LinkCard(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        contentColor = colors.text,
        border = BorderStroke(2.dp, colors.lineSoft),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
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

/** 알약 칩(34dp · 13sp 600): 고르면 연두, 아니면 흰 바탕에 옅은 테두리. */
@Composable
internal fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 34.dp).semantics { this.selected = selected },
        shape = CircleShape,
        color = if (selected) colors.primary else colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.lineSoft),
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
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
 * 편집기 상단 바(EDIT-02·03): 왼쪽 닫기 원형 버튼 · 왼쪽 정렬 제목과 보조 문구 · 오른쪽 연두 알약 버튼.
 */
@Composable
internal fun EditorTopBar(
    title: String,
    subtitle: String,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    right: @Composable RowScope.() -> Unit = {},
) {
    // 넓은 창에서도 본문([com.ssafy.eolssu.core.ui.component.EolssuScaffold])과 같은 휴대폰 너비로 가운데에 둔다.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier.widthIn(max = CompactContentMaxWidth).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EolssuIconButton(EolssuIcons.Close, closeLabel, onClose)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            right()
        }
    }
}

/** 연두 알약 버튼(높이 40 · 14sp 600). 상단 바 오른쪽 '미리보기'·'선택'에 쓴다. 48dp 터치 영역을 유지한다. */
@Composable
internal fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    square: Boolean = false,
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
                    .height(40.dp)
                    .widthIn(min = 64.dp)
                    .background(if (enabled) colors.primary else colors.surfaceStrong, if (square) RoundedCornerShape(12.dp) else CircleShape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = if (enabled) colors.onPrimary else colors.textDisabled,
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
                    .background(colors.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.lineSoft, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = colors.text)
            }
        }
    }
}
