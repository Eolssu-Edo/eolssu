package com.ssafy.eolssu.platform.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ssafy.eolssu.core.browser.ExternalBrowser

/**
 * 기기의 기본 브라우저로 주소를 연다. 앱과 다른 작업(task)으로 띄워야 복귀 주소(App Links)가
 * 브라우저 위에 새 화면을 쌓지 않고 이미 떠 있는 `MainActivity`(singleTask)의 `onNewIntent`로 돌아온다.
 * 주소에는 state가 들어 있으므로 로그에 남기지 않는다.
 */
class AndroidExternalBrowser(private val context: Context) : ExternalBrowser {
    override fun open(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
