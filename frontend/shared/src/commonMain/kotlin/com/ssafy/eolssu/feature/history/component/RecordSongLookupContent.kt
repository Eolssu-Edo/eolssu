package com.ssafy.eolssu.feature.history.component

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.PlaybackLoadingOverlay
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import com.ssafy.eolssu.feature.history.RecordSongLookup
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.catalog_consumer_back
import eolssu.shared.generated.resources.catalog_consumer_failed
import eolssu.shared.generated.resources.catalog_consumer_missing
import eolssu.shared.generated.resources.catalog_consumer_record_missing
import eolssu.shared.generated.resources.catalog_consumer_record_failed
import eolssu.shared.generated.resources.catalog_consumer_retry
import org.jetbrains.compose.resources.stringResource

/**
 * 기록·곡 정보를 불러오는 중에는 모범 연주 로딩과 같은 ‘로딩 중’ 막([PlaybackLoadingOverlay])을 보이고(S15P31E102-810),
 * 실패·없음이면 안내 문장과 다시 시도·돌아가기 버튼을 보인다.
 */
@Composable
internal fun RecordSongLookupContent(state: RecordDetailUiState, actions: RecordDetailActions) {
    if (state.lookupLoading) {
        PlaybackLoadingOverlay()
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(when {
                state.record == null && state.songLookup == RecordSongLookup.Failed -> Res.string.catalog_consumer_record_failed
                state.record == null -> Res.string.catalog_consumer_record_missing
                state.songLookup == RecordSongLookup.Missing -> Res.string.catalog_consumer_missing
                else -> Res.string.catalog_consumer_failed
            }),
            textAlign = TextAlign.Center,
        )
        if (state.record != null || state.songLookup == RecordSongLookup.Failed) EolssuButton(stringResource(Res.string.catalog_consumer_retry), actions.onRetrySong, qaTarget = QaTarget.RECORD_SONG_LOOKUP_CONTENT_BUTTON_01)
        EolssuButton(stringResource(Res.string.catalog_consumer_back), actions.onBack, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.RECORD_SONG_LOOKUP_CONTENT_BUTTON_02)
    }
}
