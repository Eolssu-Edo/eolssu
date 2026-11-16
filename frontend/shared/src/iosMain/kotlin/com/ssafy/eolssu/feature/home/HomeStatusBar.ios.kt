package com.ssafy.eolssu.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyleUnspecified

/**
 * SwiftUI가 감싼 화면은 하위 화면의 `preferredStatusBarStyle`을 따르지 않는다. 기본 상태 표시줄은 창의 밝기 모드를 따르므로
 * 그림 위에서는 창을 어두운 모드로 두어 아이콘을 밝게 하고, 아니면 시스템 설정(미지정)으로 되돌린다.
 * 앱 색은 시스템 밝기 모드를 읽지 않아 화면 색은 바뀌지 않는다.
 */
@Composable
internal actual fun HomeStatusBarIcons(light: Boolean) {
    val controller = LocalUIViewController.current
    fun apply(style: UIUserInterfaceStyle) {
        controller.view.window?.overrideUserInterfaceStyle = style
    }
    SideEffect { apply(if (light) UIUserInterfaceStyleDark else UIUserInterfaceStyleUnspecified) }
    DisposableEffect(controller) { onDispose { apply(UIUserInterfaceStyleUnspecified) } }
}
