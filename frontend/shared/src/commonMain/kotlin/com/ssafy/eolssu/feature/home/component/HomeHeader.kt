package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_current_instrument
import eolssu.shared.generated.resources.instrument_unknown
import eolssu.shared.generated.resources.home_my_page
import org.jetbrains.compose.resources.stringResource

/** HOME-01 '현재 악기' 표시. 바꾸기는 마이페이지(MY-01)로 옮겨 누를 수 없다. */
@Composable
internal fun InstrumentLabel(instrument: Instrument?, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.home_current_instrument),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.weight(1f, fill = false),
        )
        Surface(shape = CircleShape, color = colors.surface, contentColor = colors.text) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EolssuIcons.Flute, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(stringResource(instrument?.label ?: Res.string.instrument_unknown), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** 오른쪽 위 마이페이지 입구: 48dp 터치 영역. */
@Composable
internal fun MyPageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = colors.background,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Icon(EolssuIcons.User, contentDescription = stringResource(Res.string.home_my_page), modifier = Modifier.padding(12.dp))
    }
}
