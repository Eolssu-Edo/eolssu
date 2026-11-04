package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.song.edit.HwangPitch
import com.ssafy.eolssu.feature.song.edit.StaffDuration
import com.ssafy.eolssu.feature.song.edit.StaffNote
import com.ssafy.eolssu.feature.song.edit.StaffNoteKey
import com.ssafy.eolssu.feature.song.edit.staffDuration
import com.ssafy.eolssu.feature.song.edit.staffPitch

/** 오선보 한 줄 높이와 오선 위치(와이어프레임 EDIT-03: 높이 104 · 줄 간격 12 · 맨 위 줄 30 · 맨 아래 줄 E4 78). */
private val SystemHeight = 104.dp
private const val TOP_LINE = 30f
private const val LINE_GAP = 12f
private const val BOTTOM_LINE = TOP_LINE + LINE_GAP * 4
private const val NOTE_LEFT = 34f
private const val RIGHT_PADDING = 14f
private const val BAR_GAP = 6f

/**
 * 오선보 한 줄(각 두 개). 정간보 순서를 왼쪽 → 오른쪽으로 적고 각 사이에 세로줄을 긋는다. 박자표는 한 각 정간 수 / 8(한 정간 = 8분음표).
 * 음표는 기본 먹색, 고른 음표만 이끼색(`#7E8C1C`)으로 칠하고 따로 띠를 두르지 않는다. 플랫이 붙는 음은 ♭를 앞에 적는다.
 *
 * @param firstCell 이 줄의 첫 정간 번호.
 * @param noteLabel 음표의 읽기 이름(접근성). 두 번째 값은 곡 처음부터의 음표 순서(1부터).
 */
