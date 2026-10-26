package com.ssafy.eolssu

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lockPortraitOnPhone()

        setContent {
            App()
        }
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
