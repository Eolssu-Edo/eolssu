package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.NicknameRule
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.InstrumentPicker
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthHeading
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.feature.auth.component.SignupTopBar
import com.ssafy.eolssu.feature.auth.component.authBodyTopBelowProgress
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_instrument
import eolssu.shared.generated.resources.auth_instrument_help
import eolssu.shared.generated.resources.auth_nickname
import eolssu.shared.generated.resources.member_api_nickname_rule
import eolssu.shared.generated.resources.auth_nickname_placeholder
import eolssu.shared.generated.resources.auth_profile_heading
import eolssu.shared.generated.resources.auth_signup_done
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-07 프로필 설정. 이메일 가입은 4/4 단계, 간편 로그인 가입([social])은 이메일 인증·비밀번호 단계가 없어 2/2 단계로 같은 입력을 받는다.
 * 소셜 가입 화면은 와이어프레임에 따로 없어서 이 보드를 그대로 쓴다.
 */
@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onSelectInstrument: (Instrument) -> Unit,
    onComplete: () -> Unit,
    social: Boolean = false,
) {
    AuthScaffold(
        authFrameOf(widthClass, split = true),
        topBar = {
            SignupTopBar(SignupProgress(if (social) SignupPath.Social else SignupPath.Email, SignupStep.Profile), onBack = onBack)
        },
        bottomBar = { EolssuButton(stringResource(Res.string.auth_signup_done), onClick = onComplete) },
    ) {
        AuthBody(topPadding = authBodyTopBelowProgress()) {
            AuthHeading(stringResource(Res.string.auth_profile_heading))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EolssuTextField(
                    bordered = false,
                    labelInside = true,
                    value = state.nickname,
                    onValueChange = onNicknameChange,
                    label = stringResource(Res.string.auth_nickname),
                    placeholder = stringResource(Res.string.auth_nickname_placeholder),
                    help = stringResource(Res.string.member_api_nickname_rule),
                    error = state.nicknameError?.let { stringResource(it.message) },
                    imeAction = ImeAction.Done,
                    counter = "${state.nickname.length}/${NicknameRule.MAX_LENGTH}",
                )
                InstrumentPicker(
                    selected = state.instrument,
                    onSelect = onSelectInstrument,
                    label = stringResource(Res.string.auth_instrument),
                    help = stringResource(Res.string.auth_instrument_help),
                    instruments = state.instruments,
                    // 회색 바탕 화면이라 고르지 않은 칸은 흰 채움(S15P31E102-798).
                    unselectedColor = EolssuTheme.colors.surface,
                )
            }
        }
    }
}
