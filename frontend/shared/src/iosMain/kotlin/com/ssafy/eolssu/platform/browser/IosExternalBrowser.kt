package com.ssafy.eolssu.platform.browser

import com.ssafy.eolssu.core.browser.APP_CALLBACK_SCHEME
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.data.user.AppleNativeSignIn
import kotlinx.cinterop.ObjCSignatureOverride
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

/**
 * iOS 간편 로그인 화면. 제공자 화면은 시스템 인증 창(`ASWebAuthenticationSession`)으로 띄우고,
 * 서버 중계가 보낸 앱 전용 주소(`com.ssafy.eolssu://oauth/…`)를 받으면 [links]로 넘긴다(Android App Links 복귀와 같은 자리).
 * Apple은 인증 창 대신 기본 Apple 로그인(`ASAuthorizationController`)을 띄우고, 받은 코드로 같은 형식의 앱 주소를 만들어 넘긴다([AppleNativeSignIn]).
 * 사용자가 창을 닫으면 아무것도 넘기지 않는다. 화면은 Android에서 브라우저를 닫았을 때처럼 '다시 열기'를 보여 준다.
 * 주소에는 인가 코드·state가 들어 있어 로그에 남기지 않는다. 확인은 공통 코드(OAuthAttempts)가 한다.
 */
internal class IosExternalBrowser(private val links: IncomingLinks) : ExternalBrowser {
    // 진행 중 세션은 끝날 때까지 잡아 둔다. 참조를 놓으면 창이 바로 닫힌다.
    private var session: ASWebAuthenticationSession? = null
    private val anchor = SceneWindowAnchor()

    // 기본 Apple 로그인. 컨트롤러의 delegate는 약한 참조라 끝날 때까지 둘 다 잡아 둔다.
    private var appleController: ASAuthorizationController? = null
    private var appleDelegate: AppleSignInDelegate? = null

    override fun open(url: String): Boolean {
        AppleNativeSignIn.request(url)?.let { return signInWithApple(it) }
        val target = NSURL.URLWithString(url) ?: return false
        // '다시 열기'는 같은 주소로 새 창을 연다. 남은 창은 먼저 닫는다.
        session?.cancel()
        var opened: ASWebAuthenticationSession? = null
        val next = ASWebAuthenticationSession(uRL = target, callbackURLScheme = APP_CALLBACK_SCHEME) { callback, _ ->
            // 닫은 이전 창의 완료가 늦게 와도 새 창의 참조는 지우지 않는다.
            if (session === opened) session = null
            callback?.absoluteString?.let(links::deliver)
        }
        opened = next
        next.presentationContextProvider = anchor
        session = next
        return next.start().also { started -> if (!started) session = null }
    }

    private fun signInWithApple(request: AppleNativeSignIn.Request): Boolean {
        session?.cancel()
        val appleRequest = ASAuthorizationAppleIDProvider().createRequest().apply {
            requestedScopes = listOf(ASAuthorizationScopeEmail)
            // 서버가 state로 정한 nonce. 서버는 받은 id_token의 nonce가 이 값인지 확인한다.
            nonce = request.nonce
            state = request.state
        }
        val controller = ASAuthorizationController(authorizationRequests = listOf(appleRequest))
        val delegate = AppleSignInDelegate(request) { address ->
            // 닫은 이전 요청의 결과가 늦게 와도 새 요청의 참조는 지우지 않는다.
            if (appleController === controller) {
                appleController = null
                appleDelegate = null
            }
            address?.let(links::deliver)
        }
        controller.delegate = delegate
        controller.presentationContextProvider = delegate
        appleController = controller
        appleDelegate = delegate
        controller.performRequests()
        return true
    }
}

/** 기본 Apple 로그인 결과를 앱 복귀 주소로 바꾼다. 사용자가 닫으면 null(아무것도 넘기지 않음). */
private class AppleSignInDelegate(
    private val request: AppleNativeSignIn.Request,
    private val finish: (String?) -> Unit,
) : NSObject(), ASAuthorizationControllerDelegateProtocol, ASAuthorizationControllerPresentationContextProvidingProtocol {
    @ObjCSignatureOverride
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val code = credential?.authorizationCode?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
        finish(
            if (code.isNullOrBlank()) AppleNativeSignIn.returnAddress(request, error = "missing_code")
            else AppleNativeSignIn.returnAddress(request, code = code),
        )
    }

    @ObjCSignatureOverride
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        finish(if (didCompleteWithError.code == ASAuthorizationErrorCanceled) null else AppleNativeSignIn.returnAddress(request, error = "failed"))
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor = currentWindow()
}

/** 인증 창을 띄울 창. */
private class SceneWindowAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor = currentWindow()
}

/** 앱은 창 하나(SwiftUI `WindowGroup`)만 쓰므로 연결된 장면의 키 창, 없으면 그 장면의 첫 창이다. */
private fun currentWindow(): UIWindow {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    return scenes.firstNotNullOfOrNull { it.keyWindow }
        ?: scenes.firstNotNullOfOrNull { scene -> scene.windows.firstOrNull() as? UIWindow }
        // 창이 하나도 없으면 앱 화면도 없으므로 인증 창을 띄울 곳이 없다. 빈 창을 주고 실패는 시스템에 맡긴다.
        ?: UIWindow()
}
