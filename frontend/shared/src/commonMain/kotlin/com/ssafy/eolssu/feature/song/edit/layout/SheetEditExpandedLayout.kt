package com.ssafy.eolssu.feature.song.edit.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 태블릿 가로(Expanded) 악보 편집 배치: 왼쪽 칸에 빠르기 줄과 남은 높이를 모두 쓰는 악보, 오른쪽 칸에 입력판을 세운다.
 * 입력판이 창 높이보다 길면(시김새·이조 탭, 낮은 창) 오른쪽 칸만 세로로 민다(S15P31E102-965).
 */
@Composable
internal fun SheetEditExpandedLayout(
    tempoRow: @Composable (Modifier) -> Unit,
    scoreBoard: @Composable (Modifier) -> Unit,
    inputPanel: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            tempoRow(Modifier)
            scoreBoard(Modifier.weight(1f).fillMaxWidth())
        }
        Column(Modifier.width(InputPanelWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
            inputPanel()
        }
    }
}

/** 오른쪽 입력판 칸 너비. 안쪽 여백(14dp × 2)을 빼고 52dp 율명 자판 6개와 간격(342dp)이 한 줄에 들어간다. */
private val InputPanelWidth: Dp = 400.dp
