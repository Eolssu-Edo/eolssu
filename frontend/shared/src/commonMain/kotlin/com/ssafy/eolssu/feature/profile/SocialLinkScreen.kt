package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.ProviderWaiting
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.member_api_retry
import eolssu.shared.generated.resources.my_link_taken_message
import eolssu.shared.generated.resources.my_link_taken_title
import eolssu.shared.generated.resources.my_link_title
import eolssu.shared.generated.resources.social_auth_progress
import eolssu.shared.generated.resources.social_auth_reopen
import eolssu.shared.generated.resources.social_auth_waiting_body
import eolssu.shared.generated.resources.social_auth_waiting_title
import org.jetbrains.compose.resources.stringResource

/**
 * MY-05 간편 로그인 연결. 제공자 화면은 외부 브라우저에 뜨고, 이 화면은 그동안 제공자 표시와 진행 상태([ProviderWaiting])를 보여 준다.
 * 이미 다른 얼쑤 계정에 연결된 제공자 계정이면(409 `SOCIAL_ACCOUNT_IN_USE`) 안내 창을 띄운 뒤 [onBack]으로 돌아간다.
 */
@Composable
fun SocialLinkScreen(
    provider: SocialProvider,
    step: SocialLinkStep,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReopen: () -> Unit,
) {
    val name = stringResource(provider.displayName)
    val (title, body) = when (step) {
        is SocialLinkStep.Stopped -> null to stringResource(step.problem.message, name)
        SocialLinkStep.Starting, SocialLinkStep.Completing -> null to stringResource(Res.string.social_auth_progress)
        else -> stringResource(Res.string.social_auth_waiting_title, name) to stringResource(Res.string.social_auth_waiting_body)
    }
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_link_title, name),
        onBack,
        bottomBar = {
            when (step) {
                SocialLinkStep.InBrowser -> EolssuButton(stringResource(Res.string.social_auth_reopen, name), onClick = onReopen)
                is SocialLinkStep.Stopped -> if (step.problem.retryable) {
                    EolssuButton(stringResource(Res.string.member_api_retry), onClick = onRetry)
                } else {
                    EolssuButton(stringResource(Res.string.common_confirm), onClick = onBack)
                }
                SocialLinkStep.Starting, SocialLinkStep.Completing ->
                    EolssuButton(stringResource(Res.string.social_auth_progress), onClick = {}, busy = true)
                else -> Unit
            }
        },
    ) {
        // 본문 칸이 스크롤이라 높이를 정해 가운데에 둔다: 휴대폰 480dp, 태블릿 세로 640dp · 가로 520dp.
        ProviderWaiting(
            provider,
            title,
            body,
            busy = step !is SocialLinkStep.Stopped,
            modifier = Modifier.heightIn(
                min = when (widthClass) {
                    WindowWidthClass.Compact -> 480.dp
                    WindowWidthClass.Medium -> 640.dp
                    WindowWidthClass.Expanded -> 520.dp
                },
            ),
        )
    }
    if (step == SocialLinkStep.InUse) {
        EolssuDialog(
            title = stringResource(Res.string.my_link_taken_title, name),
            message = stringResource(Res.string.my_link_taken_message, name),
            icon = EolssuIcons.Info,
        ) {
            EolssuButton(stringResource(Res.string.common_confirm), onClick = onBack)
        }
    }
}
