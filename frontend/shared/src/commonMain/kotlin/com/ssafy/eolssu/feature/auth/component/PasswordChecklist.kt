package com.ssafy.eolssu.feature.auth.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.PasswordCheck
import com.ssafy.eolssu.core.model.PasswordRule
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_password_check_digit
import eolssu.shared.generated.resources.auth_password_check_length
import eolssu.shared.generated.resources.auth_password_check_letter
import eolssu.shared.generated.resources.auth_password_check_met
import eolssu.shared.generated.resources.auth_password_check_special
import eolssu.shared.generated.resources.auth_password_check_unmet
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val PasswordCheck.label: StringResource
    get() = when (this) {
        PasswordCheck.Letter -> Res.string.auth_password_check_letter
        PasswordCheck.Digit -> Res.string.auth_password_check_digit
        PasswordCheck.Special -> Res.string.auth_password_check_special
        PasswordCheck.MinLength -> Res.string.auth_password_check_length
    }

/**
 * 비밀번호 조건 체크리스트(2열). 입력할 때마다 [PasswordRule.checks]로 다시 판정해 제출 검사와 기준이 같다.
 * 채운 조건은 연두 원 안의 체크, 아직인 조건은 빈 원으로 보여 색 없이도 구분된다.
 * [highlightUnmet]이면(조건을 덜 채운 채 제출) 아직인 조건을 입력칸 오류처럼 굵은 글자와 안내 아이콘으로 바꾼다.
 */
@Composable
fun PasswordChecklist(password: String, highlightUnmet: Boolean, modifier: Modifier = Modifier) {
    val met = PasswordRule.checks(password)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PasswordCheck.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { check ->
                    PasswordCheckItem(check, satisfied = check in met, highlight = highlightUnmet, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PasswordCheckItem(check: PasswordCheck, satisfied: Boolean, highlight: Boolean, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val label = stringResource(check.label)
    val state = stringResource(if (satisfied) Res.string.auth_password_check_met else Res.string.auth_password_check_unmet)
    val warn = highlight && !satisfied
    Row(
        modifier.clearAndSetSemantics { contentDescription = "$label, $state" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            satisfied -> Box(
                Modifier.size(16.dp).background(colors.accentTintStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(11.dp), tint = colors.text)
            }
            warn -> Icon(EolssuIcons.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.text)
            else -> Box(Modifier.size(16.dp).border(1.5.dp, colors.line, CircleShape))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                fontWeight = if (satisfied || warn) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (satisfied || warn) colors.text else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
