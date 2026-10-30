package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import com.ssafy.eolssu.feature.history.RecordSongLookup
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.catalog_consumer_back
import eolssu.shared.generated.resources.catalog_consumer_failed
import eolssu.shared.generated.resources.catalog_consumer_loading
import eolssu.shared.generated.resources.catalog_consumer_missing
import eolssu.shared.generated.resources.catalog_consumer_record_missing
import eolssu.shared.generated.resources.catalog_consumer_retry
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RecordSongLookupContent(state: RecordDetailUiState, actions: RecordDetailActions) {
    val loading = state.songLookup == RecordSongLookup.Loading
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        Text(
            stringResource(when {
                state.record == null -> Res.string.catalog_consumer_record_missing
                loading -> Res.string.catalog_consumer_loading
                state.songLookup == RecordSongLookup.Missing -> Res.string.catalog_consumer_missing
                else -> Res.string.catalog_consumer_failed
            }),
            textAlign = TextAlign.Center,
        )
        if (state.record != null && !loading) EolssuButton(stringResource(Res.string.catalog_consumer_retry), actions.onRetrySong)
        EolssuButton(stringResource(Res.string.catalog_consumer_back), actions.onBack, style = EolssuButtonStyle.Secondary)
    }
}
