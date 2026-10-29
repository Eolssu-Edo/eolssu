package com.ssafy.eolssu.feature.result

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
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.catalog_consumer_back
import eolssu.shared.generated.resources.catalog_consumer_failed
import eolssu.shared.generated.resources.catalog_consumer_loading
import eolssu.shared.generated.resources.catalog_consumer_missing
import eolssu.shared.generated.resources.catalog_consumer_result_missing
import eolssu.shared.generated.resources.catalog_consumer_retry
import org.jetbrains.compose.resources.stringResource

/** 곡 조회 결과이며, 업로드·분석 서버의 상태와 별개다. */
enum class SongLookup { Loading, Ready, Missing, Failed }

@Composable
internal fun SongLookupContent(
    lookup: SongLookup,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    resultMissing: Boolean = false,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!resultMissing && lookup == SongLookup.Loading) CircularProgressIndicator()
        Text(
            stringResource(when {
                resultMissing -> Res.string.catalog_consumer_result_missing
                lookup == SongLookup.Loading -> Res.string.catalog_consumer_loading
                lookup == SongLookup.Missing -> Res.string.catalog_consumer_missing
                else -> Res.string.catalog_consumer_failed
            }),
            textAlign = TextAlign.Center,
        )
        if (!resultMissing && lookup != SongLookup.Loading) {
            EolssuButton(stringResource(Res.string.catalog_consumer_retry), onRetry)
        }
        EolssuButton(stringResource(Res.string.catalog_consumer_back), onBack, style = EolssuButtonStyle.Secondary)
    }
}
