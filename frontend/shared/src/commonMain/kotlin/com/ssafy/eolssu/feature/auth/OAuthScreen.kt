package com.ssafy.eolssu.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.auth.component.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 제공자 토큰/API 연결 전에는 가짜 로그인 결과로 이동하지 않는다. */
@Composable
fun OAuthScreen(provider: SocialProvider, onBack: () -> Unit,
                widthClass: WindowWidthClass = currentWindowLayout().widthClass) {
    AuthScaffold(authFrameOf(widthClass, split = false),
        topBar = { AuthTopBar(stringResource(Res.string.auth_oauth_title, stringResource(provider.displayName)), onBack) },
        bottomBar = { EolssuButton(stringResource(Res.string.common_confirm), onBack) },
    ) { AuthBody(AuthBodyTopWithBar) { Text(stringResource(Res.string.member_api_social_unavailable)) } }
}
