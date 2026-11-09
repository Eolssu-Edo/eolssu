package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.history.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 태블릿 가로 오른쪽 칸. 단독 화면과 같은 결과·재생 상태를 사용한다. */
@Composable
internal fun RecordDetailPane(state: RecordDetailUiState, actions: RecordDetailActions, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, EolssuTheme.colors.line, RoundedCornerShape(20.dp)).padding(16.dp)) {
        if (state.record == null || state.delete == RecordDeleteState.Deleted) {
            Text(stringResource(Res.string.records_pane_empty), Modifier.align(Alignment.Center).padding(24.dp),
                textAlign = TextAlign.Center, color = EolssuTheme.colors.textMuted)
            return@Box
        }
        RecordDeleteConfirmation(state, actions)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.record_result_title), style = MaterialTheme.typography.titleMedium)
                    recordResultSubtitle(state)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EolssuTheme.colors.textMuted) }
                }
                RecordDeleteButton(state.delete == RecordDeleteState.Deleting, actions.onAskDelete)
            }
            RecordResultContent(state, actions, wrapped = false, modifier = Modifier.weight(1f).fillMaxWidth())
            if (state.song != null) PlaybackBarContent(state, actions, gap = 8.dp)
        }
    }
}
