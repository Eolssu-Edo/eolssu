package com.ssafy.eolssu.core.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler

/**
 * Custom Tabs 약속(세션 extra)을 붙인 보기 Intent를 화면(Activity)에서 바로 띄운다. 새 작업을 만들지 않아
 * 브라우저 탭이 앱 위에 쌓이고, 뒤로 가기로 탭을 닫으면 앱 화면으로 돌아온다. 지원 브라우저가 없으면 기본 링크 열기로 연다.
 */
@Composable
actual fun rememberInAppLinkOpener(): (String) -> Unit {
    val context = LocalContext.current
    val fallback = LocalUriHandler.current
    return remember(context, fallback) {
        { url ->
            val activity = context.findActivity()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .putExtras(Bundle().apply { putBinder(EXTRA_SESSION, null) })
            if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                (activity ?: context).startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                fallback.openUri(url)
            }
        }
    }
}

private const val EXTRA_SESSION = "android.support.customtabs.extra.SESSION"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
