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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.InputLimit
import com.ssafy.eolssu.core.theme.EolssuTheme

/**
 * 여러 줄 메모 입력칸(S15P31E102-996). 연주 결과 저장 시트와 기록 상세의 메모 고치기가 함께 쓴다.
 *
 * - [maxLength]자(코드 포인트)를 넘는 입력은 칸에 들어가지 않는다. 값 대신 [TextFieldState]의 입력 변환으로 막아,
 *   한글을 조합하는 중에 상한에 닿아도 키보드와 칸의 내용이 어긋나 앞의 글이 되돌아가지 않는다.
 * - 줄바꿈을 여러 번 넣어도 칸은 [maxHeight]까지만 커지고, 그 뒤로는 칸 안에서 스크롤하며 커서를 따라간다.
 *   넘친 줄이 있으면 오른쪽에 스크롤 막대를 보인다.
 * - 칸 아래 오른쪽에 `n/[maxLength]`를 보이고, 왼쪽에는 [supporting](실패 안내 등)을 둔다.
 */
@Composable
fun EolssuMemoField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String? = null,
    showLabel: Boolean = false,
    maxLength: Int = InputLimit.MEMO,
    enabled: Boolean = true,
    error: Boolean = false,
    containerColor: Color? = null,
    minHeight: Dp = 120.dp,
    maxHeight: Dp = 240.dp,
    supporting: (@Composable () -> Unit)? = null,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, color = colors.text)
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    val limit = remember(maxLength) { MaxCodePoints(maxLength) }
    val length = InputLimit.length(state.text)
    val scroll = rememberScrollState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        }
        Box(
            Modifier.fillMaxWidth()
                .background(containerColor ?: colors.surface, shape)
                .border(if (error) 1.5.dp else 1.dp, if (error) colors.rec else colors.line, shape)
                // 칸을 넘친 줄이 있으면 오른쪽 테두리 안쪽에 스크롤 막대를 보인다.
                .verticalScrollIndicator(scroll, scrollIndicatorColor(colors.text))
                .padding(14.dp),
        ) {
            if (state.text.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = colors.textMuted)
            BasicTextField(
                state = state,
                enabled = enabled,
                inputTransformation = limit,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.text),
                lineLimits = TextFieldLineLimits.MultiLine(),
                scrollState = scroll,
                // 안쪽 여백(위아래 14)을 뺀 높이로 칸을 묶어, 넘치는 줄은 칸 안에서 스크롤한다.
                modifier = fieldModifier.fillMaxWidth()
                    .heightIn(min = minHeight - 28.dp, max = maxHeight - 28.dp)
                    .semantics { contentDescription = label },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { supporting?.invoke() }
            Text(
                "$length/$maxLength",
                Modifier.padding(start = 8.dp),
                style = small.copy(fontFeatureSettings = "tnum"),
                color = if (length >= maxLength) colors.recText else colors.textMuted,
            )
        }
    }
}

/**
 * [max]자(코드 포인트)를 넘는 입력을 막는다. 이미 가득 찬 칸에 치면 입력을 되돌리고,
 * 붙여넣기처럼 한 번에 넘치면 새로 들어온 글의 앞부분만 남긴다(서로게이트 쌍은 자르지 않는다).
 */
internal class MaxCodePoints(private val max: Int) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        val over = InputLimit.length(text) - max
        if (over <= 0) return
        val original = originalText
        if (InputLimit.length(original) >= max) {
            revertAllChanges()
            return
        }
        // 바뀐 구간: 앞뒤로 원래 글과 같은 부분을 뺀 나머지.
        var prefix = 0
        val shorter = minOf(text.length, original.length)
        while (prefix < shorter && text[prefix] == original[prefix]) prefix++
        var suffix = 0
        while (suffix < shorter - prefix && text[text.length - 1 - suffix] == original[original.length - 1 - suffix]) suffix++
        if (prefix > 0 && text[prefix - 1].isHighSurrogate()) prefix--
        val insertedEnd = text.length - suffix
        val inserted = text.subSequence(prefix, insertedEnd)
        val keep = InputLimit.length(inserted) - over
        if (keep <= 0) {
            revertAllChanges()
            return
        }
        replace(prefix, insertedEnd, inserted.subSequence(0, InputLimit.endOfFirst(inserted, keep)).toString())
    }
}
