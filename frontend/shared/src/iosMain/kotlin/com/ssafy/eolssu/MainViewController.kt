package com.ssafy.eolssu

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import com.ssafy.eolssu.platform.iosPlatformModule

/**
 * iOS 진입점(`ContentView`)이 부르는 화면. 플랫폼 구현(저장소 등)을 `App`에 넘긴다.
 * 키보드 회피는 화면 틀의 `imePadding`(`EolssuScaffold` 등)이 맡으므로, iOS가 입력칸을 키보드 위로 따로 끌어올리는
 * 기본 동작(`FocusableAboveKeyboard`)은 끈다. 둘이 함께 움직이면 하단 버튼이 두 번 밀리거나 튄다(S15P31E102-973).
 */
fun MainViewController() = iosPlatformModule().let { platformModule ->
    ComposeUIViewController(configure = { onFocusBehavior = OnFocusBehavior.DoNothing }) {
        App(platformModule = platformModule)
    }
}
