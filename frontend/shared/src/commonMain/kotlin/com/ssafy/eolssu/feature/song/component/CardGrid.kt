package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 태블릿 카드 격자(`grid-template-columns: repeat(n, 1fr)`). 한 줄의 카드는 가장 높은 카드에 높이를 맞춘다.
 * 곡·내 악보처럼 개수가 적은 목록에 쓰고, 바깥에서 세로로 스크롤한다. [card]는 받은 Modifier를 카드에 붙인다.
 */
@Composable
internal fun <T> CardGrid(
    items: List<T>,
    columns: Int,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    card: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { item ->
                    key(itemKey(item)) { card(item, Modifier.weight(1f).fillMaxHeight()) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
