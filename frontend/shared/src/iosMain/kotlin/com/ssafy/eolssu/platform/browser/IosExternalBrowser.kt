package com.ssafy.eolssu.platform.browser

import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.data.user.IosOAuthRelay
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * iOS 간편 로그인 화면. 제공자 화면은 시스템 인증 창(`ASWebAuthenticationSession`)으로 띄우고,
 * 서버 중계가 보낸 앱 전용 주소(`com.ssafy.eolssu://oauth/…`)를 받으면 [links]로 넘긴다(Android App Links 복귀와 같은 자리).
 * 사용자가 창을 닫으면 아무것도 넘기지 않는다. 화면은 Android에서 브라우저를 닫았을 때처럼 '다시 열기'를 보여 준다.
 * 주소에는 인가 코드·state가 들어 있어 로그에 남기지 않는다. 확인은 공통 코드(OAuthAttempts)가 한다.
 */
internal class IosExternalBrowser(private val links: IncomingLinks) : ExternalBrowser {
    // 진행 중 세션은 끝날 때까지 잡아 둔다. 참조를 놓으면 창이 바로 닫힌다.
    private var session: ASWebAuthenticationSession? = null
    private val anchor = KeyWindowAnchor()

    override fun open(url: String): Boolean {
        val target = NSURL.URLWithString(url) ?: return false
        // '다시 열기'는 같은 주소로 새 창을 연다. 남은 창은 먼저 닫는다.
        session?.cancel()
        val next = ASWebAuthenticationSession(uRL = target, callbackURLScheme = IosOAuthRelay.SCHEME) { callback, _ ->
            session = null
            callback?.absoluteString?.let(links::deliver)
        }
        next.presentationContextProvider = anchor
        session = next
        return next.start().also { started -> if (!started) session = null }
    }
}

/** 인증 창을 띄울 창. 앱은 창 하나(SwiftUI `WindowGroup`)만 쓴다. */
private class KeyWindowAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    @Suppress("DEPRECATION")
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
