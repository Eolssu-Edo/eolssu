package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 악보 올리기 화면 맨 위 안내(S15P31E102-997). 사진 악보는 받지 않으므로(S15P31E102-1225)
 * `이런 악보가 잘 읽혀요` 아래 PDF 안내 한 줄만 둔다. 개수 제한은 내 악보 목록에서 보여 준다.
 */
@Composable
internal fun SheetUploadGuideCard(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GuideTitle(stringResource(Res.string.sheet_upload_tips_title))
        GuideLine(EolssuIcons.Check, colors.accentText, stringResource(Res.string.sheet_upload_tips_pdf))
    }
}

@Composable
private fun GuideTitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = EolssuTheme.colors.text)
}

@Composable
private fun GuideLine(icon: ImageVector, tint: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(16.dp), tint = tint)
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = EolssuTheme.colors.textSub)
    }
}
