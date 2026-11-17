package com.ssafy.eolssu.core.ui.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.diagnostics.QaKind

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import eolssu.shared.generated.resources.input_password_show
import eolssu.shared.generated.resources.input_password_hide
import eolssu.shared.generated.resources.input_limit_reached
import eolssu.shared.generated.resources.Res
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
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
import com.ssafy.eolssu.core.model.InputLimit
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 입력칸 종류. 키보드와 가림 표시가 달라진다. */
enum class TextFieldType { Text, Email, Password, Number }

/**
 * 이름표가 붙은 입력칸(`.au-field`): 이름표 13sp · 칸 높이 48dp · 아래 도움말 또는 오류.
 * [error]가 있으면 도움말 대신 오류를 보이고, 테두리 있는 칸([bordered])은 빨간 테두리를 두른다. [trailing]은 칸 오른쪽 끝 글자(인증번호 남은 시간)다.
 * 로그인·가입과 마이페이지가 함께 쓴다. 칸 높이는 [fieldHeight](기본 52dp)로 줄일 수 있고, 글꼴을 키우면 내용에 맞게 높아진다.
 * [counter](예: `4/10`)가 있으면 도움말·오류 줄 오른쪽 끝에 함께 보인다.
 * [maxLength]가 있으면 그 글자 수(코드 포인트)를 넘는 입력은 칸에 남기지 않고, 넘겨 치려다 막히면 칸 아래에 'n자까지 입력할 수 있어요'를 보인다
 * ([InputLimit.limit], S15P31E102-996). 비밀번호 칸은 오른쪽 눈 버튼으로 입력한 글자를 보이거나 다시 가린다.
 * [labelInside]면 칸 위 이름표 없이 이름을 자리 글자로만 두고, 입력하면 사라진다(로그인·가입, S15P31E102-798). 화면 읽기는 그대로 이름을 읽는다.
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
    counter: String? = null,
    bordered: Boolean = true,
    containerColor: Color? = null,
    labelInside: Boolean = false,
    maxLength: Int? = null,
    qaTarget: QaTarget = QaTarget.TEXT_FIELD,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val focusManager = LocalFocusManager.current
    val hideKeyboard = rememberHideKeyboard()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused != wasFocused) UserActionLog.event(if (focused) QaKind.INPUT_FOCUS else QaKind.INPUT_BLUR, qaTarget)
        wasFocused = focused
    }
    androidx.compose.runtime.LaunchedEffect(error != null) {
        if (error != null) UserActionLog.event(QaKind.INPUT_VALIDATION, qaTarget, com.ssafy.eolssu.core.diagnostics.QaOutcome.INVALID)
    }
    val password = type == TextFieldType.Password
    // 가려진 비밀번호도 무엇을 쳤는지 확인할 수 있게 한다. 화면을 다시 그려도 가린 상태로 돌아가지 않게 저장한다.
    var revealed by rememberSaveable { mutableStateOf(false) }
    // 상한을 넘겨 치려다 막혔을 때만 안내한다. 딱 맞게 채운 값(빠르기 120 등)에는 띄우지 않고, 값이 상한 아래로 줄면 지운다.
    var overflowed by remember { mutableStateOf(false) }
    val atLimit = maxLength != null && InputLimit.length(value) >= maxLength
    if (!atLimit && overflowed) overflowed = false
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!labelInside) Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = colors.text)
        BasicTextField(
            value = value,
            onValueChange = UserActionLog.change(qaTarget, { input ->
                if (maxLength == null) onValueChange(input)
                else {
                    overflowed = InputLimit.length(input) > maxLength
                    onValueChange(InputLimit.limit(value, input, maxLength))
                }
            }),
            singleLine = true,
            interactionSource = interaction,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.text),
            visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (type) {
                    TextFieldType.Text -> KeyboardType.Text
                    TextFieldType.Email -> KeyboardType.Email
                    TextFieldType.Password -> KeyboardType.Password
                    // BPM·인증번호 등 일반 숫자를 자격 증명으로 분류하지 않는다.
                    TextFieldType.Number -> KeyboardType.Number
                },
                autoCorrectEnabled = type == TextFieldType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = {
                UserActionLog.event(QaKind.INPUT_COMMIT, qaTarget)
                // '다음'은 다음 칸으로 옮기고, 옮길 칸이 없으면 키보드를 내린다.
                // 확인·완료 등은 키보드를 먼저 내린 뒤 [onImeAction](로그인·저장 등)을 한다(S15P31E102-800).
                when {
                    imeAction == ImeAction.Next && onImeAction != null -> onImeAction()
                    imeAction == ImeAction.Next -> if (!focusManager.moveFocus(FocusDirection.Next)) hideKeyboard()
                    imeAction == ImeAction.Previous -> if (onImeAction != null) onImeAction() else focusManager.moveFocus(FocusDirection.Previous)
                    else -> {
                        hideKeyboard()
                        onImeAction?.invoke()
                    }
                }
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
                        .background(containerColor ?: colors.surface, shape)
                        // [bordered]가 false면 오류일 때도 테두리 없이 같은 바탕색 칸을 두고, 오류는 아래 문장으로만 알린다(S15P31E102-798).
                        // 다만 고른(포커스) 칸은 어디인지 보이도록 연두 1.5dp 테두리를 두른다(S15P31E102-969).
                        // 테두리 있는 칸은 오류일 때 빨간 테두리를 두른다.
                        .then(
                            when {
                                !bordered -> if (focused) Modifier.border(1.5.dp, colors.primary, shape) else Modifier
                                error != null -> Modifier.border(1.5.dp, colors.rec, shape)
                                else -> Modifier.border(1.dp, colors.line, shape)
                            },
                        )
                        .padding(horizontal = if (labelInside) 16.dp else 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.padding(end = if (trailing != null) 48.dp else if (password) 36.dp else 0.dp)) {
                        // 자리 글자는 대비 기준(4.5:1)을 지키도록 회색 글자색을 쓴다(와이어프레임은 더 옅은 회색).
                        if (value.isEmpty()) Text(if (labelInside) label else placeholder, style = textStyle, color = colors.textMuted)
                        innerTextField()
                    }
                    if (password && trailing == null) {
                        val toggle = stringResource(if (revealed) Res.string.input_password_hide else Res.string.input_password_show)
                        // 누르는 영역은 44dp로 두고, 칸 오른쪽 여백 안쪽에 눈 아이콘을 맞춘다.
                        // 아이콘은 모바일 앱 관례대로 현재 상태를 보여 준다(가려짐: 빗금 친 눈, 보임: 눈).
                        Box(
                            Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).size(44.dp)
                                .clickable(role = Role.Button, onClickLabel = toggle) { revealed = !revealed }
                                .semantics { contentDescription = toggle },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (revealed) EolssuIcons.Eye else EolssuIcons.EyeOff, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.textMuted)
                        }
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
        val helpStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp)
        // 상한에 닿으면 도움말 대신 상한 안내를 보인다. 오류가 있으면 오류가 먼저다.
        val limitNotice = if (atLimit && overflowed && maxLength != null) stringResource(Res.string.input_limit_reached, maxLength) else null
        if (error != null || help != null || counter != null || limitNotice != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    when {
                        error != null -> Row(
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(EolssuIcons.Info, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.text)
                            Text(error, style = helpStyle.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                        }
                        limitNotice != null -> Text(
                            limitNotice,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = helpStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.recText,
                        )
                        help != null -> Text(help, style = helpStyle, color = colors.textMuted)
                    }
                }
                if (counter != null) Text(counter, style = helpStyle.copy(fontFeatureSettings = "tnum"), color = colors.textMuted)
            }
        }
    }
}
