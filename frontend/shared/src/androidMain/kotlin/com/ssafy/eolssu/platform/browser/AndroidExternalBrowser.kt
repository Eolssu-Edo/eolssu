package com.ssafy.eolssu.platform.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.ssafy.eolssu.core.browser.ExternalBrowser

/**
 * Custom Tabs 지원 서비스와 HTTPS 처리 기능이 있는 외부 브라우저로 주소를 연다.
 * 기본 링크 처리 앱이 내장 브라우저만 제공하면 지원되는 외부 브라우저를 대신 고른다.
 * 앱과 다른 작업(task)으로 띄워야 복귀 주소(App Links)가
 * 브라우저 위에 새 화면을 쌓지 않고 이미 떠 있는 `MainActivity`(singleTask)의 `onNewIntent`로 돌아온다.
 * 주소에는 state가 들어 있으므로 로그에 남기지 않는다.
 */
class AndroidExternalBrowser(private val context: Context) : ExternalBrowser {
    override fun open(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val packageManager = context.packageManager
        val browserPackage = selectExternalBrowserPackage(
            defaultPackage = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName,
            customTabsPackages = packageManager.queryIntentServices(Intent(CUSTOM_TABS_SERVICE_ACTION), 0)
                .mapNotNull { it.serviceInfo?.takeIf { service -> service.exported }?.packageName },
            viewHandlerPackages = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.takeIf { activity -> activity.exported }?.packageName }.toSet(),
        ) ?: return false
        intent.setPackage(browserPackage)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

private const val CUSTOM_TABS_SERVICE_ACTION = "android.support.customtabs.action.CustomTabsService"

/** 지원되는 기본 브라우저가 있으면 우선하고, 없으면 두 기능을 가진 첫 브라우저를 고른다. */
internal fun selectExternalBrowserPackage(
    defaultPackage: String?,
    customTabsPackages: List<String>,
    viewHandlerPackages: Set<String>,
): String? {
    val candidates = customTabsPackages.filter { it in viewHandlerPackages }
    return defaultPackage?.takeIf { it in candidates } ?: candidates.firstOrNull()
}
