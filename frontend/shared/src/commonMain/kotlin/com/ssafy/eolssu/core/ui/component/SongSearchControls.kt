package com.ssafy.eolssu.core.ui.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import com.ssafy.eolssu.core.model.InputLimit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_clear
import eolssu.shared.generated.resources.song_search_label
import eolssu.shared.generated.resources.song_search_placeholder
import eolssu.shared.generated.resources.song_sort_ascending
import eolssu.shared.generated.resources.song_sort_ascending_description
import eolssu.shared.generated.resources.song_sort_descending
import eolssu.shared.generated.resources.song_sort_descending_description
import org.jetbrains.compose.resources.stringResource

/** 곡 제목 검색칸(SONG-01 `.search-box`, 연습 기록 RECORD-01): 높이 46dp, 흰 바탕 · 옅은 테두리 · 모서리 10dp, 13sp. 앱 안에서 제목으로 거른다. */
@Composable
fun SongSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val label = stringResource(Res.string.song_search_label)
    val hideKeyboard = rememberHideKeyboard()
    val focusManager = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(colors.surface, shape)
            .then(if (bordered) Modifier.border(1.dp, colors.lineSoft, shape) else Modifier)
            .padding(start = 13.dp, end = if (query.isEmpty()) 13.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EolssuIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = colors.text)
            if (query.isEmpty()) {
                Text(
                    stringResource(Res.string.song_search_placeholder),
                    modifier = Modifier.clearAndSetSemantics {},
                    style = textStyle,
                    color = colors.textMuted,
                )
            }
            BasicTextField(
                value = query,
                // 검색어는 [InputLimit.SEARCH]자까지만 받는다(S15P31E102-996).
                onValueChange = UserActionLog.change(QaTarget.SONG_SEARCH_CONTROLS_01, { input -> onQueryChange(InputLimit.limit(query, input, InputLimit.SEARCH)) }),
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // 입력할 때마다 거르므로 검색 키는 키보드만 닫는다. 초점만 풀면 키보드가 남는 기기가 있어 함께 내린다(S15P31E102-800).
                keyboardActions = KeyboardActions(onAny = {
                    hideKeyboard()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        if (query.isNotEmpty()) {
            // 흰 칸 위에서 바로 보이도록 진한 회색 원에 흰 ×를 그린다(S15P31E102-870).
            // 누르는 범위는 48dp로 넓히고(46dp 칸 위아래로 1dp씩 넘친다), 오른쪽 여백을 없애 원이 검색 아이콘과 같은 거리에서 칸 끝에 붙게 한다.
            // 눌림 효과는 그린 22dp 원 안에만 둔다(S15P31E102-968).
            val clearInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .requiredSize(48.dp)
                    .clickable(interactionSource = clearInteraction, indication = null, role = Role.Button, onClick = UserActionLog.click(QaTarget.SONG_SEARCH_CONTROLS_02, onClear)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(colors.textMuted).indication(clearInteraction, ripple()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(EolssuIcons.Close, stringResource(Res.string.common_clear), Modifier.size(14.dp), tint = colors.onInk)
                }
            }
        }
    }
}

/**
 * 곡 이름 정렬 버튼: 정렬 아이콘과 지금 순서('ㄱ→ㅎ'·'ㅎ→ㄱ')를 보이고, 누르면 반대 순서로 바꾼다.
 * 화면 읽기에는 화살표 표기 대신 '가나다순'·'가나다 역순'으로 읽힌다.
 * 고르기 칩과 같은 36dp(15sp) 흰 알약이고, [small]이면 목록 위 안내 줄에 맞춘 28dp(13sp)다.
 */
@Composable
fun SongSortButton(order: SongSortOrder, onToggle: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false, bordered: Boolean = true) {
    val colors = EolssuTheme.colors
    val ascending = order == SongSortOrder.Ascending
    val description = stringResource(if (ascending) Res.string.song_sort_ascending_description else Res.string.song_sort_descending_description)
    Surface(
        onClick = UserActionLog.click(QaTarget.SONG_SEARCH_CONTROLS_03, onToggle),
        modifier = modifier.heightIn(min = if (small) 28.dp else 36.dp),
        shape = CircleShape,
        color = colors.surface,
        contentColor = colors.textSub,
        border = if (bordered) BorderStroke(1.dp, colors.lineSoft) else null,
    ) {
        Row(
            Modifier.padding(horizontal = if (small) 10.dp else 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(EolssuIcons.Sort, contentDescription = null, modifier = Modifier.size(if (small) 14.dp else 16.dp))
            Text(
                stringResource(if (ascending) Res.string.song_sort_ascending else Res.string.song_sort_descending),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                // 낱자(ㄱ·ㅎ)는 글꼴에서 작게 그려져 아이콘보다 1sp 크게 둔다.
                style = MaterialTheme.typography.labelLarge.copy(fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}
