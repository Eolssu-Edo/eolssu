package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.ui.component.StartMessage
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.OAuthPurpose
import com.ssafy.eolssu.data.user.VerificationPurpose
import com.ssafy.eolssu.feature.auth.AccountLinkRoute
import com.ssafy.eolssu.feature.auth.ConsentRoute
import com.ssafy.eolssu.feature.auth.EmailVerificationRoute
import com.ssafy.eolssu.feature.auth.LoginRoute
import com.ssafy.eolssu.feature.auth.OAuthRoute
import com.ssafy.eolssu.feature.auth.PasswordSetupRoute
import com.ssafy.eolssu.feature.auth.ProfileSetupRoute
import com.ssafy.eolssu.feature.auth.SignupPath
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.feature.auth.TermsRoute
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_link_done
import eolssu.shared.generated.resources.auth_reset_done_notice
import eolssu.shared.generated.resources.my_link_done
import eolssu.shared.generated.resources.my_logged_out
import eolssu.shared.generated.resources.member_api_session_expired
import eolssu.shared.generated.resources.my_nickname_changed
import eolssu.shared.generated.resources.my_profile_image_changed
import eolssu.shared.generated.resources.my_password_changed
import eolssu.shared.generated.resources.my_withdrawn
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.getKoin

/** 첫 실행·로그인·회원가입·비밀번호 찾기 화면 경로. */
object AuthDestination {
    const val TERMS = "auth/terms/{agreement}"
    const val LOGIN = "auth/login"
    const val OAUTH = "auth/oauth/{provider}"
    const val LINK = "auth/link/{provider}"
    const val SIGNUP = "auth/signup"

    /**
     * 회원가입 첫 단계인 약관 동의. 동의하면 `next`(이메일 가입 [SIGNUP] 또는 간편 로그인 가입 [SOCIAL_PROFILE])로 간다.
     * 다음 단계에서 뒤로 가면 이 화면으로 돌아와 고른 체크를 그대로 보여 준다.
     */
    const val SIGNUP_CONSENT = "auth/signup/consent?next={next}"
    const val SIGNUP_PASSWORD = "auth/signup/password"
    const val PROFILE = "auth/profile"

    /** 간편 로그인 가입의 프로필 설정(AUTH-07). 이메일 인증·비밀번호 단계가 없다. */
    const val SOCIAL_PROFILE = "auth/social-signup/profile"
    const val PASSWORD_RESET = "auth/password-reset"
    const val PASSWORD_RESET_NEW = "auth/password-reset/password"

    fun terms(termId: String) = "auth/terms/$termId"

    fun signupConsent(next: String) = "auth/signup/consent?next=$next"

    fun oauth(provider: SocialProvider) = "auth/oauth/${provider.name}"

    fun link(provider: SocialProvider) = "auth/link/${provider.name}"
}

/**
 * 로그인한 회원이 있으면 홈, 없으면 로그인에서 시작한다([startNeedsLogin]). 처음 켠 기기, 로그아웃 뒤 다시 켠 기기, 세션이 만료된 기기가 로그인으로 간다.
 * 약관 동의는 회원가입 흐름([AuthDestination.SIGNUP_CONSENT])에서, 마이크 권한은 처음 녹음을 시작할 때 연습 화면에서 받는다.
 * TEMP S15P31E102-309: [qaSkipConsent]는 첫 실행 약관 동의를 건너뛰던 QA 빌드 값이다. 첫 실행에 약관 화면이 없어져 시작 화면은 같다.
 */
@Suppress("UNUSED_PARAMETER")
fun startDestination(
    firstRunCompleted: Boolean,
    qaSkipConsent: Boolean = false,
    sessionExpired: Boolean = false,
    signedIn: Boolean = true,
): String = if (startNeedsLogin(firstRunCompleted, sessionExpired, signedIn)) AuthDestination.LOGIN else EolssuDestination.HOME

/**
 * 앱을 켤 때 로그인이 필요한지. 첫 실행(로그인·가입)을 마치지 않았거나, 로그인한 회원이 없거나, 세션이 만료됐으면 필요하다.
 * 세션 만료일 때만 '로그인이 만료됐어요' 안내를 띄우고, 처음 켰거나 로그아웃 뒤 다시 켠 경우는 안내 없이 로그인 화면을 보인다.
 */
internal fun startNeedsLogin(firstRunCompleted: Boolean, sessionExpired: Boolean, signedIn: Boolean): Boolean =
    !firstRunCompleted || sessionExpired || !signedIn

