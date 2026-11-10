package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 입력칸 종류. 키보드와 가림 표시가 달라진다. */
enum class TextFieldType { Text, Email, Password, Number }

/**
 * 이름표가 붙은 입력칸(`.au-field`): 이름표 13sp · 칸 높이 48dp · 아래 도움말 또는 오류.
 * [error]가 있으면 테두리를 굵은 검정으로 바꾸고 도움말 대신 오류를 보인다. [trailing]은 칸 오른쪽 끝 글자(인증번호 남은 시간)다.
 * 로그인·가입과 마이페이지가 함께 쓴다. 칸 높이는 [fieldHeight](기본 52dp)로 줄일 수 있고, 글꼴을 키우면 내용에 맞게 높아진다.
 */
@Composable
fun EolssuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    type: TextFieldType = TextFieldType.Text,
    help: String? = null,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    trailing: String? = null,
    fieldHeight: Dp = 52.dp,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = colors.text)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.text),
            visualTransformation = if (type == TextFieldType.Password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (type) {
                    TextFieldType.Text -> KeyboardType.Text
                    TextFieldType.Email -> KeyboardType.Email
                    TextFieldType.Password -> KeyboardType.Password
                    TextFieldType.Number -> KeyboardType.NumberPassword
                },
                autoCorrectEnabled = type == TextFieldType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = {
                if (onImeAction != null) onImeAction() else defaultKeyboardAction(imeAction)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = fieldHeight)
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = fieldHeight)
                        .background(colors.surface, shape)
                        .border(if (error != null) 1.5.dp else 1.dp, if (error != null) colors.rec else colors.line, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.padding(end = if (trailing != null) 48.dp else 0.dp)) {
                        // 자리 글자는 대비 기준(4.5:1)을 지키도록 회색 글자색을 쓴다(와이어프레임은 더 옅은 회색).
                        if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.textMuted)
                        innerTextField()
                    }
                    if (trailing != null) {
                        Text(
                            trailing,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                        )
                    }
                }
            },
        )
        when {
            error != null -> Row(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EolssuIcons.Info, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.text)
                Text(error, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
            }
            help != null -> Text(help, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = colors.textMuted)
        }
    }
}
