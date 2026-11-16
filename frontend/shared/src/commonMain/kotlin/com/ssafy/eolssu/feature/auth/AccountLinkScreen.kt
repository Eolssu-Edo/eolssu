package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuTextLink
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithBar
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.feature.auth.component.AuthHeading
import com.ssafy.eolssu.feature.auth.component.AuthLead
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_find_password
import eolssu.shared.generated.resources.auth_link_account_kind
import eolssu.shared.generated.resources.auth_link_cancel
import eolssu.shared.generated.resources.auth_link_confirm
import eolssu.shared.generated.resources.auth_link_heading
import eolssu.shared.generated.resources.auth_link_lead
import eolssu.shared.generated.resources.auth_link_password
import eolssu.shared.generated.resources.auth_link_password_help
import eolssu.shared.generated.resources.auth_link_title
import eolssu.shared.generated.resources.auth_password_placeholder
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.social_auth_merge_expired
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-09 계정 연결 확인. 간편 로그인에 연결된 계정은 없지만 같은 이메일로 가입한 계정이 있을 때 연다.
 * 제공자가 이메일을 확인해 줬으면 동의만 받고, 아니면([AccountLinkUiState.needsPassword], 네이버는 항상) 그 계정의 비밀번호를 한 번 받는다.
 * 이메일은 서버가 가린 형태(`maskedEmail`)로 보여 준다. 연결 시간이 지났으면 안내 창의 확인으로 [onBack]한다.
 */
@Composable
fun AccountLinkScreen(
    state: AccountLinkUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onFindPassword: () -> Unit,
    onLink: () -> Unit,
) {
    val name = stringResource(state.provider.displayName)
    AuthScaffold(
        authFrameOf(widthClass, split = true),
        topBar = { AuthTopBar(stringResource(Res.string.auth_link_title), onBack = onBack) },
        bottomBar = {
            EolssuButton(stringResource(Res.string.auth_link_confirm), onClick = onLink, busy = state.linking)
            EolssuButton(stringResource(Res.string.auth_link_cancel), onClick = onBack, style = EolssuButtonStyle.Text)
        },
    ) {
        AuthBody(topPadding = AuthBodyTopWithBar) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthHeading(stringResource(Res.string.auth_link_heading))
                AuthLead(stringResource(Res.string.auth_link_lead, name))
            }
            AccountCard(state.provider, state.email)
            state.error?.let {
                Text(stringResource(it.message), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = EolssuTheme.colors.recText)
            }
            if (state.needsPassword) {
                Column {
                    EolssuTextField(
                        bordered = false,
                        labelInside = true,
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(Res.string.auth_link_password),
                        placeholder = stringResource(Res.string.auth_password_placeholder),
                        type = TextFieldType.Password,
                        help = stringResource(Res.string.auth_link_password_help),
                        error = state.passwordError?.let { stringResource(it.message) },
                        imeAction = ImeAction.Done,
                        onImeAction = onLink,
                    )
                    // 알약 안쪽 여백만큼 왼쪽으로 당겨 글자를 위 입력칸의 시작선에 맞춘다(S15P31E102-968).
                    EolssuTextLink(onFindPassword, Modifier.offset(x = (-12).dp)) {
                        Text(
                            stringResource(Res.string.auth_find_password),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = EolssuTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
    if (state.expired) {
        EolssuDialog(title = stringResource(Res.string.social_auth_merge_expired), icon = EolssuIcons.Info) {
            EolssuButton(stringResource(Res.string.common_confirm), onClick = onBack)
        }
    }
}

/** 연결할 얼쑤 계정(`.au-account`): 제공자 표시 · 이메일 · '이메일로 가입한 얼쑤 계정'. */
@Composable
private fun AccountCard(provider: SocialProvider, email: String) {
    val colors = EolssuTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        // 회색 바탕 위 테두리 없는 흰 카드(S15P31E102-798).
        color = colors.surface,
        contentColor = colors.text,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).background(colors.surfaceStrong, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text(provider.mark, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowOnTapText(email, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
                Text(
                    stringResource(Res.string.auth_link_account_kind),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.textMuted,
                )
            }
        }
    }
}

/**
 * 한 줄로 두고 넘치면 말줄임한다. 잘린 글자를 누르면 왼쪽으로 한 번 흘러 전체를 보여 준 뒤 말줄임으로 돌아온다(S15P31E102-972).
 * 흐르는 시간은 글자 폭과 [basicMarquee] 기본 속도(초당 30dp)·간격(칸 너비의 1/3)으로 셈한다.
 */
@Composable
private fun FlowOnTapText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val velocity = with(LocalDensity.current) { 30.dp.toPx() }
    var cut by remember(text) { mutableStateOf(false) }
    var flowMillis by remember(text) { mutableLongStateOf(0L) }
    var flowing by remember(text) { mutableStateOf(false) }
    LaunchedEffect(flowing) {
        if (flowing) {
            delay(flowMillis)
            flowing = false
        }
    }
    Text(
        text,
        modifier = modifier
            .clickable(enabled = cut && !flowing, interactionSource = null, indication = null) { flowing = true }
            .then(if (flowing) Modifier.basicMarquee(iterations = 1, initialDelayMillis = 0) else Modifier),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (flowing) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (!flowing) {
                cut = layout.hasVisualOverflow
                val travel = layout.multiParagraph.intrinsics.maxIntrinsicWidth + layout.size.width / 3f
                flowMillis = (travel / velocity * 1000).toLong()
            }
        },
    )
}
