package com.ssafy.eolssu

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.network.ServerAddress

class MainActivity : ComponentActivity() {
    // 간편 로그인 복귀 주소(App Links). 앱 화면이 꺼내 간다.
    private val incomingLinks = IncomingLinks()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lockPortraitOnPhone()
        // 화면을 돌려 다시 만들어질 때는 같은 Intent가 다시 오므로 처음 만들 때만 넘긴다.
        if (savedInstanceState == null) deliverLink(intent)

        setContent {
            App(context = applicationContext, serverAddress = ServerAddress(BuildConfig.API_BASE_URL), incomingLinks = incomingLinks)
        }
    }

    // singleTask라 앱이 떠 있을 때의 복귀는 새 화면 대신 여기로 온다.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverLink(intent)
    }

    // 주소에는 인가 코드·state가 들어 있어 로그에 남기지 않는다. 확인은 공통 코드(OAuthAttempts)가 한다.
    private fun deliverLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(incomingLinks::deliver)
    }

    // 휴대폰(최소 너비 600dp 미만)만 세로로 고정한다. 매니페스트로 고정하면 Android 15 이하 태블릿도 고정되고,
    // Android 16부터는 최소 너비 600dp 이상 화면에서 방향 고정이 무시되므로 태블릿은 처음부터 고정하지 않는다.
    // 폴더블을 펴거나 화면 분할을 끝내면 액티비티가 다시 만들어지며 이 판정도 다시 한다.
    // 다시 만들어진 액티비티는 이전 요청을 이어받으므로 태블릿에서는 고정을 명시적으로 푼다.
    private fun lockPortraitOnPhone() {
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
