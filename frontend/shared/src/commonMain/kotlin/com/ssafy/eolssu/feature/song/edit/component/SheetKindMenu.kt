package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuMenuAlign
import com.ssafy.eolssu.core.ui.component.EolssuMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.ui.component.EolssuMenuPopup
import com.ssafy.eolssu.feature.song.edit.ImportKind
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 악보 추가의 '내 악보 가져오기' 카드 오른쪽 화살표에서 펼쳐지는 악보 종류 메뉴(S15P31E102-997). 악기 선택 드롭다운과 같은 공통 메뉴를 쓴다.
 * 고르는 즉시 그 종류의 악보 올리기 화면으로 가므로 고른 표시는 두지 않는다.
 */
@Composable
internal fun SheetKindMenu(onPick: (ImportKind) -> Unit, onDismiss: () -> Unit) {
    // 카드 오른쪽 아래 화살표에서 펼쳐지도록 오른쪽 끝에 맞추고, 악기 메뉴처럼 넉넉한 폭·큰 그림으로 둔다.
    EolssuMenuPopup(onDismissRequest = onDismiss, align = EolssuMenuAlign.End, gap = 6.dp) {
        EolssuMenuPanel(width = 240.dp) {
            ImportKind.entries.forEach { kind ->
                val (label, icon) = when (kind) {
                    ImportKind.Jeongganbo -> stringResource(Res.string.sheetedit_new_kind_jeongganbo) to SheetEditIcons.Jeongganbo
                    ImportKind.Staff -> stringResource(Res.string.sheetedit_new_kind_staff) to SheetEditIcons.StaffNotes
                }
                EolssuMenuItem(label, selected = false, onClick = { onPick(kind) }, leading = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = EolssuTheme.colors.text)
                })
            }
        }
    }
}
