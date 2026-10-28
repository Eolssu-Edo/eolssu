package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthHeading
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthStepLabel
import com.ssafy.eolssu.feature.auth.component.AuthStepProgress
import com.ssafy.eolssu.feature.auth.component.AuthTextField
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authBodyTopBelowProgress
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_instrument
import eolssu.shared.generated.resources.auth_instrument_help
import eolssu.shared.generated.resources.auth_nickname
import eolssu.shared.generated.resources.auth_nickname_help
import eolssu.shared.generated.resources.auth_nickname_placeholder
import eolssu.shared.generated.resources.auth_profile_heading
import eolssu.shared.generated.resources.auth_signup_done
import eolssu.shared.generated.resources.auth_signup_title
import org.jetbrains.compose.resources.stringResource

/** AUTH-07 프로필 설정(이메일 가입 3/3). 간편 로그인은 로그인 전용이라 소셜 첫 가입용 화면은 없다. */
@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onSelectInstrument: (ProfileInstrument) -> Unit,
    onComplete: () -> Unit,
) {
    AuthScaffold(
        authFrameOf(widthClass, split = true),
        modifier = Modifier.imePadding(),
        topBar = {
            Column {
                AuthTopBar(stringResource(Res.string.auth_signup_title), onBack = onBack, right = { AuthStepLabel(3, SignupStepCount) })
                AuthStepProgress(3, SignupStepCount)
            }
        },
        bottomBar = { EolssuButton(stringResource(Res.string.auth_signup_done), onClick = onComplete) },
    ) {
        AuthBody(topPadding = authBodyTopBelowProgress()) {
            AuthHeading(stringResource(Res.string.auth_profile_heading))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AuthTextField(
                    value = state.nickname,
                    onValueChange = onNicknameChange,
                    label = stringResource(Res.string.auth_nickname),
                    placeholder = stringResource(Res.string.auth_nickname_placeholder),
                    help = stringResource(Res.string.auth_nickname_help),
                    error = state.nicknameError?.let { stringResource(it.message) },
                    imeAction = ImeAction.Done,
                )
                InstrumentPicker(state.instruments, state.instrument, onSelectInstrument)
            }
        }
    }
}

/** 악기 고르기(`.au-inst`): 세 칸 76dp, 고른 칸은 흰 바탕에 굵은 테두리. */
@Composable
private fun InstrumentPicker(
    instruments: List<ProfileInstrument>,
    selected: ProfileInstrument,
    onSelect: (ProfileInstrument) -> Unit,
) {
    val colors = EolssuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.auth_instrument),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
        )
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            instruments.forEach { instrument ->
                val isSelected = instrument == selected
                val shape = RoundedCornerShape(14.dp)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(76.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(instrument) }),
                    shape = shape,
                    color = if (isSelected) colors.background else colors.surface,
                    contentColor = colors.text,
                    border = if (isSelected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.line),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(instrument.label), style = EolssuTheme.serif.headline.copy(fontSize = 21.sp, lineHeight = 28.sp))
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.auth_instrument_help),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = colors.textMuted,
        )
    }
}
