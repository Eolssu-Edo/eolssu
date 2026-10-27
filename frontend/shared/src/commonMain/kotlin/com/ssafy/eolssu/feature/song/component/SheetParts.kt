package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 점선 테두리(`border: 1.5px dashed`). 악보 추가 버튼·PDF 고르기 칸에 쓴다. */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** 24dp 상태 배지(`height: 24px; padding: 0 10px`). 내 악보 카드·PDF 미리보기에서 쓴다. */
@Composable
fun SheetBadge(text: String, background: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.height(24.dp).background(background, CircleShape).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = contentColor)
    }
}

/** PDF 올리기 두 단계 진행 막대. [step]까지 검게 채운다. */
@Composable
fun UploadStepBar(step: Int, modifier: Modifier = Modifier, steps: Int = 2) {
    val colors = EolssuTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(steps) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(if (index < step) colors.primary else colors.surfaceStrong, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** 회색 바탕 한 줄 입력칸(높이 48dp). */
@Composable
fun FilledTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    val colors = EolssuTheme.colors
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = colors.text)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = colors.textMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 44dp 회색 칸 안의 종류 글자·아이콘 자리(`PDF`, `MIDI`). */
@Composable
fun KindTile(modifier: Modifier = Modifier, background: Color = EolssuTheme.colors.surface, content: @Composable () -> Unit) {
    Box(modifier.size(44.dp).background(background, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { content() }
}

/** [KindTile] 안의 11sp 굵은 글자. */
@Composable
fun KindLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = EolssuTheme.colors.text)
}
