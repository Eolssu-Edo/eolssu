package com.ssafy.eolssu.platform.update

import com.ssafy.eolssu.core.update.StoreUpdate
import com.ssafy.eolssu.core.update.StoreUpdates
import com.ssafy.eolssu.core.update.appStoreLookupUrl
import com.ssafy.eolssu.core.update.isNewerVersion
import com.ssafy.eolssu.core.update.parseAppStoreLookup
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iTunes Lookup API로 App Store의 현재 버전을 확인한다(S15P31E102-1237). [client]는 서버 쿠키를 싣지 않는 별도 클라이언트다.
 * App Store에서 설치한 앱만 확인한다. Ad Hoc IPA·TestFlight 설치본은 같은 Bundle ID라도 App Store 버전으로 바꾸라고 안내하지 않는다.
 * '업데이트'는 App Store 앱 페이지를 연다.
 */
internal class IosStoreUpdates(private val client: HttpClient, private val bundle: NSBundle = NSBundle.mainBundle) : StoreUpdates {
    private var pageUrl: String? = null

    override suspend fun latest(): StoreUpdate? {
        if (!installedFromAppStore()) return null
        val bundleId = bundle.bundleIdentifier ?: return null
        val installed = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: return null
        val response = client.get(appStoreLookupUrl(bundleId))
        if (!response.status.isSuccess()) return null
        val release = parseAppStoreLookup(response.bodyAsText()) ?: return null
        if (!isNewerVersion(release.version, installed)) return null
        pageUrl = release.url
        return StoreUpdate(release.version)
    }

    override fun open(update: StoreUpdate) {
        val url = pageUrl?.let { NSURL.URLWithString(it) } ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    // App Store 설치본에는 영수증 파일이 있고, TestFlight는 `sandboxReceipt`, Ad Hoc·개발 설치본은 영수증이 없다.
    // `appStoreReceiptURL`은 iOS 18부터 지원 중단 예정이지만 아직 동작하며, 대체 API(StoreKit 2 AppTransaction)는 Swift 전용이다.
    @Suppress("DEPRECATION")
    private fun installedFromAppStore(): Boolean {
        val receipt = bundle.appStoreReceiptURL ?: return false
        if (receipt.lastPathComponent == "sandboxReceipt") return false
        val path = receipt.path ?: return false
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }
}
