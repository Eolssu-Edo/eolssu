package com.ssafy.eolssu.feature.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideText
import com.ssafy.eolssu.feature.guide.component.HoleLegend
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_blow_unavailable
import eolssu.shared.generated.resources.guide_fingering_title
import org.jetbrains.compose.resources.stringResource

/** GUIDE-01 운지법 보기. 악기·취법을 고르면 그 취법의 모든 음을 낮은 음부터 운지 그림과 함께 보여준다. */
@Composable
fun FingeringScreen(
    state: FingeringUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleDropdown: () -> Unit,
    onDismissDropdown: () -> Unit,
    onSelectInstrument: (GuideInstrument) -> Unit,
    onSelectBlow: (Blow) -> Unit,
    onListen: (FingeringNote) -> Unit,
) {
    val instrument = state.instrument
    EolssuScaffold(
        topBar = { EolssuTopBar(stringResource(Res.string.guide_fingering_title), onBack = onBack) },
        snackbarHostState = snackbarHostState,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Column {
                    InstrumentDropdown(
                        selected = instrument,
                        open = state.isDropdownOpen,
                        onToggle = onToggleDropdown,
                        onSelect = onSelectInstrument,
                        onDismiss = onDismissDropdown,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    BlowTabs(instrument, state.shownBlow, onSelectBlow, Modifier.padding(bottom = 10.dp))
                    HoleLegend(Modifier.padding(top = 2.dp, bottom = 6.dp))
                    GuideText(
                        instrument.note,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.5.sp),
                        color = EolssuTheme.colors.textMuted,
                    )
                }
            }
            items(state.notes, key = { "${instrument.id}-${it.hanja}" }) { note ->
                FingeringCard(instrument, note, playing = state.playing == note.hanja, onListen = { onListen(note) })
            }
            item(key = "source") {
                GuideText(
                    instrument.source,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.5.sp),
                    color = EolssuTheme.colors.textMuted,
                )
            }
        }
    }
}

/** 취법 탭(`.blow`). 이 악기에 없는 취법(단소·소금의 저취)은 누를 수 없다. */
@Composable
private fun BlowTabs(instrument: GuideInstrument, shown: Blow, onSelect: (Blow) -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val unavailable = stringResource(Res.string.guide_blow_unavailable)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Blow.entries.forEach { blow ->
            val enabled = blow in instrument.blows
            val on = blow == shown
            Surface(
                onClick = { onSelect(blow) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .semantics {
                        role = Role.Tab
                        selected = on
                        if (!enabled) stateDescription = unavailable
                    },
                shape = RoundedCornerShape(10.dp),
                color = if (on) colors.text else colors.background,
                contentColor = when {
                    on -> colors.onPrimary
                    enabled -> colors.text
                    else -> colors.lineStrong
                },
                border = BorderStroke(1.dp, if (on) colors.text else colors.line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(blow.label), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}
