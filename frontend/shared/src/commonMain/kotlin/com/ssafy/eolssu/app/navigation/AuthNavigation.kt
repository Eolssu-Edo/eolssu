package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ssafy.eolssu.core.audio.MicPermission
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
import com.ssafy.eolssu.feature.auth.MicPermissionScreen
import com.ssafy.eolssu.feature.auth.OAuthRoute
import com.ssafy.eolssu.feature.auth.PasswordSetupRoute
import com.ssafy.eolssu.feature.auth.ProfileSetupRoute
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.feature.auth.TermsRoute
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_link_done
import eolssu.shared.generated.resources.auth_mic_denied_notice
import eolssu.shared.generated.resources.auth_reset_done_notice
import eolssu.shared.generated.resources.auth_welcome
import eolssu.shared.generated.resources.my_link_done
import eolssu.shared.generated.resources.my_logged_out
import eolssu.shared.generated.resources.my_nickname_changed
import eolssu.shared.generated.resources.my_password_changed
import eolssu.shared.generated.resources.my_withdrawn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.getKoin

/** 첫 실행·로그인·회원가입·비밀번호 찾기 화면 경로. */
object AuthDestination {
    const val CONSENT = "auth/consent"
    const val TERMS = "auth/terms/{agreement}"
    const val MIC = "auth/mic"
    const val LOGIN = "auth/login"
    const val OAUTH = "auth/oauth/{provider}"
    const val LINK = "auth/link/{provider}"
    const val SIGNUP = "auth/signup"
    const val SIGNUP_PASSWORD = "auth/signup/password"
    const val PROFILE = "auth/profile"
    const val PASSWORD_RESET = "auth/password-reset"
    const val PASSWORD_RESET_NEW = "auth/password-reset/password"

    fun terms(termId: String) = "auth/terms/$termId"

    fun oauth(provider: SocialProvider) = "auth/oauth/${provider.name}"

    fun link(provider: SocialProvider) = "auth/link/${provider.name}"
}

/** 첫 실행을 마쳤으면 홈, 아니면 약관 동의에서 시작한다. */
fun startDestination(firstRunCompleted: Boolean): String =
    if (firstRunCompleted) EolssuDestination.HOME else AuthDestination.CONSENT

/**
 * 다음 화면에 들어가면 한 번 띄울 안내. 이동한 화면의 저장 상태에 이름으로 남겨 화면을 돌려도 유지된다.
 * 계정 연결 안내는 문구에 간편 로그인 제공자 이름을 넣으므로 제공자도 함께 남긴다.
 */
enum class StartNotice(val message: StringResource) {
    MicDenied(Res.string.auth_mic_denied_notice),
    Welcome(Res.string.auth_welcome),

    /** 로그인 중 계정 연결(AUTH-09) 뒤 홈. */
    AccountLinked(Res.string.auth_link_done),
    PasswordChanged(Res.string.auth_reset_done_notice),
    NicknameChanged(Res.string.my_nickname_changed),
    MyPasswordChanged(Res.string.my_password_changed),

