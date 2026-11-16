package com.ssafy.eolssu.feature.home

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
internal actual fun HomeStatusBarIcons(light: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    // MainActivity가 정한 앱 기본(밝은 바탕용 어두운 아이콘)을 기억해 홈을 떠날 때 되돌린다.
    val appDefault = remember(controller) { controller.isAppearanceLightStatusBars }
    SideEffect { controller.isAppearanceLightStatusBars = if (light) false else appDefault }
    DisposableEffect(controller) { onDispose { controller.isAppearanceLightStatusBars = appDefault } }
}
