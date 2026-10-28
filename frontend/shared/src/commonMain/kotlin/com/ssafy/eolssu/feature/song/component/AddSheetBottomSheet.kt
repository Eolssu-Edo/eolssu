package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.SheetHeader
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_add_audio_desc
import eolssu.shared.generated.resources.mysongs_add_audio_title
import eolssu.shared.generated.resources.mysongs_add_new_desc
import eolssu.shared.generated.resources.mysongs_add_new_title
import eolssu.shared.generated.resources.mysongs_add_pdf_desc
import eolssu.shared.generated.resources.mysongs_add_pdf_title
import eolssu.shared.generated.resources.mysongs_add_title
import eolssu.shared.generated.resources.mysongs_kind_pdf
import org.jetbrains.compose.resources.stringResource

/** 악보를 더하는 방법. */
enum class AddSheetOption { Pdf, Audio, New }

/** SONG-03 '악보 추가하기' 시트: PDF 올리기 · 음원으로 만들기 · 직접 만들기. */
@Composable
fun AddSheetBottomSheet(onSelect: (AddSheetOption) -> Unit, onDismiss: () -> Unit) {
    EolssuBottomSheet(
        onDismiss = onDismiss,
        header = { SheetHeader(stringResource(Res.string.mysongs_add_title), onClose = onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionRow(
                title = stringResource(Res.string.mysongs_add_pdf_title),
                description = stringResource(Res.string.mysongs_add_pdf_desc),
                onClick = { onSelect(AddSheetOption.Pdf) },
            ) { KindLabel(stringResource(Res.string.mysongs_kind_pdf)) }
            OptionRow(
                title = stringResource(Res.string.mysongs_add_audio_title),
                description = stringResource(Res.string.mysongs_add_audio_desc),
                onClick = { onSelect(AddSheetOption.Audio) },
            ) { OptionIcon(EolssuIcons.Music) }
            OptionRow(
                title = stringResource(Res.string.mysongs_add_new_title),
                description = stringResource(Res.string.mysongs_add_new_desc),
                onClick = { onSelect(AddSheetOption.New) },
            ) { OptionIcon(EolssuIcons.Pencil) }
        }
    }
}

@Composable
private fun OptionIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = EolssuTheme.colors.text)
}

@Composable
private fun OptionRow(title: String, description: String, onClick: () -> Unit, tile: @Composable () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = colors.surface) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindTile(background = colors.background, content = tile)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.text)
                Text(
                    description,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.8.sp),
                    color = colors.textMuted,
                )
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        }
    }
}
