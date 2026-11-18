package com.ssafy.eolssu.core.ui.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ssafy.eolssu.core.theme.EolssuTheme

/**
 * 앱 공통 드롭다운 메뉴 모양(S15P31E102-768): 테두리·구분선 없는 흰 카드(모서리 16dp)에 옅은 그림자,
 * 안쪽 여백 6dp. 고른 줄은 마이페이지 '연결'과 같은 연한 연두 바탕(`accentTint`)·짙은 연두 글자·확인 표시다.
 */
val EolssuMenuShape = RoundedCornerShape(16.dp)

/** 메뉴 카드. 팝업 안에 두고 [EolssuMenuItem]을 넣는다. [width]를 정하지 않으면 주어진 폭을 채운다. */
@Composable
fun EolssuMenuPanel(width: Dp = Dp.Unspecified, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .then(if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width))
            .shadow(16.dp, EolssuMenuShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(EolssuMenuShape)
            .background(EolssuTheme.colors.surface)
            .padding(6.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** 메뉴 한 줄(48dp, 모서리 10dp). [leading]이 있으면 글자 앞에 둔다(악기 그림 등). */
@Composable
fun EolssuMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = EolssuTheme.colors
    val ink = if (selected) colors.accentText else colors.text
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentTint else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = UserActionLog.click(QaTarget.EOLSSU_MENU_01, onClick))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = ink)
    }
}

/** 메뉴를 기준 영역의 어느 끝에 맞출지. [Start]는 왼쪽 끝, [End]는 오른쪽 끝이다. */
enum class EolssuMenuAlign { Start, End }

/**
 * 앱 공통 드롭다운 창(S15P31E102-970): 기준 영역(이 함수를 부른 `Box`) 아래에 [gap]만큼 띄워 [content]를 연다.
 * 아래 공간이 모자라면 위로 뒤집고([BelowAnchor]), 바깥을 누르거나 뒤로 가면 [onDismissRequest]로 닫는다.
 * 모든 드롭다운이 Material `DropdownMenu` 대신 이 창과 [EolssuMenuPanel]을 쓴다.
 */
@Composable
fun EolssuMenuPopup(
    onDismissRequest: () -> Unit,
    align: EolssuMenuAlign = EolssuMenuAlign.Start,
    gap: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    Popup(
        popupPositionProvider = remember(gapPx, align) { BelowAnchor(gapPx, align) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
        content = { QaModalObservation(); content() },
    )
}

/**
 * 메뉴를 기준 영역 아래에 [gap]만큼 띄워 두고, 화면 아래로 넘치면 기준 영역 위로 뒤집는다.
 * 위아래 모두 모자라면 공간이 더 넓은 쪽에 두고 화면 안으로 당긴다. 가로는 [align] 끝에 맞추고 화면 밖으로 나가지 않게 당긴다.
 */
internal class BelowAnchor(private val gap: Int, private val align: EolssuMenuAlign = EolssuMenuAlign.Start) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val x = when (align) {
            EolssuMenuAlign.Start -> anchorBounds.left
            EolssuMenuAlign.End -> anchorBounds.right - width
        }.coerceIn(0, (windowSize.width - width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - height
        val roomBelow = windowSize.height - below
        val roomAbove = anchorBounds.top - gap
        val y = when {
            height <= roomBelow -> below
            height <= roomAbove -> above
            roomAbove > roomBelow -> above
            else -> below
        }.coerceIn(0, (windowSize.height - height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
