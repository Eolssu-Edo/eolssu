package com.ssafy.eolssu.app.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.feature.profile.DataSourcesRoute
import com.ssafy.eolssu.feature.profile.LegalDocumentRoute
import com.ssafy.eolssu.feature.profile.MyPageRoute
import com.ssafy.eolssu.feature.profile.MyPageTarget
import com.ssafy.eolssu.feature.profile.NicknameEditRoute
import com.ssafy.eolssu.feature.profile.PasswordChangeRoute
import com.ssafy.eolssu.feature.profile.ProfileImageRoute
import com.ssafy.eolssu.feature.profile.SocialLinkRoute
import com.ssafy.eolssu.feature.profile.WithdrawRoute

/** 마이페이지 화면 경로(MY-01~05). */
object ProfileDestination {
    const val MY_PAGE = "my"
    const val NICKNAME = "my/nickname"
    const val PROFILE_IMAGE = "my/profile-image"
    const val PASSWORD = "my/password"
    const val WITHDRAW = "my/withdraw"
    const val SOURCES = "my/sources"
    const val LINK = "my/link/{provider}"
    const val LEGAL = "my/legal/{document}"

    fun link(provider: SocialProvider) = "my/link/${provider.name}"
    fun legal(document: PublishedLegalDocument) = "my/legal/${document.name}"
}

/**
 * MY-01~05 화면 이동. 홈 오른쪽 위 아이콘 → 마이페이지. 닉네임·비밀번호를 바꾸거나 간편 로그인을 연결하면 마이페이지로 돌아와 안내를 띄운다.
 * 로그아웃·탈퇴 신청은 쌓인 화면을 모두 비우고 로그인 화면(AUTH-04)으로 간다.
 * 첫 실행을 마친 기기는 앱을 다시 켜면 홈에서 시작하며, 회원 요청은 서버 쿠키로 인증한다.
 */
fun NavGraphBuilder.profileGraph(navController: NavController) {
    val toLogin: (StartNotice) -> Unit = { notice ->
        navController.navigateWithNotice(AuthDestination.LOGIN, notice) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
    composable(ProfileDestination.MY_PAGE) { entry ->
        MyPageRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onOpen = { target ->
                navController.navigate(
                    when (target) {
                        MyPageTarget.Nickname -> ProfileDestination.NICKNAME
                        MyPageTarget.ProfileImage -> ProfileDestination.PROFILE_IMAGE
                        MyPageTarget.Password -> ProfileDestination.PASSWORD
                        MyPageTarget.Withdraw -> ProfileDestination.WITHDRAW
                        MyPageTarget.Sources -> ProfileDestination.SOURCES
                        MyPageTarget.Records -> EolssuDestination.records()
                    },
                )
            },
            onLink = { navController.navigate(ProfileDestination.link(it)) },
            onOpenLegal = { navController.navigate(ProfileDestination.legal(it)) },
            onLoggedOut = { toLogin(StartNotice.LoggedOut) },
            onSessionExpired = { toLogin(StartNotice.SessionExpired) },
            startMessage = entry.startNotice(),
            onStartMessageShown = entry::clearStartNotice,
            providers = socialProviders(),
        )
    }
    composable(ProfileDestination.NICKNAME) { entry ->
        NicknameEditRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onSaved = { navController.popWithNotice(StartNotice.NicknameChanged) },
        )
    }
    composable(ProfileDestination.PROFILE_IMAGE) { entry ->
        ProfileImageRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onSaved = { navController.popWithNotice(StartNotice.ProfileImageChanged) },
        )
    }
    composable(ProfileDestination.PASSWORD) { entry ->
        PasswordChangeRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onChanged = { navController.popWithNotice(StartNotice.MyPasswordChanged) },
        )
    }
    composable(ProfileDestination.SOURCES) { entry ->
        DataSourcesRoute(onBack = { navController.popBackStackFrom(entry) })
    }
    composable(
        ProfileDestination.LEGAL,
        arguments = listOf(navArgument("document") { type = NavType.StringType }),
    ) { entry ->
        LegalDocumentRoute(entry.legalDocument(), onBack = { navController.popBackStackFrom(entry) })
    }
    composable(ProfileDestination.WITHDRAW) { entry ->
        WithdrawRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onWithdrawn = { toLogin(StartNotice.Withdrawn) },
        )
    }
    composable(
        ProfileDestination.LINK,
        arguments = listOf(navArgument("provider") { type = NavType.StringType }),
    ) { entry ->
        val provider = entry.linkProvider()
        SocialLinkRoute(
            provider = provider,
            onBack = { navController.popBackStackFrom(entry) },
            onLinked = { navController.popWithNotice(StartNotice.MyAccountLinked, provider) },
        )
    }
}

private fun NavBackStackEntry.legalDocument(): PublishedLegalDocument =
    savedStateHandle.get<String>("document")?.let { name -> PublishedLegalDocument.entries.firstOrNull { it.name == name } } ?: PublishedLegalDocument.Service

private fun NavBackStackEntry.linkProvider(): SocialProvider =
    savedStateHandle.get<String>("provider")?.let { name -> SocialProvider.entries.firstOrNull { it.name == name } } ?: SocialProvider.Kakao