/** 복원 중인 일회성 OAuth 완료 요청과 가입 흐름을 초기 세션 확인이 덮지 않는다. */
internal fun shouldRedirectExpiredSession(route: String?): Boolean =
    route == AuthDestination.LOGIN || route?.startsWith("auth/") != true

/**
 * 다음 화면에 들어가면 한 번 띄울 안내. 이동한 화면의 저장 상태에 이름으로 남겨 화면을 돌려도 유지된다.
 * 계정 연결 안내는 문구에 간편 로그인 제공자 이름을 넣으므로 제공자도 함께 남긴다.
 */
enum class StartNotice(val message: StringResource) {
    /** 로그인 중 계정 연결(AUTH-09) 뒤 홈. */
    AccountLinked(Res.string.auth_link_done),
    PasswordChanged(Res.string.auth_reset_done_notice),
    NicknameChanged(Res.string.my_nickname_changed),
    ProfileImageChanged(Res.string.my_profile_image_changed),
    MyPasswordChanged(Res.string.my_password_changed),

    /** 마이페이지 간편 로그인 연결(MY-05) 뒤 마이페이지. */
    MyAccountLinked(Res.string.my_link_done),
    LoggedOut(Res.string.my_logged_out),
    SessionExpired(Res.string.member_api_session_expired),
    Withdrawn(Res.string.my_withdrawn),
}

private const val START_NOTICE_KEY = "start_notice"
private const val START_NOTICE_PROVIDER_KEY = "start_notice_provider"
private const val VERIFIED_EMAIL_KEY = "verified_email"

private fun NavBackStackEntry.putStartNotice(notice: StartNotice, provider: SocialProvider?) {
    savedStateHandle[START_NOTICE_KEY] = notice.name
    if (provider != null) savedStateHandle[START_NOTICE_PROVIDER_KEY] = provider.name
}

/** [route]로 이동하고, 새 화면에 [notice]를 남긴다. [provider]는 안내 문구에 넣을 간편 로그인 제공자다. */
fun NavController.navigateWithNotice(
    route: String,
    notice: StartNotice?,
    provider: SocialProvider? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route, builder)
    if (notice != null) currentBackStackEntry?.putStartNotice(notice, provider)
}

/** 앞 화면으로 돌아가며 그 화면에 [notice]를 남긴다(마이페이지에서 닉네임·비밀번호를 바꾼 뒤 등). */
fun NavController.popWithNotice(notice: StartNotice, provider: SocialProvider? = null) {
    previousBackStackEntry?.putStartNotice(notice, provider)
    popBackStack()
}

/** 이 화면에 남은 안내. 띄운 뒤에는 [clearStartNotice]로 지운다. */
fun NavBackStackEntry.startNotice(): StartMessage? {
    val notice = savedStateHandle.get<String>(START_NOTICE_KEY)?.let { name -> StartNotice.entries.firstOrNull { it.name == name } } ?: return null
    val provider = savedStateHandle.get<String>(START_NOTICE_PROVIDER_KEY)?.let { name -> SocialProvider.entries.firstOrNull { it.name == name } }
    return StartMessage(notice.message, provider?.displayName)
}

fun NavBackStackEntry.clearStartNotice() {
    savedStateHandle.remove<String>(START_NOTICE_KEY)
    savedStateHandle.remove<String>(START_NOTICE_PROVIDER_KEY)
}

/**
 * 첫 실행을 마쳤다고 기기에 남기고 홈으로 간다. 뒤로 가기로 로그인·가입 화면에 돌아오지 않도록 쌓인 화면을 모두 비운다.
 * 홈에서 띄울 안내(계정 연결)는 [notice]로, 계정 연결 안내에 넣을 제공자는 [provider]로 넘긴다.
 */
