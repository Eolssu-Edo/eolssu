package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 배지 색(`.badge` · `.dark` · `.play` · `.rec` · 회색 바탕). */
enum class EolssuBadgeStyle { Outline, Dark, Play, Recording, Soft }

/** 22dp 알약 배지. [Play]·[Recording]은 앞에 점을 찍는다. */
@Composable
fun EolssuBadge(text: String, modifier: Modifier = Modifier, style: EolssuBadgeStyle = EolssuBadgeStyle.Outline, icon: ImageVector? = null) {
    val colors = EolssuTheme.colors
    val (bg, fg, line) = when (style) {
        EolssuBadgeStyle.Outline -> Triple(colors.background, colors.text, colors.line)
        EolssuBadgeStyle.Dark -> Triple(colors.primary, colors.onPrimary, colors.primary)
        EolssuBadgeStyle.Play -> Triple(colors.playSoft, colors.playText, Color.Transparent)
        EolssuBadgeStyle.Recording -> Triple(colors.recSoft, colors.recText, Color.Transparent)
        EolssuBadgeStyle.Soft -> Triple(colors.surface, colors.text, Color.Transparent)
    }
    Row(
        modifier
            .height(22.dp)
            .background(bg, CircleShape)
            .border(1.dp, line, CircleShape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style == EolssuBadgeStyle.Play || style == EolssuBadgeStyle.Recording) {
            Box(Modifier.padding(end = 6.dp).size(6.dp).background(fg, CircleShape))
        }
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(13.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = fg)
    }
}

/** 고르기 칩(`.chip`, 높이 34dp). 고르면 검은 바탕. */
@Composable
fun EolssuChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp).semantics {
            this.selected = selected
            role = Role.Tab
        },
        shape = CircleShape,
        color = if (selected) colors.primary else colors.background,
        contentColor = if (selected) colors.onPrimary else colors.text,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.line),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
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
