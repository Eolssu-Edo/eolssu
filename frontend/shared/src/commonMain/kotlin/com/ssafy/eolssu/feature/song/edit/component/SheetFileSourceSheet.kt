package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.storage.SheetFileSource
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.SheetHeader
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * `PDF·이미지 선택`을 눌렀을 때 가져올 곳을 고르는 아래 패널(S15P31E102-997).
 * 플랫폼이 주는 [sources] 순서대로 한 줄씩 두고, 고르면 패널을 닫은 뒤 [onPick]으로 그 선택 창을 연다.
 */
@Composable
internal fun SheetFileSourceSheet(sources: List<SheetFileSource>, onPick: (SheetFileSource) -> Unit, onDismiss: () -> Unit) {
    EolssuBottomSheet(
        onDismiss = onDismiss,
        // 흰 줄이 바탕과 구분되도록 회색 바탕에 둔다.
        containerColor = EolssuTheme.colors.groupedBackground,
        header = { SheetHeader(stringResource(Res.string.sheet_upload_source_title), onClose = onDismiss) },
    ) {
        sources.forEach { source ->
            when (source) {
                SheetFileSource.Album -> LinkRow(SheetEditIcons.Photo, stringResource(Res.string.sheet_upload_source_album), onClick = { onPick(source) })
                SheetFileSource.Files -> LinkRow(SheetEditIcons.Folder, stringResource(Res.string.sheet_upload_source_files), onClick = { onPick(source) })
            }
        }
    }
}
