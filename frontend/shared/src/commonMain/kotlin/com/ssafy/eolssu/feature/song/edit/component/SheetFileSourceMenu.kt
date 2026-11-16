package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.storage.SheetFileSource
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.ui.component.EolssuMenuPopup
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * `PDF·이미지 선택` 버튼 아래로 펼쳐지는 가져올 곳 메뉴(S15P31E102-997). 악기 선택 드롭다운과 같은 공통 메뉴를 쓴다.
 * `파일`은 문서 선택기(드라이브), `갤러리`는 휴대폰 사진첩을 연다. 사진 촬영(S15P31E102-1003)은 [SheetFileSource]에 항목을 더해 붙인다.
 * 고르는 즉시 선택 창을 여는 동작이라 고른 표시는 두지 않는다.
 */
@Composable
internal fun SheetFileSourceMenu(sources: List<SheetFileSource>, onPick: (SheetFileSource) -> Unit, onDismiss: () -> Unit) {
    EolssuMenuPopup(onDismissRequest = onDismiss) {
        EolssuMenuPanel(width = 200.dp) {
            sources.forEach { source ->
                val (label, icon) = when (source) {
                    SheetFileSource.Files -> stringResource(Res.string.sheet_upload_source_files) to SheetEditIcons.Folder
                    SheetFileSource.Album -> stringResource(Res.string.sheet_upload_source_album) to SheetEditIcons.Photo
                }
                EolssuMenuItem(label, selected = false, onClick = { onPick(source) }, leading = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = EolssuTheme.colors.text)
                })
            }
        }
    }
}
