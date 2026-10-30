package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.guide.FingeringNote
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.HoleState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_fingering_figure
import eolssu.shared.generated.resources.guide_legend_closed
import eolssu.shared.generated.resources.guide_legend_half
import eolssu.shared.generated.resources.guide_legend_open
import eolssu.shared.generated.resources.guide_listen
import eolssu.shared.generated.resources.guide_note_needs_check
import eolssu.shared.generated.resources.guide_note_reading
import eolssu.shared.generated.resources.guide_note_semitone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 구멍 표시 색. 와이어프레임 그림의 막음 색(#121212)과 '?' 색이다. */
private val HoleInk = Color(0xFF121212)

/**
 * 음 하나의 운지 카드(`.fg-row`): 율명 · 이름과 계이름 · 읽는 법 · 음 듣기 · 악기 그림.
 *
 * @param inLearn 기초부터 배우기의 카드(`.lp-figs .fing-row`). 율명 칸이 조금 좁고 반음 표시를 하지 않는다.
 */
@Composable
internal fun FingeringCard(
    instrument: GuideInstrument,
    note: FingeringNote,
    playing: Boolean,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
    inLearn: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val details = buildList {
        add(stringResource(Res.string.guide_note_reading, note.reading))
        if (!inLearn && !note.basic) add(stringResource(Res.string.guide_note_semitone))
        if (note.needsCheck) add(stringResource(Res.string.guide_note_needs_check))
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background, shape)
            .border(1.dp, colors.line, shape)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (inLearn) 8.dp else 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(if (inLearn) 40.dp else 44.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                HanjaGlyph(note.hanja, if (inLearn) 28.sp else 30.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
                    SolfegeText(instrument.solfegeOf(note.hanja), Modifier.padding(start = 7.dp))
                }
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
            }
            ListenButton(playing, stringResource(Res.string.guide_listen, note.name), onListen)
        }
        FingeringFigure(
            instrument,
            note.holes,
            Modifier.padding(top = if (inLearn) 6.dp else 4.dp),
            description = stringResource(Res.string.guide_fingering_figure, stringResource(instrument.label)),
        )
    }
}

/** '음 듣기' 원형 버튼(`.listen-btn`). 누르면 잠시 노랗게 강조한다. */
@Composable
internal fun ListenButton(playing: Boolean, contentDescription: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (playing) colors.accent else colors.background,
        border = BorderStroke(1.dp, if (playing) colors.accent else colors.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(GuideIcons.Speaker, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.text)
        }
    }
}

/**
 * 악기 사진 위에 구멍 상태를 겹친 그림. 막음은 검은 원, 반 막음은 왼쪽 반원, 확인 필요는 '?'.
 * 좌표는 사진 원본(가로 2000px) 기준이라 그린 너비에 맞춰 줄인다.
 */
@Composable
internal fun FingeringFigure(
    instrument: GuideInstrument,
    holes: List<HoleState>,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val muted = EolssuTheme.colors.textMuted
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(instrument.imageWidth.toFloat() / instrument.imageHeight)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Image(painterResource(instrument.image), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / instrument.imageWidth
            val r = (instrument.holeRadius - 2) * k
            instrument.holes.forEachIndexed { i, point ->
                val center = Offset(point.x * k, point.y * k)
                when (holes.getOrElse(i) { HoleState.Open }) {
                    HoleState.Closed -> drawCircle(HoleInk, r, center)
                    HoleState.Half -> drawPath(
                        Path().apply { arcTo(Rect(center, r), 90f, 180f, forceMoveTo = true); close() },
                        HoleInk,
                    )
                    HoleState.Unknown -> {
                        val layout = measurer.measure(
                            "?",
                            TextStyle(fontSize = (36 * k).toSp(), fontWeight = FontWeight.Bold, color = muted),
                        )
                        drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
                    }
                    HoleState.Open -> Unit
                }
            }
        }
    }
}

/** 구멍 표시 범례(`.legend`): 막는 구멍 · 여는 구멍 · 반 막음. 너비를 넓혀 주면 가운데에 놓는다. */
@Composable
internal fun HoleLegend(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(stringResource(Res.string.guide_legend_closed)) { drawCircle(colors.text) }
        LegendItem(stringResource(Res.string.guide_legend_open)) {}
        LegendItem(stringResource(Res.string.guide_legend_half)) {
            drawArc(colors.text, 90f, 180f, useCenter = true)
        }
    }
}

@Composable
private fun LegendItem(label: String, fill: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(12.dp)) {
            fill()
            val stroke = 1.5.dp.toPx()
            drawCircle(
                colors.text,
                radius = (size.minDimension - stroke) / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
        }
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
    }
}
