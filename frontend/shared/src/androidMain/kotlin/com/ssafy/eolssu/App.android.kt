package com.ssafy.eolssu

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.platform.androidPlatformModule
import com.ssafy.eolssu.platform.permission.AndroidMicPermission
import com.ssafy.eolssu.core.network.ServerAddress

/**
 * Android 진입점(`MainActivity`)이 부르는 `App`. [context]로 플랫폼 구현(저장소 등)을 만든다.
 * [incomingLinks]에는 `MainActivity`가 간편 로그인 복귀 주소(App Links)를 넣는다.
 */
@Composable
fun App(context: Context, serverAddress: ServerAddress = ServerAddress.Production, incomingLinks: IncomingLinks = remember { IncomingLinks() },
    // TEMP S15P31E102-309: remove parameter and forwarding after consent recovery.
    qaSkipConsent: Boolean = false,
) {
    val platformModule = remember(context, serverAddress.baseUrl, incomingLinks) { androidPlatformModule(context, serverAddress, incomingLinks) }
    // 마이크 권한 창은 화면에 등록한 요청으로만 띄울 수 있어 여기서 등록해 넘긴다.
    val micPermission = remember(context) { AndroidMicPermission.of(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), micPermission::onResult)
    // 거부 뒤 설명을 보일 필요가 없다고 하면(‘다시 묻지 않음’) 연습 화면이 ‘설정에서 허용하기’ 안내를 띄운다.
    // [context]는 applicationContext라 Activity가 아니다. 판정은 권한 창을 띄운 Activity(이 화면을 그리는 Activity)로 한다.
    val activity = LocalActivity.current
    DisposableEffect(launcher, activity) {
        micPermission.launcher = launcher
        micPermission.shouldShowRationale = activity?.let { { it.shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO) } }
        onDispose {
            if (micPermission.launcher === launcher) {
                micPermission.launcher = null
                micPermission.shouldShowRationale = null
            }
        }
    }
    App(platformModule = platformModule, qaSkipConsent = qaSkipConsent)
}
