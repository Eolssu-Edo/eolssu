package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.song.SongLoadState
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 목록의 빈 응답과 통신 실패, 상세의 404를 구분한다. 검색 결과 없음은 기존 필터 안내를 쓴다. */
@Composable
internal fun SongLoadNotice(state: SongLoadState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state == SongLoadState.Loading) CircularProgressIndicator()
        Text(stringResource(when (state) {
            SongLoadState.Loading -> Res.string.song_catalog_loading
            SongLoadState.Ready -> Res.string.song_catalog_empty
            SongLoadState.Failed -> Res.string.song_catalog_failed
            SongLoadState.NotFound -> Res.string.song_catalog_not_found
        }), textAlign = TextAlign.Center)
        if (state != SongLoadState.Loading) EolssuButton(stringResource(Res.string.song_catalog_retry), onRetry)
    }
}