@Composable
internal fun StaffSystemView(
    label: String,
    firstCell: Int,
    notes: List<StaffNote>,
    firstOrder: Int,
    gak: Int,
    hwang: HwangPitch,
    selected: StaffNoteKey?,
    noteLabel: @Composable (StaffNote, Int) -> String,
    onPick: (StaffNoteKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val measurer = rememberTextMeasurer()
    val serif = EolssuTheme.serif.yul
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(SystemHeight)
            .border(1.dp, colors.lineSoft, RoundedCornerShape(12.dp)),
    ) {
        val width = maxWidth.value
        val step = (width - NOTE_LEFT - RIGHT_PADDING - BAR_GAP) / (gak * 2)
        fun noteX(note: StaffNote): Float {
            val local = note.key.start - firstCell
            return NOTE_LEFT + local * step + (if (local >= gak) BAR_GAP else 0f) + note.key.sub * (step / note.subCount)
        }
        Canvas(Modifier.fillMaxSize()) {
            val unit = 1.dp.toPx()
            val lineColor = colors.primary
            for (k in 0..4) {
                val y = (TOP_LINE + LINE_GAP * k) * unit
                drawLine(lineColor, Offset(10 * unit, y), Offset(size.width - 10 * unit, y), strokeWidth = unit)
            }
            val barX = (NOTE_LEFT + gak * step + BAR_GAP / 2 - 2) * unit
            drawLine(lineColor, Offset(barX, TOP_LINE * unit), Offset(barX, BOTTOM_LINE * unit), strokeWidth = unit)
            drawLine(lineColor, Offset(size.width - 10 * unit, TOP_LINE * unit), Offset(size.width - 10 * unit, BOTTOM_LINE * unit), strokeWidth = unit)
            val meterStyle = serif.copy(fontSize = 17.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = colors.text)
            drawText(measurer, "$gak", topLeft = Offset(14 * unit, (TOP_LINE - 2) * unit), style = meterStyle)
            drawText(measurer, "8", topLeft = Offset(14 * unit, (TOP_LINE + LINE_GAP * 2 - 1) * unit), style = meterStyle)
            notes.forEach { note ->
                val ink = if (note.key == selected) colors.radical else colors.text
                drawStaffNote(note, noteX(note) * unit, hwang, ink, unit, measurer, serif.copy(fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = colors.textMuted,
        )
        // 누를 자리: 음표마다 한 정간 너비(최소 18dp)의 투명 버튼. 화면 읽기 프로그램은 율명과 음 이름을 읽는다.
        notes.forEachIndexed { i, note ->
            val description = noteLabel(note, firstOrder + i)
            val on = note.key == selected
            Box(
                Modifier
                    .offset(x = noteX(note).dp - 2.dp, y = 14.dp)
                    .size(width = maxOf(step / note.subCount, 18f).dp, height = 76.dp)
                    .semantics {
                        contentDescription = description
                        this.selected = on
                    }
                    .clickable(role = Role.Button) { onPick(note.key) },
            )
        }
    }
}

/** 음표 하나: 머리(기울인 타원) · 기둥 · 꼬리 · 점 · 플랫 · 덧줄, 쉼표는 작은 막대. [x]는 음표 자리의 왼쪽(px)이다. */
private fun DrawScope.drawStaffNote(
    note: StaffNote,
    x: Float,
    hwang: HwangPitch,
    ink: Color,
    unit: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    flatStyle: TextStyle,
) {
    val yul = note.yul
    if (yul == null) {
        drawRect(ink, topLeft = Offset(x + 3 * unit, (TOP_LINE + LINE_GAP * 1.5f) * unit), size = Size(9 * unit, 5 * unit))
        return
    }
    val pitch = staffPitch(yul, hwang)
    val y = (BOTTOM_LINE - pitch.stepsAboveBottomLine * LINE_GAP / 2) * unit
    val steps = pitch.stepsAboveBottomLine
    // 덧줄: 오선 아래(C4 이하)·위(A5 이상)의 줄 자리마다 짧게 긋는다.
    val ledgerSteps = when {
        steps <= -2 -> (-2 downTo steps step 2).toList()
        steps >= 10 -> (10..steps step 2).toList()
        else -> emptyList()
    }
    ledgerSteps.forEach { s ->
        val ly = (BOTTOM_LINE - s * LINE_GAP / 2) * unit
        drawLine(ink, Offset(x - 1 * unit, ly), Offset(x + 16 * unit, ly), strokeWidth = unit)
    }
    val duration = staffDuration(note.length)
    val hollow = duration == StaffDuration.Half || duration == StaffDuration.DottedHalf
    val center = Offset(x + 7.5f * unit, y)
    rotate(-20f, pivot = center) {
        drawOval(
            ink,
            topLeft = Offset(center.x - 6.5f * unit, center.y - 5 * unit),
            size = Size(13 * unit, 10 * unit),
            style = if (hollow) Stroke(1.6f * unit) else androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
    val stemX = x + 13.75f * unit
    val stemTop = y - 30 * unit
    drawLine(ink, Offset(stemX, y), Offset(stemX, stemTop), strokeWidth = 1.5f * unit)
    val flags = when (duration) {
        StaffDuration.Sixteenth -> 2
        StaffDuration.Eighth, StaffDuration.DottedEighth -> 1
        else -> 0
    }
    repeat(flags) { k ->
        val top = stemTop + k * 5 * unit
        val flag = Path().apply {
            moveTo(stemX, top)
            quadraticTo(stemX + 7 * unit, top + 3 * unit, stemX + 6.5f * unit, top + 11 * unit)
        }
        drawPath(flag, ink, style = Stroke(1.6f * unit))
    }
    if (duration == StaffDuration.DottedEighth || duration == StaffDuration.DottedQuarter || duration == StaffDuration.DottedHalf) {
        drawCircle(ink, radius = 1.5f * unit, center = Offset(x + 18.5f * unit, y - 2 * unit))
    }
    if (pitch.flat) {
        drawText(measurer, "♭", topLeft = Offset(x - 7 * unit, y - 9 * unit), style = flatStyle.copy(color = ink))
    }
}
