package com.ssafy.eolssu.core.ui.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 글자 링크(비밀번호 찾기·회원가입·약관 보기·회원 탈퇴·다른 곡 고르기·더 보기).
 * 누르는 영역은 48dp 이상이고, 눌림 효과는 글자 둘레의 [pillHeight] 높이 완전한 알약(모서리 = 높이/2) 안에만 그린다(S15P31E102-968).
 *
 * [reserveTouchHeight]가 false면 배치에는 알약 높이만 차지하고 48dp 누르는 영역은 위아래로 넘쳐 걸친다.
 * 섹션 머리의 '더 보기'처럼 줄 높이를 늘리면 안 되는 곳에 쓴다. 겹치는 뒤쪽 형제가 있으면 그쪽 누름이 먼저다.
 */
@Composable
fun EolssuTextLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pillHeight: Dp = TextLinkPillHeight,
    reserveTouchHeight: Boolean = true,
    qaTarget: QaTarget = QaTarget.UNIDENTIFIED,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (reserveTouchHeight) Modifier else Modifier.overflowTouchHeight(pillHeight))
            .sizeIn(minWidth = TextLinkTouch, minHeight = TextLinkTouch)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = UserActionLog.click(qaTarget, onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = pillHeight)
                .clip(CircleShape)
                .indication(interaction, ripple())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/** 재어 둔 높이 대신 [height]만 배치에 보고하고, 실제 내용은 가운데 맞춰 위아래로 넘치게 둔다. */
private fun Modifier.overflowTouchHeight(height: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val reported = minOf(height.roundToPx(), placeable.height)
    layout(placeable.width, reported) { placeable.place(0, (reported - placeable.height) / 2) }
}

/** 글자 링크의 보이는 알약 높이. 16sp 이하 글자의 위아래가 잘려 보이지 않는 높이다. */
private val TextLinkPillHeight = 36.dp

/** 손가락으로 누르는 영역의 한 변 최소 길이. */
private val TextLinkTouch = 48.dp
