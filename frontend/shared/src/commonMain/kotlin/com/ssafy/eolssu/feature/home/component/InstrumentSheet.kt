package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.home.Instrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.instrument_sheet_current
import eolssu.shared.generated.resources.instrument_sheet_subtitle
import eolssu.shared.generated.resources.instrument_sheet_title
import org.jetbrains.compose.resources.stringResource

/** HOME-01 '현재 악기'를 누르면 열리는 악기 선택 하단 시트. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstrumentSheet(
    instruments: List<Instrument>,
    selected: Instrument,
    onSelect: (Instrument) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 중간 높이 없이 열림·닫힘 두 단계만 쓴다.
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = EolssuTheme.colors.background,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
            Text(stringResource(Res.string.instrument_sheet_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.instrument_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = EolssuTheme.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )
            Column(Modifier.selectableGroup()) {
                instruments.forEach { instrument ->
                    InstrumentRow(instrument, isSelected = instrument == selected, onClick = { onSelect(instrument) })
                    HorizontalDivider(color = EolssuTheme.colors.line)
                }
            }
        }
    }
}

@Composable
private fun InstrumentRow(instrument: Instrument, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(EolssuTheme.colors.surface, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EolssuIcons.Flute, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(stringResource(instrument.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (isSelected) {
            Text(stringResource(Res.string.instrument_sheet_current), style = MaterialTheme.typography.labelMedium)
            Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.size(18.dp))
        }
    }
}
