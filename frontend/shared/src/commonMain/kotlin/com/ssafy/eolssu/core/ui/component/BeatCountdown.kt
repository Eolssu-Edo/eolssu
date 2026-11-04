package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/**
 * 멈췄다가 다시 시작할 때 박을 세어 주는 3·2·1 막(2차 결합본 `COUNT_OVERLAY`, 리듬 게임식). 숫자는 한 박마다 하나씩 줄어든다.
 * 바탕색 74% 막 위 가운데에 흰 원(150dp, 안쪽 6dp 연두 테두리) · 숫자(Song Myung 86sp) · 센 박 점(12dp) · 안내 문구(14sp).
 * 연습 화면의 평가 시작 카운트다운과 구간 반복이 함께 쓰는 모양이다. 막 아래는 누를 수 없다.
 *
 * @param count 지금 보일 숫자(3 → 1).
 * @param beats 셀 박 수. 점 개수다.
 * @param action 안내 문구 아래에 둘 버튼(평가 시작의 ‘시작 취소’). 막 아래는 누를 수 없으므로 빠져나갈 길은 여기에 둔다.
 */
@Composable
fun BeatCountdownOverlay(count: Int, message: String, modifier: Modifier = Modifier, beats: Int = 3, action: (@Composable () -> Unit)? = null) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.74f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        // 숫자·문구만 한 번에 읽히고, [action] 버튼은 따로 누를 수 있게 둔다.
        Column(
            Modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "$count · $message"
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BeatCountRing(count, beats = beats)
            Text(
                message,
                modifier = Modifier.padding(horizontal = 24.dp).clearAndSetSemantics {},
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

/**
 * 3·2·1 원: 150dp 흰 원(안쪽 6dp 연두 테두리)에 Song Myung 86sp 숫자, 아래에 센 박만큼 먹색으로 칠하는 12dp 점.
 * [BeatCountdownOverlay]와 연습 화면의 평가 시작 카운트다운·한 음씩 다시 시작이 함께 쓴다.
 */
@Composable
fun BeatCountRing(count: Int, modifier: Modifier = Modifier, beats: Int = 3) {
    val colors = EolssuTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(150.dp)
                .shadow(14.dp, CircleShape, ambientColor = colors.text.copy(alpha = 0.14f), spotColor = colors.text.copy(alpha = 0.14f))
                .background(colors.surface, CircleShape)
                .border(6.dp, colors.accent, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", style = EolssuTheme.serif.brand.copy(fontSize = 86.sp, lineHeight = 86.sp, letterSpacing = 0.sp), color = colors.text)
        }
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 센 박만큼 먹색 점: 3이면 하나, 1이면 셋.
            for (beat in 1..beats) {
                Box(Modifier.size(12.dp).background(if (beat <= beats + 1 - count) colors.text else colors.line, CircleShape))
            }
        }
    }
}
