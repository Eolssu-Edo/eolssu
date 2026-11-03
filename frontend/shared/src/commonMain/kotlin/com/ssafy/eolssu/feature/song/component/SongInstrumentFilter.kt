package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.ChipRow
import com.ssafy.eolssu.core.ui.component.EolssuChip
import com.ssafy.eolssu.data.song.CatalogInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_instrument_all
import eolssu.shared.generated.resources.song_instrument_filter
import org.jetbrains.compose.resources.stringResource

/** 서버의 활성 악기 이름과 ID를 사용해 정악대금·산조대금도 개별 선택한다. */
@Composable
internal fun SongInstrumentFilter(instruments: List<CatalogInstrument>, selectedId: String?, onSelect: (String?) -> Unit) {
    if (instruments.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.song_instrument_filter), style = MaterialTheme.typography.labelLarge,
            color = EolssuTheme.colors.textSub)
        ChipRow {
            EolssuChip(stringResource(Res.string.song_instrument_all), selectedId == null, { onSelect(null) })
            instruments.forEach { instrument ->
                EolssuChip(instrument.name, selectedId == instrument.instrumentId, { onSelect(instrument.instrumentId) })
            }
        }
    }
}
