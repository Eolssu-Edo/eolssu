package com.ssafy.eolssu.feature.profile.component

import com.ssafy.eolssu.core.ui.component.QaModalObservation
import com.ssafy.eolssu.core.ui.component.qaModalInput

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.my_withdraw_confirm_image
import eolssu.shared.generated.resources.my_withdraw_confirm_message
import eolssu.shared.generated.resources.my_withdraw_confirm_title
import eolssu.shared.generated.resources.withdraw_jeolssu_crying
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * MY-04 탈퇴 확인 창. 우는 절쑤 그림 · 제목 · 30일 안내 · 세로로 쌓은 버튼(빨간 [confirmLabel] · '취소').
 * 확인 창(`EolssuDialog`)과 같은 흰 상자에 그림을 가운데 두려고 글자도 가운데 맞춘다. 바깥을 누르거나 뒤로 가면 취소한다.
 */
@Composable
fun WithdrawConfirmDialog(confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = EolssuTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        QaModalObservation()
        // 휴대폰 가로·큰 글꼴에서 높이가 모자라면 그림·안내만 스크롤하고 버튼은 아래에 고정한다.
        Column(
            Modifier.qaModalInput()
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 16.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .background(colors.surface, RoundedCornerShape(20.dp))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painterResource(Res.drawable.withdraw_jeolssu_crying),
                    contentDescription = stringResource(Res.string.my_withdraw_confirm_image),
                    modifier = Modifier.size(132.dp),
                )
                Text(
                    stringResource(Res.string.my_withdraw_confirm_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.7.sp),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(Res.string.my_withdraw_confirm_message),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = colors.textSub,
                    textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EolssuButton(confirmLabel, onClick = UserActionLog.click(QaTarget.WITHDRAW_CONFIRM_DIALOG_01, onConfirm), style = EolssuButtonStyle.Danger, qaTarget = QaTarget.WITHDRAW_CONFIRM_DIALOG_BUTTON_01)
                EolssuButton(stringResource(Res.string.common_cancel), onClick = UserActionLog.click(QaTarget.WITHDRAW_CONFIRM_DIALOG_02, onDismiss), style = EolssuButtonStyle.Text, qaTarget = QaTarget.WITHDRAW_CONFIRM_DIALOG_BUTTON_02)
            }
        }
    }
}
