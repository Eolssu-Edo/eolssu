package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.theme.EolssuTheme

/**
 * 배지 색. 공통 컴포넌트 보드의 `.badge`(상태)와 `.lv`(난이도) 기준이다.
 * - [Soft] `.badge` 기본(연습 준비) · [Play] `.badge.play`(모범 연주·AI 평가) · [Recording] `.badge.rec`(연주 중, 빨간 점)
 * - [Outline] `.badge.lite`(예시 결과·예시 악보처럼 덜 중요한 표시)
 * - [Level1]·[Level2]·[Level3] `.lv.l1~l3`(입문 · 초급 · 중급). 난이도는 [EolssuLevelBadge]로 쓰고, 같은 단계 색을 다른 문구에 빌릴 때만 직접 고른다.
 */
enum class EolssuBadgeStyle { Soft, Play, Recording, Outline, Level1, Level2, Level3 }

/**
 * 알약 배지. 상태 배지는 최소 26dp · 12sp, 난이도 배지는 최소 24dp · 12sp이고 [small]이면 각각 22dp · 20dp, 11sp다(SONG-03 곡 카드).
 * 글꼴 확대에 따라 높이가 늘어난다.
 */
@Composable
fun EolssuBadge(
    text: String,
    modifier: Modifier = Modifier,
    style: EolssuBadgeStyle = EolssuBadgeStyle.Outline,
    icon: ImageVector? = null,
    small: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val (bg, fg, line) = when (style) {
        EolssuBadgeStyle.Soft -> Triple(colors.accentTint, colors.text, Color.Transparent)
        EolssuBadgeStyle.Play -> Triple(colors.accentTint, colors.playText, Color.Transparent)
        EolssuBadgeStyle.Recording -> Triple(colors.recSoft, colors.recText, Color.Transparent)
        EolssuBadgeStyle.Outline -> Triple(colors.surface, colors.textMuted, colors.lineSoft)
        EolssuBadgeStyle.Level1 -> Triple(colors.accentTint, colors.accentText, colors.accentTintLine)
        EolssuBadgeStyle.Level2 -> Triple(colors.accentTintStrong, colors.text, colors.accentTintLine)
        EolssuBadgeStyle.Level3 -> Triple(colors.text, colors.background, Color.Transparent)
    }
    val level = style == EolssuBadgeStyle.Level1 || style == EolssuBadgeStyle.Level2 || style == EolssuBadgeStyle.Level3
    val minHeight = when {
        level -> if (small) 20.dp else 24.dp
        else -> if (small) 22.dp else 26.dp
    }
    Row(
        modifier
            .heightIn(min = minHeight)
            .background(bg, CircleShape)
            .border(1.dp, line, CircleShape)
            .padding(horizontal = if (level || small) 9.dp else 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style == EolssuBadgeStyle.Recording) {
            Box(Modifier.padding(end = 6.dp).size(if (small) 6.dp else 8.dp).background(colors.rec, CircleShape))
        }
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(13.dp), tint = fg)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (small) 11.sp else 12.sp,
                fontWeight = if (style == EolssuBadgeStyle.Outline) FontWeight.Medium else FontWeight.SemiBold,
            ),
            color = fg,
        )
    }
}

/** 곡 난이도 배지(`.lv`): 입문은 옅은 연두, 초급은 진한 연두, 중급은 먹색 바탕이다. */
@Composable
fun EolssuLevelBadge(level: SongLevel, modifier: Modifier = Modifier, small: Boolean = false) {
    EolssuBadge(songLevelLabel(level), modifier, style = levelBadgeStyle(level), small = small)
}

/** 난이도마다 쓰는 배지 색. */
fun levelBadgeStyle(level: SongLevel): EolssuBadgeStyle = when (level) {
    SongLevel.Intro -> EolssuBadgeStyle.Level1
    SongLevel.Elementary -> EolssuBadgeStyle.Level2
    SongLevel.Intermediate -> EolssuBadgeStyle.Level3
}

/**
 * 고르기 칩(`.chip`): 흰 바탕 · 옅은 테두리, 고르면 연두 바탕, [enabled]가 false면 회색 바탕의 흐린 글자.
 * 보이는 높이는 36dp(14sp)이고 [small]이면 28dp(12sp, 연습 속도 칩)다. 누르는 영역은 Material 최소 크기(48dp)를 유지하고, 확대 글꼴을 수용한다.
 */
@Composable
fun EolssuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val on = selected && enabled
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (small) 28.dp else 36.dp).semantics {
            this.selected = selected
            role = Role.Tab
        },
        shape = CircleShape,
        color = when {
            !enabled -> colors.surfaceStrong
            on -> colors.primary
            else -> colors.surface
        },
        contentColor = when {
            !enabled -> colors.textDisabled
            on -> colors.onPrimary
            else -> colors.textSub
        },
        border = BorderStroke(1.dp, if (on) colors.primary else colors.lineSoft),
    ) {
        Box(Modifier.padding(horizontal = if (small) 10.dp else 14.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = if (small) 12.sp else 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        }
    }
}

/** 칩을 가로로 늘어놓고 넘치면 다음 줄로 보낸다(`.chips`). */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
