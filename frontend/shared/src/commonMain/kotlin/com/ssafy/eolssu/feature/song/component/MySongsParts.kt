package com.ssafy.eolssu.feature.song.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.song.MySongsTab
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.mysongs_add
import eolssu.shared.generated.resources.mysongs_delete
import eolssu.shared.generated.resources.mysongs_delete_message
import eolssu.shared.generated.resources.mysongs_delete_title
import eolssu.shared.generated.resources.mysongs_tab_sheets
import eolssu.shared.generated.resources.mysongs_tab_songs
import org.jetbrains.compose.resources.stringResource

/** SONG-03 두 칸 탭(`.tabs`, `role=tablist`): 흰 바탕 옅은 테두리 틀 안에서 고른 칸만 연두 바탕. 개수는 회색으로 붙인다. */
@Composable
internal fun MySongsTabSwitch(
    tab: MySongsTab,
    songCount: Int,
    sheetCount: Int,
    onSelect: (MySongsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val frame = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceStrong, frame)
            .padding(4.dp),
    ) {
        MySongsTab.entries.forEach { entry ->
            val selected = tab == entry
            val (label, count) = when (entry) {
                MySongsTab.Songs -> stringResource(Res.string.mysongs_tab_songs) to songCount
                MySongsTab.Sheets -> stringResource(Res.string.mysongs_tab_sheets) to sheetCount
            }
            Surface(
                onClick = UserActionLog.click(QaTarget.MY_SONGS_PARTS_01, { onSelect(entry) }),
                modifier = Modifier
                    .weight(1f)
                    // 보이는 높이를 누르는 최소 높이(48dp)와 같게 해 위아래 간격이 양옆 4dp와 같아진다.
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                // 틀 모서리(14dp)에서 안쪽 여백(4dp)을 뺀 값이라 두 모서리가 나란히 휜다.
                shape = RoundedCornerShape(10.dp),
                color = if (selected) colors.surface else Color.Transparent,
                contentColor = if (selected) colors.text else colors.textSub,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                    Text(label, style = style.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
                    Text("$count", style = style.copy(fontWeight = FontWeight.Medium), color = colors.textMuted)
                }
            }
        }
    }
}

/** 내 악보 삭제 확인(`Song-Practice-Delete`): 붉은 휴지통 고리 · '삭제' · '취소'. 가림막·뒤로 가기도 취소다. 내 악보 탭과 악보 추가 화면이 함께 쓴다. */
@Composable
internal fun DeleteSheetDialog(sheet: MySheet, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = EolssuTheme.colors
    EolssuDialog(
        title = stringResource(Res.string.mysongs_delete_title, sheet.title),
        message = stringResource(Res.string.mysongs_delete_message),
        icon = EolssuIcons.Trash,
        iconBackground = colors.recSoft,
        iconTint = colors.recText,
        onDismissRequest = onDismiss,
    ) {
        EolssuButton(stringResource(Res.string.mysongs_delete), onConfirm, style = EolssuButtonStyle.Danger, qaTarget = QaTarget.MY_SONGS_PARTS_BUTTON_01)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.MY_SONGS_PARTS_BUTTON_02)
    }
}

/** '악보 추가'(`.btn.outline`, 2차: 누르면 바로 악보 추가 화면): 52dp, 회색 바탕 위 테두리 없는 흰 단추, 모서리 14dp, 더하기 20dp. */
@Composable
internal fun AddSheetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = UserActionLog.click(QaTarget.MY_SONGS_PARTS_02, onClick),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        contentColor = colors.text,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EolssuIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.mysongs_add), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
