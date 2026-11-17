package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.ssafy.eolssu.feature.song.edit.ImportKind
import com.ssafy.eolssu.feature.song.edit.MY_SHEET_LIMIT
import com.ssafy.eolssu.feature.song.edit.SheetUploadTip
import com.ssafy.eolssu.feature.song.edit.sheetUploadTips
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 악보 올리기 화면 맨 위 안내(S15P31E102-997). 처음 올리는 사람이 고르기 전에 한눈에 보도록
 * `이런 악보가 잘 읽혀요`(체크 줄)와 `알아 두세요`(제한 줄)를 짧은 한 줄씩 둔다.
 * 제한 값은 서버와 같다: 내 악보 [MY_SHEET_LIMIT]개, 변환은 회원당 한 번에 1건.
 */
@Composable
internal fun SheetUploadGuideCard(kind: ImportKind, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GuideTitle(stringResource(Res.string.sheet_upload_tips_title))
        sheetUploadTips(kind).forEach { tip ->
            when (tip) {
                SheetUploadTip.DigitalPdf -> GuideLine(EolssuIcons.Check, colors.accentText, stringResource(Res.string.sheet_upload_tips_pdf))
                SheetUploadTip.PhotoFraming -> GuideLine(EolssuIcons.Check, colors.accentText, stringResource(Res.string.sheet_upload_tips_photo))
                SheetUploadTip.StaffPhotoWeak -> GuideLine(EolssuIcons.Alert, colors.recText, stringResource(Res.string.sheet_upload_tips_staff_photo))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(colors.line)) {}
        GuideTitle(stringResource(Res.string.sheet_upload_rules_title))
        GuideLine(EolssuIcons.Info, colors.textSub, stringResource(Res.string.sheet_upload_rules_limit, MY_SHEET_LIMIT))
        GuideLine(EolssuIcons.Info, colors.textSub, stringResource(Res.string.sheet_upload_rules_one_at_a_time))
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
