package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuMenuPopup
import com.ssafy.eolssu.core.ui.component.EolssuMenuShape
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 악보 올리기 `악보 파일` 제목 옆 ⓘ(S15P31E102-1230). 제목 위에 카드 한 장을 차지하던 안내를
 * 누를 때만 말풍선으로 보인다. 터치 화면에는 호버가 없으므로 눌러서 열고, 바깥을 누르거나 뒤로 가면 닫는다.
 */
@Composable
internal fun SheetUploadInfoButton(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val label = stringResource(Res.string.sheet_upload_files_info)
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = UserActionLog.click(QaTarget.SHEET_UPLOAD_INFO_01, { open = !open }))
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(EolssuIcons.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        }
        if (open) EolssuMenuPopup(onDismissRequest = { open = false }) {
            InfoBubble { InfoLine(stringResource(Res.string.sheet_upload_tips_pdf)) }
        }
    }
}

/** 드롭다운 메뉴와 같은 흰 카드(모서리 16dp, 옅은 그림자)에 안내 줄을 담는다. */
@Composable
private fun InfoBubble(content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(260.dp)
            .shadow(16.dp, EolssuMenuShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(EolssuMenuShape)
            .background(EolssuTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun InfoLine(text: String) {
    val colors = EolssuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(16.dp), tint = colors.accentText)
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = colors.textSub)
    }
}
