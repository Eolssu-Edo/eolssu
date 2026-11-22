package com.ssafy.eolssu.platform.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.ssafy.eolssu.core.update.StoreUpdate
import com.ssafy.eolssu.core.update.StoreUpdates
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Play In-App Updates로 Play 스토어의 새 버전을 확인한다(S15P31E102-1237).
 * Play에서 설치하지 않은 앱(QA APK 등)은 Play가 업데이트 없음이나 오류로 답하므로 안내하지 않는다.
 * '업데이트'는 Play의 즉시 업데이트 화면을 띄운다. 띄울 [activity]가 없거나 즉시 업데이트를 쓸 수 없으면 Play 스토어 앱 페이지를 연다.
 */
internal class AndroidStoreUpdates(context: Context) : StoreUpdates {
    private val appContext = context.applicationContext
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)

    // 업데이트 화면은 확인에서 받은 정보로만 띄울 수 있고, 그 정보는 한 번만 쓸 수 있다.
    private var info: AppUpdateInfo? = null

    /** Play 업데이트 화면을 띄울 Activity. `App(context)`가 화면에 있는 동안만 넣는다. */
    var activity: Activity? = null

    override suspend fun latest(): StoreUpdate? {
        val found = manager.appUpdateInfo.await()
        if (found.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return null
        info = found
        return StoreUpdate(found.availableVersionCode().toString())
    }

    override fun open(update: StoreUpdate) {
        val found = info?.takeIf { it.availableVersionCode().toString() == update.version }
        val host = activity
        val options = AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
        if (found == null || host == null || !found.isUpdateTypeAllowed(options)) {
            openListing()
            return
        }
        info = null
        manager.startUpdateFlow(found, host, options).addOnFailureListener { openListing() }
    }

    private fun openListing() {
        val packageName = appContext.packageName
        try {
            appContext.startActivity(listing("market://details?id=$packageName"))
        } catch (e: ActivityNotFoundException) {
            // Play 스토어 앱이 없으면 웹 페이지로 연다.
            try {
                appContext.startActivity(listing("https://play.google.com/store/apps/details?id=$packageName"))
            } catch (e: ActivityNotFoundException) {
                // 열 수 있는 앱이 없다. 안내는 이미 닫혔으므로 앱을 그대로 쓴다.
            }
        }
    }

    private fun listing(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
}
