package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_terms_clause_1
import eolssu.shared.generated.resources.auth_terms_clause_2
import eolssu.shared.generated.resources.auth_terms_clause_3
import eolssu.shared.generated.resources.auth_terms_clause_4
import eolssu.shared.generated.resources.auth_terms_clause_5
import eolssu.shared.generated.resources.auth_terms_effective_date
import eolssu.shared.generated.resources.auth_terms_pending
import eolssu.shared.generated.resources.common_confirm
import org.jetbrains.compose.resources.stringResource

private val clauses = listOf(
    Res.string.auth_terms_clause_1,
    Res.string.auth_terms_clause_2,
    Res.string.auth_terms_clause_3,
    Res.string.auth_terms_clause_4,
    Res.string.auth_terms_clause_5,
)

/**
 * AUTH-02 약관 보기. 본문은 서버 약관 목록(`GET /api/terms`)의 `contentUrl`로 보여 줄 예정이라 지금은 와이어프레임처럼 조항 제목과 본문 자리만 보인다.
 * 뒤로 가기와 확인 모두 [onClose]로 약관 동의 화면에 돌아간다.
 */
@Composable
fun TermsScreen(agreement: Agreement, onClose: () -> Unit, widthClass: WindowWidthClass = currentWindowLayout().widthClass) {
    val colors = EolssuTheme.colors
    val helpStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp)
    AuthScaffold(
        authFrameOf(widthClass, split = false),
        topBar = { AuthTopBar(stringResource(agreement.title), onBack = onClose) },
        bottomBar = { EolssuButton(stringResource(Res.string.common_confirm), onClick = onClose) },
    ) {
        AuthBody(topPadding = 8.dp, gap = 18.dp) {
            Text(stringResource(Res.string.auth_terms_effective_date), style = helpStyle, color = colors.textMuted)
            clauses.forEach { clause ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(clause), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    ClauseLines()
                }
            }
            Text(stringResource(Res.string.auth_terms_pending), style = helpStyle, color = colors.textMuted)
        }
    }
}

/** 조항 본문 자리(`.au-lines`): 회색 줄 세 개, 마지막 줄은 70%. */
@Composable
private fun ClauseLines() {
    val shape = RoundedCornerShape(5.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1f, 1f, 0.7f).forEach { fraction ->
            Box(Modifier.fillMaxWidth(fraction).height(10.dp).background(EolssuTheme.colors.surfaceStrong, shape))
        }
    }
}
