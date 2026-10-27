package com.ssafy.eolssu.app.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.feature.auth.Agreement
import com.ssafy.eolssu.feature.auth.ConsentRoute
import com.ssafy.eolssu.feature.auth.LoginRoute
import com.ssafy.eolssu.feature.auth.MicPermissionScreen
import com.ssafy.eolssu.feature.auth.OAuthScreen
import com.ssafy.eolssu.feature.auth.ProfileSetupRoute
import com.ssafy.eolssu.feature.auth.SignupRoute
import com.ssafy.eolssu.feature.auth.SocialProvider
import com.ssafy.eolssu.feature.auth.TermsScreen
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_mic_denied_notice
import eolssu.shared.generated.resources.auth_welcome
import org.jetbrains.compose.resources.StringResource

/** 첫 실행·로그인·회원가입 화면 경로. */
object AuthDestination {
    const val CONSENT = "auth/consent"
    const val TERMS = "auth/terms/{agreement}"
    const val MIC = "auth/mic"
    const val LOGIN = "auth/login"
    const val OAUTH = "auth/oauth/{provider}"
    const val SIGNUP = "auth/signup"
    const val PROFILE = "auth/profile?provider={provider}"

    fun terms(agreement: Agreement) = "auth/terms/${agreement.name}"

    fun oauth(provider: SocialProvider) = "auth/oauth/${provider.name}"

    /** [provider]가 없으면 이메일 가입 2/2, 있으면 소셜 첫 가입. */
    fun profile(provider: SocialProvider?) = if (provider == null) "auth/profile" else "auth/profile?provider=${provider.name}"
}

/** 첫 실행을 마쳤으면 홈, 아니면 약관 동의에서 시작한다. */
fun startDestination(firstRunCompleted: Boolean): String =
    if (firstRunCompleted) EolssuDestination.HOME else AuthDestination.CONSENT

/** 다음 화면에 들어가면 한 번 띄울 안내. 이동한 화면의 저장 상태에 이름으로 남겨 화면을 돌려도 유지된다. */
enum class StartNotice(val message: StringResource) {
    MicDenied(Res.string.auth_mic_denied_notice),
    Welcome(Res.string.auth_welcome),
}

private const val START_NOTICE_KEY = "start_notice"

/** [route]로 이동하고, 새 화면에 [notice]를 남긴다. */
fun NavController.navigateWithNotice(route: String, notice: StartNotice?, builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(route, builder)
    if (notice != null) currentBackStackEntry?.savedStateHandle?.set(START_NOTICE_KEY, notice.name)
}

/** 이 화면에 남은 안내. 띄운 뒤에는 [clearStartNotice]로 지운다. */
fun NavBackStackEntry.startNotice(): StartNotice? =
    savedStateHandle.get<String>(START_NOTICE_KEY)?.let { name -> StartNotice.entries.firstOrNull { it.name == name } }

fun NavBackStackEntry.clearStartNotice() {
    savedStateHandle.remove<String>(START_NOTICE_KEY)
}

/**
 * 첫 실행을 마쳤다고 기기에 남기고 홈으로 간다. 뒤로 가기로 로그인·가입 화면에 돌아오지 않도록 쌓인 화면을 모두 비운다.
 * 가입을 마치고 왔으면 [welcome]으로 홈에서 환영 안내를 띄운다.
 */
fun NavController.finishFirstRun(firstRunRepository: FirstRunRepository, welcome: Boolean) {
    firstRunRepository.markCompleted()
    navigateWithNotice(EolssuDestination.HOME, if (welcome) StartNotice.Welcome else null) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/**
 * AUTH-01~07 화면 이동. 약관 동의 → 마이크 안내 → 로그인 → (소셜 첫 가입 · 이메일 가입) → 프로필 설정 → 홈.
 * 로그인 화면에 들어가면 약관 동의·마이크 안내는 비워 뒤로 가기로 돌아가지 않는다.
 */
fun NavGraphBuilder.authGraph(navController: NavController, onFinished: (welcome: Boolean) -> Unit) {
    val toLogin: (StartNotice?) -> Unit = { notice ->
        navController.navigateWithNotice(AuthDestination.LOGIN, notice) {
            popUpTo(AuthDestination.CONSENT) { inclusive = true }
        }
    }
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
        TermsScreen(
            agreement = Agreement.entries.firstOrNull { it.name == name && it.hasTerms } ?: Agreement.Terms,
            onClose = { navController.popBackStack() },
        )
    }
    composable(AuthDestination.MIC) {
        MicPermissionScreen(onAllow = { toLogin(null) }, onDeny = { toLogin(StartNotice.MicDenied) })
    }
    composable(AuthDestination.LOGIN) { entry ->
        LoginRoute(
            startMessage = entry.startNotice()?.message,
            onStartMessageShown = entry::clearStartNotice,
            onLoggedIn = { onFinished(false) },
            onSocialLogIn = { navController.navigate(AuthDestination.oauth(it)) },
            onSignUp = { navController.navigate(AuthDestination.SIGNUP) },
        )
    }
    composable(
        AuthDestination.OAUTH,
        arguments = listOf(navArgument("provider") { type = NavType.StringType }),
    ) { entry ->
        val provider = entry.socialProvider() ?: SocialProvider.Kakao
        OAuthScreen(
            provider = provider,
            onBack = { navController.popBackStack() },
            onNewAccount = { navController.navigate(AuthDestination.profile(provider)) },
            onExistingAccount = { onFinished(false) },
        )
    }
    composable(AuthDestination.SIGNUP) {
        SignupRoute(
            onBack = { navController.popBackStack() },
            onNext = { navController.navigate(AuthDestination.profile(null)) },
        )
    }
    composable(
        AuthDestination.PROFILE,
        arguments = listOf(
            navArgument("provider") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        ProfileSetupRoute(
            provider = entry.socialProvider(),
            onBack = { navController.popBackStack() },
            onDone = { onFinished(true) },
        )
    }
}

private fun NavBackStackEntry.socialProvider(): SocialProvider? =
    savedStateHandle.get<String>("provider")?.let { name -> SocialProvider.entries.firstOrNull { it.name == name } }
