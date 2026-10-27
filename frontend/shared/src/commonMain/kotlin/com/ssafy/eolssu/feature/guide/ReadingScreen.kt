package com.ssafy.eolssu.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.guide.component.ComposedGlyph
import com.ssafy.eolssu.feature.guide.component.GuideCard
import com.ssafy.eolssu.feature.guide.component.GuideLead
import com.ssafy.eolssu.feature.guide.component.GuideText
import com.ssafy.eolssu.feature.guide.component.LengthRow
import com.ssafy.eolssu.feature.guide.component.ReadOrderBoard
import com.ssafy.eolssu.feature.guide.component.SigimsaeBasicBlock
import com.ssafy.eolssu.feature.guide.component.SigimsaeMoreBlock
import com.ssafy.eolssu.feature.guide.component.dashedCircle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_reading_fact_cell
import eolssu.shared.generated.resources.guide_reading_fact_cell_desc
import eolssu.shared.generated.resources.guide_reading_fact_yul
import eolssu.shared.generated.resources.guide_reading_fact_yul_desc
import eolssu.shared.generated.resources.guide_reading_intro
import eolssu.shared.generated.resources.guide_reading_length
import eolssu.shared.generated.resources.guide_reading_length_lead
import eolssu.shared.generated.resources.guide_reading_order
import eolssu.shared.generated.resources.guide_reading_pitch
import eolssu.shared.generated.resources.guide_reading_pitch_base
import eolssu.shared.generated.resources.guide_reading_pitch_base_desc
import eolssu.shared.generated.resources.guide_reading_pitch_caption
import eolssu.shared.generated.resources.guide_reading_pitch_high
import eolssu.shared.generated.resources.guide_reading_pitch_high_desc
import eolssu.shared.generated.resources.guide_reading_pitch_low
import eolssu.shared.generated.resources.guide_reading_pitch_low_desc
import eolssu.shared.generated.resources.guide_reading_sigimsae
import eolssu.shared.generated.resources.guide_reading_sigimsae_lead
import eolssu.shared.generated.resources.guide_reading_symbol_hold
import eolssu.shared.generated.resources.guide_reading_symbol_rest
import eolssu.shared.generated.resources.guide_reading_symbols
import eolssu.shared.generated.resources.guide_reading_title
import eolssu.shared.generated.resources.guide_sigimsae_credit
import org.jetbrains.compose.resources.stringResource

/**
 * GUIDE-02 정간보 쉽게 읽기. 정간보란 · 1 읽는 순서 · 2 음의 길이 · 3 음의 높낮이 · 4 기호 · 5 시김새.
 * 상태는 '더 많은 시김새' 펼침 하나뿐이라 ViewModel 없이 화면이 들고 있다.
 */
@Composable
fun ReadingScreen(onBack: () -> Unit) {
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    EolssuScaffold(topBar = { EolssuTopBar(stringResource(Res.string.guide_reading_title), onBack = onBack) }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuideCard(intro = true) {
                GuideLead(stringResource(Res.string.guide_reading_intro))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fact(stringResource(Res.string.guide_reading_fact_cell), stringResource(Res.string.guide_reading_fact_cell_desc), Modifier.weight(1f))
                    Fact(stringResource(Res.string.guide_reading_fact_yul), stringResource(Res.string.guide_reading_fact_yul_desc), Modifier.weight(1f))
                }
            }
            GuideCard(number = 1, title = stringResource(Res.string.guide_reading_order)) { ReadOrderBoard() }
            GuideCard(number = 2, title = stringResource(Res.string.guide_reading_length)) {
                GuideLead(stringResource(Res.string.guide_reading_length_lead))
                LengthRow()
            }
            GuideCard(number = 3, title = stringResource(Res.string.guide_reading_pitch)) { PitchRow() }
            GuideCard(number = 4, title = stringResource(Res.string.guide_reading_symbols)) { SymbolRow() }
            GuideCard(number = 5, title = stringResource(Res.string.guide_reading_sigimsae)) {
                GuideLead(stringResource(Res.string.guide_reading_sigimsae_lead))
                SigimsaeBasicBlock()
                SigimsaeMoreBlock(moreExpanded, onToggle = { moreExpanded = !moreExpanded })
                Text(
                    stringResource(Res.string.guide_sigimsae_credit),
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = EolssuTheme.colors.textMuted,
                )
            }
        }
    }
}

/** 정간보란 카드의 두 칸(`.two-facts`). */
@Composable
private fun Fact(title: String, description: String, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier.background(colors.background, RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        GuideText(
            description,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
            boldColor = colors.text,
        )
    }
}

/** 낮은 음 · 기준 음 · 높은 음. 높낮이는 색 대신 테두리 모양(점선 · 연한 실선 · 진한 실선)으로 구분한다. */
private enum class RingKind { Low, Base, High }

@Composable
private fun PitchRow() {
    val colors = EolssuTheme.colors
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            Octave(RingKind.Low, stringResource(Res.string.guide_reading_pitch_low), stringResource(Res.string.guide_reading_pitch_low_desc))
            OctaveArrow("←")
            Octave(RingKind.Base, stringResource(Res.string.guide_reading_pitch_base), stringResource(Res.string.guide_reading_pitch_base_desc))
            OctaveArrow("→")
            Octave(RingKind.High, stringResource(Res.string.guide_reading_pitch_high), stringResource(Res.string.guide_reading_pitch_high_desc))
        }
        GuideText(
            stringResource(Res.string.guide_reading_pitch_caption),
            Modifier.fillMaxWidth().padding(top = 10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textMuted,
            boldColor = colors.text,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Octave(kind: RingKind, name: String, description: String) {
    val colors = EolssuTheme.colors
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val ring = Modifier.size(62.dp).background(colors.background, CircleShape)
        Box(
            when (kind) {
                RingKind.Low -> ring.dashedCircle(colors.text, 2.dp)
                RingKind.Base -> ring.border(2.dp, colors.line, CircleShape)
                RingKind.High -> ring.border(2.dp, colors.text, CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                RingKind.Low -> ComposedGlyph("亻", "黃", 22.sp, 24.sp, overlap = 0.25f, radicalWeight = FontWeight.Bold)
                RingKind.Base -> Text("黃", style = EolssuTheme.serif.yul.copy(fontSize = 24.sp, lineHeight = 24.sp))
                RingKind.High -> ComposedGlyph("氵", "黃", 22.sp, 24.sp, overlap = 0.25f, radicalWeight = FontWeight.Bold)
            }
        }
        Text(name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Text(description, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
    }
}

@Composable
private fun OctaveArrow(arrow: String) {
    Text(
        arrow,
        Modifier.padding(top = 20.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        color = EolssuTheme.colors.textMuted,
    )
}

@Composable
private fun SymbolRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SymbolCell("ㅡ", stringResource(Res.string.guide_reading_symbol_hold), Modifier.weight(1f))
        SymbolCell("△", stringResource(Res.string.guide_reading_symbol_rest), Modifier.weight(1f))
    }
}

@Composable
private fun SymbolCell(symbol: String, description: String, modifier: Modifier) {
    Row(
        modifier.background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            Text(symbol, style = EolssuTheme.serif.yul.copy(fontSize = 26.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold))
        }
        Text(description, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
    }
}
