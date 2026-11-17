package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.data.user.TermsBlock
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithBar
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.member_api_retry
import eolssu.shared.generated.resources.member_api_terms_loading
import eolssu.shared.generated.resources.member_api_terms_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * 약관 상세(AUTH-02, 보드 `.au-terms`). 상단 바에 약관 이름, 본문에 서버 약관 문서를 바로 펼친다.
 * 확인·뒤로 가기는 약관 동의 화면으로 돌아가며, 동의 화면의 체크 상태는 그대로 남는다.
 */
@Composable
fun TermsScreen(state: TermsUiState, onClose: () -> Unit, onRetry: () -> Unit, widthClass: WindowWidthClass) {
    AuthScaffold(authFrameOf(widthClass, split = false),
        topBar = { AuthTopBar(state.term?.title.orEmpty(), onBack = onClose) },
        bottomBar = { EolssuButton(stringResource(Res.string.common_confirm), onClose, qaTarget = QaTarget.TERMS_SCREEN_BUTTON_01) },
    ) {
        AuthBody(AuthBodyTopWithBar, gap = 12.dp) {
            val body = state.body
            when {
                state.loading -> Text(stringResource(Res.string.member_api_terms_loading))
                body == null -> {
                    Text(stringResource(Res.string.member_api_terms_unavailable))
                    EolssuButton(stringResource(Res.string.member_api_retry), onRetry, qaTarget = QaTarget.TERMS_SCREEN_BUTTON_02)
                }
                else -> body.forEach { TermsBlockText(it) }
            }
        }
    }
}

@Composable
private fun TermsBlockText(block: TermsBlock) {
    val colors = EolssuTheme.colors
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 23.8.sp)
    when (block) {
        is TermsBlock.Heading -> Text(
            block.text,
            modifier = Modifier.padding(top = if (block.level <= 1) 0.dp else 8.dp).semantics { heading() },
            style = if (block.level <= 1) {
                MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
            },
            color = colors.text,
        )
        is TermsBlock.Paragraph -> Text(block.text, style = bodyStyle, color = colors.textSub)
        is TermsBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("•", style = bodyStyle, color = colors.textSub)
            Column { Text(block.text, style = bodyStyle, color = colors.textSub) }
        }
    }
}
