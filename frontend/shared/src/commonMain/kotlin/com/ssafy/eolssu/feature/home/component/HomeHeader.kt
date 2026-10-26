package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.home.Instrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_change_instrument
import eolssu.shared.generated.resources.home_current_instrument
import org.jetbrains.compose.resources.stringResource

/** HOME-01 '현재 악기' 표시. 누르면 악기 선택 시트를 연다. */
@Composable
internal fun InstrumentChip(instrument: Instrument, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(instrument.label)
    val description = stringResource(Res.string.home_change_instrument, name)
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.home_current_instrument),
            style = MaterialTheme.typography.bodySmall,
            color = EolssuTheme.colors.textMuted,
        )
        OutlinedButton(
            onClick = onClick,
            shape = CircleShape,
            border = BorderStroke(1.dp, EolssuTheme.colors.line),
            modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = description },
        ) {
            Icon(HomeIcons.Flute, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
            Icon(HomeIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(12.dp), tint = EolssuTheme.colors.textMuted)
        }
    }
}
