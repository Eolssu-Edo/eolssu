package com.ssafy.eolssu

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ssafy.eolssu.platform.androidPlatformModule

/** Android 진입점(`MainActivity`)이 부르는 `App`. [context]로 플랫폼 구현(저장소 등)을 만든다. */
@Composable
fun App(context: Context) {
    val platformModule = remember(context) { androidPlatformModule(context) }
    App(platformModule = platformModule)
}
