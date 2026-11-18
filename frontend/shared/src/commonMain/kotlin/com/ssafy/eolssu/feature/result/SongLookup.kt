package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.diagnostics.QaTarget

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
import eolssu.shared.generated.resources.catalog_result_loading
import eolssu.shared.generated.resources.catalog_result_failed
import eolssu.shared.generated.resources.catalog_result_retry
import eolssu.shared.generated.resources.catalog_consumer_retry
import org.jetbrains.compose.resources.stringResource

/** 곡 조회 결과이며, 업로드·분석 서버의 상태와 별개다. */
enum class SongLookup { Loading, Ready, Missing, Failed }

@Composable
internal fun SongLookupContent(
    lookup: SongLookup,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    resultLookup: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        header?.invoke()
        if (lookup == SongLookup.Loading) CircularProgressIndicator()
        Text(
            stringResource(when {
                resultLookup && lookup == SongLookup.Loading -> Res.string.catalog_result_loading
                resultLookup && lookup == SongLookup.Failed -> Res.string.catalog_result_failed
                resultLookup -> Res.string.catalog_consumer_result_missing
                lookup == SongLookup.Loading -> Res.string.catalog_consumer_loading
                lookup == SongLookup.Missing -> Res.string.catalog_consumer_missing
                else -> Res.string.catalog_consumer_failed
            }),
            textAlign = TextAlign.Center,
        )
        if (lookup != SongLookup.Loading) {
            EolssuButton(stringResource(if (resultLookup) Res.string.catalog_result_retry else Res.string.catalog_consumer_retry), onRetry, qaTarget = QaTarget.SONG_LOOKUP_BUTTON_01)
        }
        EolssuButton(stringResource(Res.string.catalog_consumer_back), onBack, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SONG_LOOKUP_BUTTON_02)
    }
}
