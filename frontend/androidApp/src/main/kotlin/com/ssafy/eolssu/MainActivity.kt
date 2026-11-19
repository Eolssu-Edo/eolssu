package com.ssafy.eolssu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.KeyEvent
import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaKind
import com.ssafy.eolssu.core.diagnostics.QaTarget
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.network.ServerAddress

class MainActivity : ComponentActivity() {
    // 간편 로그인 복귀 주소(App Links). 앱 화면이 꺼내 간다.
    private val incomingLinks = IncomingLinks()
    private val qaInput = QaInputObserver()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        qaInput.touch(event, window.decorView) { super.dispatchTouchEvent(event) }

    // androidx.core가 공개 Activity API인 dispatchKeyEvent 재정의에 @RestrictTo를 붙여 lint가 오탐하므로 이 함수만 억제한다.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        qaInput.key(event) { super.dispatchKeyEvent(event) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 앱은 밝은 테마를 사용하므로 시스템 다크 모드에서도 어두운 시스템 바 아이콘을 유지한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { false },
            navigationBarStyle = SystemBarStyle.auto(
                Color.argb(0xe6, 0xff, 0xff, 0xff),
                Color.argb(0x80, 0x1b, 0x1b, 0x1b),
            ) { false },
        )
        super.onCreate(savedInstanceState)
        lockPortraitOnPhone()
        QaPerformanceLog.install(applicationContext)
        // 화면을 돌려 다시 만들어질 때는 같은 Intent가 다시 오므로 처음 만들 때만 넘긴다.
        if (savedInstanceState == null) deliverLink(intent)

        setContent {
            App(context = applicationContext, serverAddress = ServerAddress(BuildConfig.API_BASE_URL), incomingLinks = incomingLinks,
                // TEMP S15P31E102-309: Android debug QA build is the only opt-in entry.
                qaSkipConsent = BuildConfig.DEBUG && BuildConfig.QA_SKIP_CONSENT)
        }
    }

    override fun onStart() {
        super.onStart()
        QaPerformanceLog.show(this)
        UserActionLog.event(QaKind.LIFECYCLE, QaTarget.ACTIVITY, com.ssafy.eolssu.core.diagnostics.QaOutcome.STARTED)
    }

    override fun onStop() {
        UserActionLog.event(QaKind.LIFECYCLE, QaTarget.ACTIVITY, com.ssafy.eolssu.core.diagnostics.QaOutcome.STOPPED)
        QaPerformanceLog.hide(this)
        super.onStop()
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

    // 매니페스트 configChanges로 회전·화면 분할·폴더블 접기에서는 액티비티가 다시 만들어지지 않으므로,
    // 최소 너비가 바뀌는 경우(폴더블 펴기, 화면 분할 시작·끝)를 여기서 다시 판정한다.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        UserActionLog.event(QaKind.LIFECYCLE, QaTarget.ACTIVITY)
        lockPortraitOnPhone(newConfig)
    }

    // 휴대폰(최소 너비 600dp 미만)만 세로로 고정한다. 매니페스트로 고정하면 Android 15 이하 태블릿도 고정되고,
    // Android 16부터는 최소 너비 600dp 이상 화면에서 방향 고정이 무시되므로 태블릿은 처음부터 고정하지 않는다.
    // 이전 요청이 남아 있을 수 있으므로 태블릿에서는 고정을 명시적으로 푼다.
    // 같은 값을 다시 요청하지 않아 설정 변경이 반복되지 않게 한다.
    private fun lockPortraitOnPhone(config: Configuration = resources.configuration) {
        val wanted = if (config.smallestScreenWidthDp < 600) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }
}
