package com.ssafy.eolssu.feature.guide.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.guide.FingeringActions
import com.ssafy.eolssu.feature.guide.FingeringUiState
import com.ssafy.eolssu.feature.guide.component.BlowTabs
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.core.ui.score.HoleLegend
import com.ssafy.eolssu.core.model.fingering.FingeringNote

/**
 * 휴대폰 배치: 취법 탭은 위에 고정하고(악기는 상단 바 오른쪽), 그 아래 오른쪽 정렬 범례와 운지 카드만 한 줄씩 쌓아
 * 스크롤한다(S15P31E102-1242). 악기나 취법을 바꾸면 목록을 맨 위로 되돌린다.
 */
@Composable
internal fun FingeringCompactLayout(
    state: FingeringUiState,
    actions: FingeringActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
) {
    val instrument = state.instrument
    val listState = rememberSaveable(instrument, state.shownBlow, saver = LazyListState.Saver) { LazyListState() }
    Column(Modifier.fillMaxSize()) {
        BlowTabs(state.blowTabs, actions.onSelectBlow, Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "legend") {
                Box(Modifier.fillMaxWidth()) {
                    HoleLegend(Modifier.align(Alignment.CenterEnd).padding(top = 2.dp, bottom = 6.dp))
                }
            }
            items(state.notes, key = { "${instrument.id}-${state.shownBlow}-${it.hanja}" }) { note ->
                FingeringCard(instrument, note, audioContent = { noteAudioContent(note) })
            }
            item(key = "source") {
            }
        }
    }
}