fun NavController.finishFirstRun(firstRunRepository: FirstRunRepository, notice: StartNotice?, provider: SocialProvider? = null) {
    firstRunRepository.markCompleted()
    navigateWithNotice(EolssuDestination.HOME, notice, provider) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/** 인증한 이메일을 들고 [route]로 간다. 웹 주소창에 남지 않도록 경로가 아닌 새 화면의 저장 상태에 둔다. */
private fun NavController.navigateWithVerifiedEmail(route: String, email: String) {
    navigate(route)
    currentBackStackEntry?.savedStateHandle?.set(VERIFIED_EMAIL_KEY, email)
}

private fun NavBackStackEntry.verifiedEmail(): String = savedStateHandle.get<String>(VERIFIED_EMAIL_KEY).orEmpty()

/**
 * AUTH-01~11 화면 이동. 첫 실행은 로그인(또는 가입) → 홈.
 * 간편 로그인: 연결된 계정 → 홈, 같은 이메일 계정 → 계정 연결 확인(AUTH-09), 처음 온 계정 → 소셜 가입(프로필 설정 AUTH-07) → 홈.
 * 소셜 가입 전 서버가 '가입된 계정 없음'을 주면 이메일 가입을 안내한다.
 * 계정 연결 확인·가입으로 갈 때는 간편 로그인 화면을 비워 뒤로 가기로 다 쓴 제공자 화면에 돌아오지 않는다.
 * 가입은 항상 약관 동의(AUTH-01, [AuthDestination.SIGNUP_CONSENT])에서 시작하고, 약관 '보기'는 약관 상세(AUTH-02)를 열었다가 동의 화면으로 돌아온다.
 * 이메일 가입은 약관 동의 → 이메일 인증(AUTH-06) → 비밀번호 설정(AUTH-08) → 프로필 설정(AUTH-07) → 홈,
 * 비밀번호 찾기는 이메일 인증(AUTH-10) → 새 비밀번호(AUTH-11) → 로그인이다.
 * 마이크 권한은 여기서 묻지 않고 처음 녹음을 시작할 때 연습 화면이 묻는다.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onFinished: (notice: StartNotice?, provider: SocialProvider?) -> Unit,
) {
    val fromLogin: (String) -> Unit = { route -> navController.navigate(route) { popUpTo(AuthDestination.LOGIN) } }
    composable(
        AuthDestination.TERMS,
        arguments = listOf(navArgument("agreement") { type = NavType.StringType }),
    ) { entry ->
        val name = entry.savedStateHandle.get<String>("agreement")
        TermsRoute(
            termId = name.orEmpty(),
            onClose = { navController.popBackStackFrom(entry) },
        )
    }
    composable(AuthDestination.LOGIN) { entry ->
        val draft = getKoin().get<AccountDraft>()
        LaunchedEffect(draft) { draft.clearVerification() }
        LoginRoute(
            startMessage = entry.startNotice(),
            onStartMessageShown = entry::clearStartNotice,
            onLoggedIn = { onFinished(null, null) },
            onSocialLogIn = { navController.navigate(AuthDestination.oauth(it)) },
            onFindPassword = { navController.navigate(AuthDestination.PASSWORD_RESET) },
            onSignUp = { navController.navigate(AuthDestination.signupConsent(SignUpNext.Email.name)) },
            providers = socialProviders(),
        )
    }
    composable(
        AuthDestination.OAUTH,
        arguments = listOf(navArgument("provider") { type = NavType.StringType }),
    ) { entry ->
        val provider = entry.socialProvider() ?: SocialProvider.Kakao
        OAuthRoute(
            provider = provider,
            onBack = { navController.popBackStackFrom(entry) },
            onLoggedIn = { onFinished(null, null) },
            onLinkAvailable = { fromLogin(AuthDestination.link(provider)) },
            onSocialSignUp = { fromLogin(AuthDestination.signupConsent(SignUpNext.Social.name)) },
            onSignUp = { fromLogin(AuthDestination.signupConsent(SignUpNext.Email.name)) },
        )
    }
    composable(
        AuthDestination.SIGNUP_CONSENT,
        arguments = listOf(navArgument("next") { type = NavType.StringType; defaultValue = SignUpNext.Email.name }),
    ) { entry ->
        val next = entry.savedStateHandle.get<String>("next")?.let { name -> SignUpNext.entries.firstOrNull { it.name == name } } ?: SignUpNext.Email
        ConsentRoute(
            path = next.path,
            onBack = { navController.popBackStackFrom(entry) },
            onOpenTerms = { navController.navigate(AuthDestination.terms(it)) },
            // 동의 화면을 남겨 다음 단계에서 뒤로 가면 고른 체크 그대로 돌아온다.
            onStart = { navController.navigate(next.route) },
        )
    }
    composable(AuthDestination.SOCIAL_PROFILE) { entry ->
        ProfileSetupRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onDone = { onFinished(null, null) },
            social = true,
        )
    }
    composable(
        AuthDestination.LINK,
        arguments = listOf(navArgument("provider") { type = NavType.StringType }),
    ) { entry ->
        AccountLinkRoute(
            onCancel = { navController.popBackStackFrom(entry) },
            onFindPassword = { fromLogin(AuthDestination.PASSWORD_RESET) },
            onLinked = { onFinished(StartNotice.AccountLinked, it) },
        )
    }
    composable(AuthDestination.SIGNUP) { entry ->
        EmailVerificationRoute(
            purpose = VerificationPurpose.SignUp,
            onBack = { navController.popBackStackFrom(entry) },
            onVerified = { navController.navigateWithVerifiedEmail(AuthDestination.SIGNUP_PASSWORD, it) },
        )
    }
    composable(AuthDestination.SIGNUP_PASSWORD) { entry ->
        PasswordSetupRoute(
            purpose = VerificationPurpose.SignUp,
            email = entry.verifiedEmail(),
            onBack = { navController.popBackStackFrom(entry) },
            onDone = { navController.navigate(AuthDestination.PROFILE) },
        )
    }
    composable(AuthDestination.PROFILE) { entry ->
        ProfileSetupRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onDone = { onFinished(null, null) },
        )
    }
    composable(AuthDestination.PASSWORD_RESET) { entry ->
        EmailVerificationRoute(
            purpose = VerificationPurpose.PasswordReset,
            onBack = { navController.popBackStackFrom(entry) },
            onVerified = { navController.navigateWithVerifiedEmail(AuthDestination.PASSWORD_RESET_NEW, it) },
        )
    }
    composable(AuthDestination.PASSWORD_RESET_NEW) { entry ->
        PasswordSetupRoute(
            purpose = VerificationPurpose.PasswordReset,
            email = entry.verifiedEmail(),
            onBack = { navController.popBackStackFrom(entry) },
            // 바꾼 뒤 자동 로그인하지 않고 새 로그인 화면에서 다시 로그인한다.
            onDone = {
                navController.navigateWithNotice(AuthDestination.LOGIN, StartNotice.PasswordChanged) {
                    popUpTo(AuthDestination.LOGIN) { inclusive = true }
                }
            },
        )
    }
}

/** 가입 약관 동의 뒤 갈 곳과, 그 가입 경로의 단계([SignupPath], 단계 표시용). */
private enum class SignUpNext(val route: String, val path: SignupPath) {
    Email(AuthDestination.SIGNUP, SignupPath.Email),
    Social(AuthDestination.SOCIAL_PROFILE, SignupPath.Social),
}

private fun NavBackStackEntry.socialProvider(): SocialProvider? =
    savedStateHandle.get<String>("provider")?.let { name -> SocialProvider.entries.firstOrNull { it.name == name } }

/**
 * 간편 로그인을 마친 브라우저가 복귀 주소(App Links)로 앱을 열면, 그 시도를 시작한 화면(AUTH-05·MY-05·MY-04 재인증)이 주소를 받도록 그 화면으로 간다.
 * 이미 그 화면에 있으면 화면이 직접 꺼낸다. 진행 중 시도가 없으면(만료·다른 기기에서 연 주소 등) 주소를 버린다.
 * 앱이 꺼진 사이 돌아와도 시도 상태는 기기에 남아 있어 같은 화면으로 이어 간다.
 */
/** 이 플랫폼에서 보여 줄 간편 로그인 제공자. 플랫폼 모듈이 등록한 복귀 경로가 정한다(iOS만 Apple 포함). */
@Composable
internal fun socialProviders(): List<SocialProvider> =
    (getKoin().getOrNull<com.ssafy.eolssu.data.user.OAuthRoute>() ?: com.ssafy.eolssu.data.user.OAuthRedirect).providers

@Composable
fun OAuthReturnEffect(navController: NavController) {
    val links = getKoin().getOrNull<IncomingLinks>() ?: return
    val attempts = getKoin().get<OAuthAttempts>()
    val pending by links.pending.collectAsStateWithLifecycle()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(pending, route) {
        val uri = pending ?: return@LaunchedEffect
        // 화면 이동 그래프가 아직 없거나, 복귀를 받을 화면이 이미 떠 있다.
        if (route == null || route == AuthDestination.OAUTH || route == ProfileDestination.LINK || route == ProfileDestination.WITHDRAW) return@LaunchedEffect
        val target = attempts.pending()
        if (target == null) {
            links.take(uri)
            return@LaunchedEffect
        }
        navController.navigate(
            when (target.purpose) {
                OAuthPurpose.LogIn -> AuthDestination.oauth(target.provider)
                OAuthPurpose.Link -> ProfileDestination.link(target.provider)
                OAuthPurpose.Withdraw -> ProfileDestination.WITHDRAW
            },
        )
    }
}
