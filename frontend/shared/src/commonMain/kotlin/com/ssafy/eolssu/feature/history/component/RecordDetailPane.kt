package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.ui.component.PlaybackLoadingOverlay
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.history.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 태블릿 가로 오른쪽 칸. 단독 화면과 같은 결과·재생 상태를 사용한다. */
@Composable
internal fun RecordDetailPane(state: RecordDetailUiState, actions: RecordDetailActions, modifier: Modifier = Modifier) {
    // 회색 바탕 위에 휴대폰 상세와 같은 흰 카드들을 바로 놓는다(테두리 상자 없음, S15P31E102-768).
    Box(modifier.padding(vertical = 4.dp)) {
        // 목록에 없는 기록을 처음 불러오는 동안은 빈 칸 안내 대신 ‘로딩 중’ 막을 보인다(S15P31E102-810).
        if (state.record == null && state.lookupLoading) {
            PlaybackLoadingOverlay()
            return@Box
        }
        if (state.record == null || state.delete == RecordDeleteState.Deleted) {
            Text(stringResource(Res.string.records_pane_empty), Modifier.align(Alignment.Center).padding(24.dp),
                textAlign = TextAlign.Center, color = EolssuTheme.colors.textMuted)
            return@Box
        }
        RecordDeleteConfirmation(state, actions)
        RecordComparePick(state, actions)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recordTitleLine(state).orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RecordDeleteButton(state.delete == RecordDeleteState.Deleting, actions.onAskDelete)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                RecordResultContent(state, actions, wrapped = false, modifier = Modifier.fillMaxSize())
                if (state.playbackLoading) PlaybackLoadingOverlay()
            }
            PlaybackBarContent(state, actions)
        }
    }
}
