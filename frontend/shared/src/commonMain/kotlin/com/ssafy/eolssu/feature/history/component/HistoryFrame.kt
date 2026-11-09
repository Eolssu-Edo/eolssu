package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuBackButton
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
internal fun HistoryTopBar(frame: HistoryFrame, title: String, subtitle: String?, onBack: () -> Unit, bottom: Dp = 12.dp) {
    when (frame.widthClass) {
        WindowWidthClass.Compact -> HistoryBoardTopBar(title, subtitle, onBack, bottom = bottom)
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

/**
 * 보드 기록·성장 화면의 상단 바: 40dp 뒤로 가기 꺽쇠([EolssuBackButton]) · 가운데 제목(16sp 굵게)과 보조 문구(12sp) · 오른쪽 빈 칸.
 * 아래 여백은 화면마다 다르다(목록 12 · 상세 8 · 기록지 10).
 */
@Composable
internal fun HistoryBoardTopBar(title: String, subtitle: String?, onBack: () -> Unit, bottom: Dp = 12.dp) {
    val colors = EolssuTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EolssuBackButton(onBack, size = 40.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = colors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(40.dp))
    }
}
