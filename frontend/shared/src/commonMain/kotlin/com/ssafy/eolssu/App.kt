package com.ssafy.eolssu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.ssafy.eolssu.app.di.appModule
import com.ssafy.eolssu.app.navigation.BrowserBackGuard
import com.ssafy.eolssu.app.navigation.EolssuNavHost
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.user.FirstRunRepository
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.core.ui.component.EolssuButton
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.member_api_unavailable
import eolssu.shared.generated.resources.member_api_retry
import eolssu.shared.generated.resources.member_api_profile_loading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

/**
 * 모든 플랫폼이 띄우는 공통 진입점. Koin을 시작하고 테마 안에서 화면 이동 그래프를 그린다.
 * [platformModule]은 플랫폼 구현 등록이다. 진입점은 플랫폼별 `App`(androidMain·webMain)이나 `MainViewController`를 부른다.
 * Web은 [onNavHostReady]로 브라우저 뒤로 가기·주소를 화면 이동에 연결하고, [browserBackGuard]로 연주 중 브라우저 뒤로 가기를 막는다.
 * 앱이 앞으로 올 때마다 보내지 못한 연습 시간 기록을 보낸다.
 */
@Composable
internal fun App(
    platformModule: Module,
    onNavHostReady: suspend (NavController) -> Unit = {},
    browserBackGuard: BrowserBackGuard? = null,
    // TEMP S15P31E102-309: Android QA only; all other callers retain false.
    qaSkipConsent: Boolean = false,
) {
    KoinApplication(configuration = koinConfiguration { modules(platformModule, appModule) }) {
        val practiceTimeQueue = koinInject<PracticeTimeQueue>()
        LifecycleEventEffect(Lifecycle.Event.ON_START) { practiceTimeQueue.onAppStart() }
        val firstRun = koinInject<FirstRunRepository>()
        val users = koinInject<UserRepository>()
        val restoreNeeded = remember { firstRun.isCompleted() }
        var restored by remember { mutableStateOf<MemberResult?>(if (restoreNeeded) null else MemberResult.Done) }
        var retry by remember { mutableStateOf(0) }
        // 화면별 재조회와 OAuth 브라우저 복귀가 아닌, 앱 생성 시 회원 설정을 한 번 복구한다.
        LaunchedEffect(users, retry) {
            if (restoreNeeded) {
                restored = users.refresh().also { if (it == MemberResult.Unauthenticated) users.logOut() }
            }
        }
        EolssuTheme {
            when (restored) {
                MemberResult.Done, MemberResult.Unauthenticated -> EolssuNavHost(
                    onNavHostReady = onNavHostReady, browserBackGuard = browserBackGuard, qaSkipConsent = qaSkipConsent,
                    sessionExpired = restored == MemberResult.Unauthenticated,
                )
                else -> Column(Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    if (restored == null) {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.member_api_profile_loading))
                    } else {
                        Text(stringResource(Res.string.member_api_unavailable))
                        EolssuButton(stringResource(Res.string.member_api_retry), onClick = { restored = null; retry++ })
                    }
                }
            }
        }
    }
}
