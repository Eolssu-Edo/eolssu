package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.TextFieldType
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_password_current
import eolssu.shared.generated.resources.my_withdraw
import eolssu.shared.generated.resources.my_withdraw_done
import eolssu.shared.generated.resources.my_withdraw_heading
import eolssu.shared.generated.resources.my_withdraw_notice_1
import eolssu.shared.generated.resources.my_withdraw_notice_2
import eolssu.shared.generated.resources.my_withdraw_notice_3
import eolssu.shared.generated.resources.my_withdraw_password_help
import org.jetbrains.compose.resources.stringResource

/** MY-04 회원 탈퇴. 30일 안내를 보이고 현재 비밀번호로 본인인지 다시 확인한다. */
@Composable
fun WithdrawScreen(
    state: WithdrawUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = EolssuTheme.colors
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_withdraw),
        onBack,
        bottomBar = { EolssuButton(stringResource(Res.string.my_withdraw_done), onClick = onSubmit, style = EolssuButtonStyle.Danger) },
    ) {
        Text(
            stringResource(Res.string.my_withdraw_heading),
            style = EolssuTheme.serif.headline.copy(fontSize = if (widthClass == WindowWidthClass.Compact) 24.sp else 28.sp, lineHeight = 34.sp),
            color = colors.text,
        )
        // 안내 목록(`.my-notice`): 회색 바탕에 점 목록
        Column(
            Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(Res.string.my_withdraw_notice_1, Res.string.my_withdraw_notice_2, Res.string.my_withdraw_notice_3).forEach { notice ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Text(stringResource(notice), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp), color = colors.text)
                }
            }
        }
        EolssuTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(Res.string.my_password_current),
            placeholder = stringResource(Res.string.my_password_current),
            type = TextFieldType.Password,
            help = stringResource(Res.string.my_withdraw_password_help),
            error = state.error?.let { stringResource(it.message) },
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
    }
}
