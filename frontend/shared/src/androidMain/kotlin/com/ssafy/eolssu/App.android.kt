package com.ssafy.eolssu

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.ssafy.eolssu.platform.androidPlatformModule
import com.ssafy.eolssu.platform.permission.AndroidMicPermission

/** Android 진입점(`MainActivity`)이 부르는 `App`. [context]로 플랫폼 구현(저장소 등)을 만든다. */
@Composable
fun App(context: Context) {
    val platformModule = remember(context) { androidPlatformModule(context) }
    // 마이크 권한 창은 화면에 등록한 요청으로만 띄울 수 있어 여기서 등록해 넘긴다.
    val micPermission = remember(context) { AndroidMicPermission.of(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), micPermission::onResult)
    DisposableEffect(launcher) {
        micPermission.launcher = launcher
        onDispose { if (micPermission.launcher === launcher) micPermission.launcher = null }
    }
    App(platformModule = platformModule)
}
