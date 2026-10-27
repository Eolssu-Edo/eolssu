package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuTopBar

/**
 * 기록 화면들의 창 너비별 틀.
 *
 * @property gutter 본문 좌우 여백.
 * @property contentMaxWidth 여백을 포함한 본문 최대 너비([com.ssafy.eolssu.core.ui.component.EolssuScaffold]에 넘긴다).
 */
internal class HistoryFrame(
    val widthClass: WindowWidthClass,
    val gutter: Dp,
    val contentMaxWidth: Dp,
) {
    val isTablet: Boolean get() = widthClass != WindowWidthClass.Compact
}

/** 태블릿 세로 아트보드의 내용 너비(800 - 좌우 40). */
private val PortraitContentWidth: Dp = 720.dp

/**
 * 휴대폰: 여백 20dp · 최대 560dp. 태블릿 세로: 여백 40dp · 내용 720dp.
 * 태블릿 가로·넓은 웹 창: 여백 40dp · 내용 1120dp(1280dp 창에서 와이어프레임과 같은 좌우 80dp가 된다).
 */
internal fun historyFrame(widthClass: WindowWidthClass): HistoryFrame = when (widthClass) {
    WindowWidthClass.Compact -> HistoryFrame(widthClass, 20.dp, CompactContentMaxWidth)
    WindowWidthClass.Medium -> HistoryFrame(widthClass, 40.dp, PortraitContentWidth + 80.dp)
    WindowWidthClass.Expanded -> HistoryFrame(widthClass, 40.dp, WindowBreakpoints.ContentMaxWidth + 80.dp)
}

/**
 * 창 너비에 맞춘 상단 바. 태블릿 세로는 창 가장자리에서 32dp, 가로는 본문 왼쪽 끝에 뒤로 가기 버튼을 둔다.
 * (상단 바 안쪽 여백 20dp에 바깥 여백을 더한다.)
 */
@Composable
internal fun HistoryTopBar(frame: HistoryFrame, title: String, subtitle: String?, onBack: () -> Unit) {
    when (frame.widthClass) {
        WindowWidthClass.Compact -> EolssuTopBar(title, onBack = onBack, subtitle = subtitle)
        WindowWidthClass.Medium -> EolssuTopBar(
            title, onBack = onBack, subtitle = subtitle,
            modifier = Modifier.padding(horizontal = 12.dp),
            maxWidth = WindowBreakpoints.ContentMaxWidth,
        )
        WindowWidthClass.Expanded -> EolssuTopBar(
            title, onBack = onBack, subtitle = subtitle,
            modifier = Modifier.padding(horizontal = 20.dp),
            maxWidth = WindowBreakpoints.ContentMaxWidth + 40.dp,
        )
    }
}
