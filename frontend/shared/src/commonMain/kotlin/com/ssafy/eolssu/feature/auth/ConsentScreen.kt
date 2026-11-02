package com.ssafy.eolssu.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ssafy.eolssu.data.user.AccountTerm
import eolssu.shared.generated.resources.member_api_terms_unavailable
import eolssu.shared.generated.resources.member_api_terms_loading
import eolssu.shared.generated.resources.member_api_retry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthCheckBox
import com.ssafy.eolssu.feature.auth.component.AuthHeading
import com.ssafy.eolssu.feature.auth.component.AuthLead
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.authBodyTopWithoutBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_agree_all
import eolssu.shared.generated.resources.auth_consent_lead
import eolssu.shared.generated.resources.auth_consent_title
import eolssu.shared.generated.resources.auth_optional
import eolssu.shared.generated.resources.auth_required
import eolssu.shared.generated.resources.auth_start
import eolssu.shared.generated.resources.auth_start_disabled
import eolssu.shared.generated.resources.auth_view_terms
import eolssu.shared.generated.resources.auth_view_terms_description
import org.jetbrains.compose.resources.stringResource

/**
 * AUTH-01 약관 동의(첫 실행). 상단 바 없이 제목은 위, 동의 목록은 본문 아래쪽에 붙는다.
 * 태블릿은 제목과 동의 목록을 한 덩어리로 화면 가운데에 두고, 가로에서는 왼쪽에 브랜드 칸을 둔다.
 * 서버가 요구한 약관을 모두 명시적으로 선택해야 시작 버튼이 켜진다.
 */
@Composable
fun ConsentScreen(
    state: ConsentUiState,
    widthClass: WindowWidthClass,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onOpenTerms: (String) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
) {
    val frame = authFrameOf(widthClass, split = true)
    AuthScaffold(
        frame,
        bottomBar = {
            EolssuButton(
                stringResource(if (state.canStart) Res.string.auth_start else Res.string.auth_start_disabled),
                onClick = onStart,
                enabled = state.canStart,
            )
        },
    ) {
        AuthBody(
            topPadding = authBodyTopWithoutBar(),
            gap = 0.dp,
            verticalAlignment = if (frame.isTablet) Alignment.CenterVertically else Alignment.Top,
        ) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthHeading(stringResource(Res.string.auth_consent_title))
                AuthLead(stringResource(Res.string.auth_consent_lead))
            }
            Spacer(Modifier.height(20.dp))
            // 휴대폰은 동의 목록을 아래쪽에 붙이고, 태블릿은 제목 바로 아래 12dp에 둔다(`.tabapp.auth .au-agree`).
            if (frame.isTablet) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.loading) Text(stringResource(Res.string.member_api_terms_loading))
                else if (state.failed) {
                    Text(stringResource(Res.string.member_api_terms_unavailable))
                    EolssuButton(stringResource(Res.string.member_api_retry), onRetry)
                } else {
                    AgreeAllRow(state.allChecked, onToggleAll)
                    state.terms.forEach { term ->
                        AgreementRow(term, term.termId in state.checked, { onToggle(term.termId) }, { onOpenTerms(term.termId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgreeAllRow(checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(EolssuTheme.colors.surface)
            .border(1.dp, EolssuTheme.colors.lineSoft, RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AuthCheckBox(checked)
        Text(
            stringResource(Res.string.auth_agree_all),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = EolssuTheme.colors.text,
        )
    }
}

@Composable
private fun AgreementRow(agreement: AccountTerm, checked: Boolean, onToggle: () -> Unit, onOpenTerms: () -> Unit) {
    val colors = EolssuTheme.colors
    val prefix = stringResource(if (agreement.required) Res.string.auth_required else Res.string.auth_optional)
    val label = agreement.title
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AuthCheckBox(checked)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(
                            if (agreement.required) SpanStyle(fontWeight = FontWeight.Bold)
                            else SpanStyle(fontWeight = FontWeight.Medium, color = colors.textMuted),
                        ) { append(prefix) }
                        append(" ")
                        append(label)
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.75.sp),
                    color = colors.text,
                )

            }
        }
        if (agreement.contentUrl.isNotBlank()) {
            val description = stringResource(Res.string.auth_view_terms_description, label)
            Box(
                Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onOpenTerms)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                    }
                    .padding(start = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.auth_view_terms),
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline),
                    color = colors.textMuted,
                )
            }
        }
    }
}
