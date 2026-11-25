package com.ssafy.eolssu.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 배치 판정 기준값. 근거와 사용 방침은 `frontend/docs/architecture.md` 9장에 둔다.
 */
object WindowBreakpoints {
    /** 이 너비부터 [WindowWidthClass.Medium] (태블릿 세로) */
    val MediumMinWidth: Dp = 600.dp

    /** 이 너비부터 [WindowWidthClass.Expanded] (태블릿 가로·넓은 웹 창) */
    val ExpandedMinWidth: Dp = 840.dp

    /** 넓은 창에서 내용을 가운데에 둘 때의 최대 너비 */
    val ContentMaxWidth: Dp = 1120.dp

    /** 태블릿 가로 배치에서 12정간 줄을 정간 44dp로 세로 스크롤 없이 넣는 데 필요한 창 높이 */
    val LandscapeLayoutMinHeight: Dp = 650.dp

    /** 이 높이 미만이면 [WindowLayout.isCompactHeight] (휴대폰 가로 웹 창처럼 아주 낮은 창). Material 3의 Compact 높이 기준과 같다. */
    val MediumMinHeight: Dp = 480.dp
}

/** 창 너비 구간. 기기 종류가 아니라 지금 앱이 쓰는 창의 너비로 정한다. */
enum class WindowWidthClass {
    /** 600dp 미만: 휴대폰 */
    Compact,

    /** 600dp 이상 840dp 미만: 태블릿 세로 */
    Medium,

    /** 840dp 이상: 태블릿 가로·넓은 웹 창 */
    Expanded,
}

/**
 * 화면이 배치를 고를 때 쓰는 창 크기 판정 결과.
 *
 * @property isShortHeight 창 높이가 [WindowBreakpoints.LandscapeLayoutMinHeight]보다 낮은지.
 * 너비는 넓지만 높이가 낮은 창(납작한 웹 창, 태블릿 화면 분할)을 가려낸다.
 * @property isCompactHeight 창 높이가 [WindowBreakpoints.MediumMinHeight]보다 낮은지. 휴대폰 가로 웹 창처럼
 * 어느 배치도 한 화면에 담기 어려운 창을 가려낸다. 연습 화면은 이때 페이지 전체를 세로로 스크롤한다.
 */
data class WindowLayout(
    val widthClass: WindowWidthClass,
    val isShortHeight: Boolean,
    val isCompactHeight: Boolean = false,
)

fun windowLayoutOf(width: Dp, height: Dp): WindowLayout {
    val widthClass = when {
        width < WindowBreakpoints.MediumMinWidth -> WindowWidthClass.Compact
        width < WindowBreakpoints.ExpandedMinWidth -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }
    return WindowLayout(
        widthClass = widthClass,
        isShortHeight = height < WindowBreakpoints.LandscapeLayoutMinHeight,
        isCompactHeight = height < WindowBreakpoints.MediumMinHeight,
    )
}

/** 현재 창 크기로 판정한다. 창 크기가 바뀌면(회전, 화면 분할, 웹 창 조절) 다시 계산된다. */
@Composable
fun currentWindowLayout(): WindowLayout {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return remember(size, density) {
        with(density) { windowLayoutOf(size.width.toDp(), size.height.toDp()) }
    }
}
