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
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.StoreKit.SKStoreProductParameterITunesItemIdentifier
import platform.StoreKit.SKStoreProductViewController
import platform.StoreKit.SKStoreProductViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iTunes Lookup API로 App Store의 현재 버전을 확인한다(S15P31E102-1237). [client]는 서버 쿠키를 싣지 않는 별도 클라이언트다.
 * App Store에서 설치한 앱만 확인한다. Ad Hoc IPA·TestFlight 설치본은 같은 Bundle ID라도 App Store 버전으로 바꾸라고 안내하지 않는다.
 * iOS에는 앱 안에서 업데이트를 내려받는 공식 API가 없다. '업데이트하기'는 앱 안에 App Store 시트(`SKStoreProductViewController`)를
 * 띄워 그 자리에서 '업데이트'를 누르게 한다(S15P31E102-1257). 시트를 띄울 수 없으면 App Store 앱 페이지를 연다.
 */
internal class IosStoreUpdates(private val client: HttpClient, private val bundle: NSBundle = NSBundle.mainBundle) : StoreUpdates {
    private var pageUrl: String? = null
    private var appId: Long? = null

    // 시트의 delegate는 약한 참조라 시트가 닫힐 때까지 여기서 붙잡아 둔다.
    private var sheetDelegate: StoreSheetDelegate? = null

    override suspend fun latest(): StoreUpdate? {
        if (!installedFromAppStore()) return null
        val bundleId = bundle.bundleIdentifier ?: return null
        val installed = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: return null
        val response = client.get(appStoreLookupUrl(bundleId))
        if (!response.status.isSuccess()) return null
        val release = parseAppStoreLookup(response.bodyAsText()) ?: return null
        if (!isNewerVersion(release.version, installed)) return null
        pageUrl = release.url
        appId = release.id
        return StoreUpdate(release.version)
    }

    override fun open(update: StoreUpdate) {
        val id = appId
        val presenter = topViewController()
        if (id == null || presenter == null) {
            openPage()
            return
        }
        val sheet = SKStoreProductViewController()
        val delegate = StoreSheetDelegate { if (sheetDelegate === it) sheetDelegate = null }
        sheetDelegate = delegate
        sheet.delegate = delegate
        // 먼저 띄우고 불러온다. 불러오는 동안 시트가 로딩 표시를 보인다.
        presenter.presentViewController(sheet, animated = true, completion = null)
        sheet.loadProductWithParameters(
            mapOf<Any?, Any?>(SKStoreProductParameterITunesItemIdentifier to NSNumber.numberWithLongLong(id)),
        ) { loaded, _ ->
            if (!loaded) dispatch_async(dispatch_get_main_queue()) {
                sheet.dismissViewControllerAnimated(true) { openPage() }
                if (sheetDelegate === delegate) sheetDelegate = null
            }
        }
    }

    private fun openPage() {
        val url = pageUrl?.let { NSURL.URLWithString(it) } ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    @Suppress("DEPRECATION")
    private fun topViewController(): UIViewController? {
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        return presenter
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

/** 시트의 '완료'를 누르면 시트를 닫는다. */
private class StoreSheetDelegate(private val onFinish: (StoreSheetDelegate) -> Unit) : NSObject(), SKStoreProductViewControllerDelegateProtocol {
    override fun productViewControllerDidFinish(viewController: SKStoreProductViewController) {
        viewController.dismissViewControllerAnimated(true, completion = null)
        onFinish(this)
    }
}
