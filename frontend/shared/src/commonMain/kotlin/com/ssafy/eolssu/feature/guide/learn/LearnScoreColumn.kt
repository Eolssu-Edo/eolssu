package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.FingeringFigure
import com.ssafy.eolssu.feature.guide.component.HanjaGlyph
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_practice_pitch_note_current
import eolssu.shared.generated.resources.learn_practice_pitch_note_hit
import eolssu.shared.generated.resources.learn_practice_pitch_note_waiting
import eolssu.shared.generated.resources.learn_sequence_note
import eolssu.shared.generated.resources.practice_finger_gak
import eolssu.shared.generated.resources.practice_finger_head_fingering
import eolssu.shared.generated.resources.practice_finger_head_yul
import org.jetbrains.compose.resources.stringResource

/** 정간 한 줄의 연습 상태. */
internal enum class LearnCellState { Waiting, Current, Hit }

/**
 * 5단계 음 순서를 연주 평가의 '운지법과 함께 보기' 표처럼 그린다(S15P31E102-749).
 * 머리 줄 '{악기} 운지 | 율명', 첫 줄 위 먹색 굵은 선과 ‘1각’, 줄 사이 점선, 왼쪽 가로 운지 그림, 오른쪽 율명 칸.
 * [states]가 있으면 연주 평가처럼 지금 낼 줄을 연두 테두리로 두르고, 그 음을 이어 낸 만큼([hold]) 위에서부터 회색으로 채우며
 * 듣는 중이면([recording]) 율명을 붉게 쓴다. 맞힌 줄은 끝까지 채운다. 없으면 차례만 보인다.
 * 연습 화면의 표는 곡 악보(`Song`)에 묶여 있고 다른 기능의 내부라 같은 모양을 여기서 따로 그린다.
 */
@Composable
internal fun LearnScoreColumn(
    instrument: GuideInstrument,
    notes: List<FingeringNote>,
    states: List<LearnCellState>? = null,
    hold: Float = 0f,
    recording: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(3.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape)) {
        ScoreHead(stringResource(instrument.label))
        notes.forEachIndexed { i, note ->
            ScoreRow(instrument, note, number = i + 1, state = states?.getOrNull(i), hold = hold, recording = recording, gakStart = i == 0)
        }
    }
}

/** 표 머리(29dp): ‘{악기} 운지 | 율명’, 10sp 회색, 칸 사이 선. */
@Composable
private fun ScoreHead(instrumentName: String) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
    Row(Modifier.fillMaxWidth().height(29.dp).clearAndSetSemantics {}) {
        Text(
            stringResource(Res.string.practice_finger_head_fingering, instrumentName),
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp, vertical = 5.dp),
            style = style,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier.width(NoteWidth).fillMaxHeight().drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) }.padding(vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(Res.string.practice_finger_head_yul), style = style, color = colors.textMuted, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ScoreRow(
    instrument: GuideInstrument,
    note: FingeringNote,
    number: Int,
    state: LearnCellState?,
    hold: Float,
    recording: Boolean,
    gakStart: Boolean,
) {
    val colors = EolssuTheme.colors
    val current = state == LearnCellState.Current
    val description = when (state) {
        null -> stringResource(Res.string.learn_sequence_note, number, note.name)
        LearnCellState.Hit -> stringResource(Res.string.learn_practice_pitch_note_hit, note.reading)
        LearnCellState.Current -> stringResource(Res.string.learn_practice_pitch_note_current, note.reading)
        LearnCellState.Waiting -> stringResource(Res.string.learn_practice_pitch_note_waiting, note.reading)
    }
    val filled by animateFloatAsState(
        when (state) {
            LearnCellState.Hit -> 1f
            LearnCellState.Current -> hold
            else -> 0f
        },
        label = "learn-row-fill",
    )
    val ring by animateColorAsState(if (current) colors.accent else colors.accent.copy(alpha = 0f), label = "learn-row-ring")
    val yulColor = if (current && recording) colors.rec else colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .drawBehind {
                if (filled > 0f) drawRect(colors.doneSoft, size = Size(size.width, size.height * filled))
                if (gakStart) {
                    drawRect(colors.text, size = Size(size.width, 2.dp.toPx()))
                } else {
                    drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
                }
            }
            .border(2.dp, ring)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 2.dp)) {
            if (gakStart) {
                Text(
                    stringResource(Res.string.practice_finger_gak, 1),
                    modifier = Modifier.align(Alignment.TopStart),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                    color = colors.textMuted,
                )
            }
            Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                // 연습 화면 운지 표와 같은 비율로 그린다(그림 위아래 여백을 잘라 낸 가로 악기).
                val ratio = instrument.imageWidth.toFloat() / (instrument.imageHeight - 300)
                FingeringFigure(instrument, note.holes, Modifier.widthIn(max = FigureHeight * ratio).height(FigureHeight), cropVertical = true)
            }
        }
        Box(
            Modifier.width(NoteWidth).fillMaxHeight().drawBehind { drawRect(colors.line, size = Size(1.dp.toPx(), size.height)) },
            contentAlignment = Alignment.Center,
        ) {
            HanjaGlyph(note.hanja, 25.sp, color = yulColor)
        }
    }
}

private val RowHeight = 60.dp
private val FigureHeight = 40.dp
private val NoteWidth = 76.dp
