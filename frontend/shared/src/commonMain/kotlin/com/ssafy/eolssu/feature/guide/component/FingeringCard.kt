package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.NoteRemark
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_fingering_figure
import eolssu.shared.generated.resources.guide_listen
import eolssu.shared.generated.resources.guide_note_needs_check
import eolssu.shared.generated.resources.guide_note_reading
import eolssu.shared.generated.resources.guide_note_semitone
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.ui.score.FingeringFigure

/**
 * 음 하나의 운지 카드(`.fg-row`): 흰 바탕에 율명 · 이름과 계이름 · 읽는 법 · 음 듣기 · 악기 그림.
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
    val details = listOf(stringResource(Res.string.guide_note_reading, note.reading)) + note.remarks(inLearn).map { remark ->
        when (remark) {
            NoteRemark.Semitone -> stringResource(Res.string.guide_note_semitone)
            NoteRemark.NeedsCheck -> stringResource(Res.string.guide_note_needs_check)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.lineSoft, shape)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (inLearn) 8.dp else 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(if (inLearn) 40.dp else 44.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                HanjaGlyph(note.hanja, if (inLearn) 28.sp else 30.sp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
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
            description = stringResource(Res.string.guide_fingering_figure, note.name),
        )
    }
}

/** '음 듣기' 원형 버튼(`.listen-btn`, 40dp). 누를 수 있는 영역은 Surface가 48dp로 넓힌다. [playing]이면 연두로 강조한다. */
@Composable
internal fun ListenButton(playing: Boolean, contentDescription: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (playing) colors.accent else colors.surface,
        border = BorderStroke(1.dp, if (playing) colors.accent else colors.lineSoft),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(GuideIcons.Speaker, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.text)
        }
    }
}