    /** 마이페이지 간편 로그인 연결(MY-05) 뒤 마이페이지. */
    MyAccountLinked(Res.string.my_link_done),
    LoggedOut(Res.string.my_logged_out),
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
 * 홈에서 띄울 안내(가입 환영·계정 연결)는 [notice]로, 계정 연결 안내에 넣을 제공자는 [provider]로 넘긴다.
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
 * AUTH-01~11 화면 이동. 약관 동의 → 마이크 안내 → 로그인 → 홈.
 * 간편 로그인은 로그인 전용이다: 연결된 계정 → 홈, 같은 이메일 계정 → 계정 연결 확인(AUTH-09), 없음 → 이메일 가입 안내.
 * 계정 연결 확인·이메일 가입으로 갈 때는 간편 로그인 화면을 비워 뒤로 가기로 다 쓴 제공자 화면에 돌아오지 않는다.
 * 이메일 가입은 이메일 인증(AUTH-06) → 비밀번호 설정(AUTH-08) → 프로필 설정(AUTH-07) → 홈,
 * 비밀번호 찾기는 이메일 인증(AUTH-10) → 새 비밀번호(AUTH-11) → 로그인이다.
 * 로그인 화면에 들어가면 약관 동의·마이크 안내는 비워 뒤로 가기로 돌아가지 않는다.
 * 마이크 안내에서 '허용'하면 OS 권한 창을 띄우고, 거기서도 허용하면 [onMicAllowed]로 남겨 연습 화면에서 다시 묻지 않는다.
 * OS 권한 창에서 거부하면 '허용 안 함'과 같게 처리한다.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onMicAllowed: () -> Unit,
    onFinished: (notice: StartNotice?, provider: SocialProvider?) -> Unit,
) {
    val toLogin: (StartNotice?) -> Unit = { notice ->
        navController.navigateWithNotice(AuthDestination.LOGIN, notice) {
            popUpTo(AuthDestination.CONSENT) { inclusive = true }
        }
    }
    val fromLogin: (String) -> Unit = { route -> navController.navigate(route) { popUpTo(AuthDestination.LOGIN) } }
    composable(AuthDestination.CONSENT) {
        ConsentRoute(
            onOpenTerms = { navController.navigate(AuthDestination.terms(it)) },
            onStart = { navController.navigate(AuthDestination.MIC) },
        )
    }
    composable(
        AuthDestination.TERMS,
        arguments = listOf(navArgument("agreement") { type = NavType.StringType }),
    ) { entry ->
        val name = entry.savedStateHandle.get<String>("agreement")
        TermsRoute(
            termId = name.orEmpty(),
            onClose = { navController.popBackStack() },
        )
    }
    composable(AuthDestination.MIC) {
        val micPermission = getKoin().getOrNull<MicPermission>()
        val scope = rememberCoroutineScope()
        MicPermissionScreen(
            onAllow = {
                scope.launch {
                    if (micPermission?.request() != false) {
                        onMicAllowed()
                        toLogin(null)
                    } else {
                        toLogin(StartNotice.MicDenied)
                    }
                }
            },
            onDeny = { toLogin(StartNotice.MicDenied) },
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
            onSignUp = { navController.navigate(if (draft.agreements.isEmpty()) AuthDestination.CONSENT else AuthDestination.SIGNUP) },
        )
    }
    composable(
        AuthDestination.OAUTH,
        arguments = listOf(navArgument("provider") { type = NavType.StringType }),
    ) { entry ->
        val provider = entry.socialProvider() ?: SocialProvider.Kakao
        val draft = getKoin().get<AccountDraft>()
        OAuthRoute(
            provider = provider,
            onBack = { navController.popBackStack() },
            onLoggedIn = { onFinished(null, null) },
            onLinkAvailable = { fromLogin(AuthDestination.link(provider)) },
            onSignUp = { fromLogin(if (draft.agreements.isEmpty()) AuthDestination.CONSENT else AuthDestination.SIGNUP) },
        )
    }
    composable(
        AuthDestination.LINK,
        arguments = listOf(navArgument("provider") { type = NavType.StringType }),
    ) {
        AccountLinkRoute(
            onCancel = { navController.popBackStack() },
            onFindPassword = { fromLogin(AuthDestination.PASSWORD_RESET) },
            onLinked = { onFinished(StartNotice.AccountLinked, it) },
        )
    }
    composable(AuthDestination.SIGNUP) {
        EmailVerificationRoute(
            purpose = VerificationPurpose.SignUp,
            onBack = { navController.popBackStack() },
            onVerified = { navController.navigateWithVerifiedEmail(AuthDestination.SIGNUP_PASSWORD, it) },
        )
    }
    composable(AuthDestination.SIGNUP_PASSWORD) { entry ->
        PasswordSetupRoute(
            purpose = VerificationPurpose.SignUp,
            email = entry.verifiedEmail(),
            onBack = { navController.popBackStack() },
            onDone = { navController.navigate(AuthDestination.PROFILE) },
        )
    }
    composable(AuthDestination.PROFILE) {
        ProfileSetupRoute(
            onBack = { navController.popBackStack() },
            onDone = { onFinished(StartNotice.Welcome, null) },
        )
    }
    composable(AuthDestination.PASSWORD_RESET) {
        EmailVerificationRoute(
            purpose = VerificationPurpose.PasswordReset,
            onBack = { navController.popBackStack() },
            onVerified = { navController.navigateWithVerifiedEmail(AuthDestination.PASSWORD_RESET_NEW, it) },
        )
    }
    composable(AuthDestination.PASSWORD_RESET_NEW) { entry ->
        PasswordSetupRoute(
            purpose = VerificationPurpose.PasswordReset,
            email = entry.verifiedEmail(),
            onBack = { navController.popBackStack() },
            // 바꾼 뒤 자동 로그인하지 않고 새 로그인 화면에서 다시 로그인한다.
            onDone = {
                navController.navigateWithNotice(AuthDestination.LOGIN, StartNotice.PasswordChanged) {
                    popUpTo(AuthDestination.LOGIN) { inclusive = true }
                }
            },
        )
    }
}

private fun NavBackStackEntry.socialProvider(): SocialProvider? =
    savedStateHandle.get<String>("provider")?.let { name -> SocialProvider.entries.firstOrNull { it.name == name } }

/**
 * 간편 로그인을 마친 브라우저가 복귀 주소(App Links)로 앱을 열면, 그 시도를 시작한 화면(AUTH-05·MY-05)이 주소를 받도록 그 화면으로 간다.
 * 이미 그 화면에 있으면 화면이 직접 꺼낸다. 진행 중 시도가 없으면(만료·다른 기기에서 연 주소 등) 주소를 버린다.
 * 앱이 꺼진 사이 돌아와도 시도 상태는 기기에 남아 있어 같은 화면으로 이어 간다.
 */
@Composable
fun OAuthReturnEffect(navController: NavController) {
    val links = getKoin().getOrNull<IncomingLinks>() ?: return
    val attempts = getKoin().get<OAuthAttempts>()
    val pending by links.pending.collectAsStateWithLifecycle()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(pending, route) {
        val uri = pending ?: return@LaunchedEffect
        // 화면 이동 그래프가 아직 없거나, 복귀를 받을 화면이 이미 떠 있다.
        if (route == null || route == AuthDestination.OAUTH || route == ProfileDestination.LINK) return@LaunchedEffect
        val target = attempts.pending()
        if (target == null) {
            links.take(uri)
            return@LaunchedEffect
        }
        navController.navigate(
            when (target.purpose) {
                OAuthPurpose.LogIn -> AuthDestination.oauth(target.provider)
                OAuthPurpose.Link -> ProfileDestination.link(target.provider)
            },
        )
    }
}
