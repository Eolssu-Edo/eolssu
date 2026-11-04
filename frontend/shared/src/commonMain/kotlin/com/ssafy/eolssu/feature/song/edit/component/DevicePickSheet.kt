package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.feature.song.edit.ImportKind
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.sheetedit_device_camera
import eolssu.shared.generated.resources.sheetedit_device_camera_desc
import eolssu.shared.generated.resources.sheetedit_device_file
import eolssu.shared.generated.resources.sheetedit_device_file_desc
import eolssu.shared.generated.resources.sheetedit_device_limit
import eolssu.shared.generated.resources.sheetedit_device_next_jg
import eolssu.shared.generated.resources.sheetedit_device_next_staff
import eolssu.shared.generated.resources.sheetedit_device_photos
import eolssu.shared.generated.resources.sheetedit_device_photos_desc
import eolssu.shared.generated.resources.sheetedit_device_placeholder
import eolssu.shared.generated.resources.sheetedit_device_title_jg
import eolssu.shared.generated.resources.sheetedit_device_title_staff
import org.jetbrains.compose.resources.stringResource

/**
 * DevicePick: 기기 기본 선택 창이 들어갈 자리. 사진 찍기 / 사진 보관함(JPG·PNG 1~10장) / 파일(PDF 1개 · 10쪽), 합쳐 20MB.
 * 플랫폼 파일 선택기를 붙이기 전까지 어느 줄을 눌러도 예시 파일로 인식 화면([onPick])에 간다.
 * 와이어프레임처럼 닫기(X) 없이 아래 '취소'·바깥 누르기로 닫는다.
 */
@Composable
internal fun DevicePickSheet(kind: ImportKind, onPick: () -> Unit, onDismiss: () -> Unit) {
    val colors = EolssuTheme.colors
    EolssuBottomSheet(
        onDismiss = onDismiss,
        header = {
            DeviceSheetHeader(
                stringResource(if (kind == ImportKind.Staff) Res.string.sheetedit_device_title_staff else Res.string.sheetedit_device_title_jg),
                stringResource(Res.string.sheetedit_device_limit),
            )
        },
        footer = { EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary) },
    ) {
        PlaceholderTag(
            stringResource(
                Res.string.sheetedit_device_placeholder,
                stringResource(if (kind == ImportKind.Staff) Res.string.sheetedit_device_next_staff else Res.string.sheetedit_device_next_jg),
            ),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.background, RoundedCornerShape(14.dp))
                .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp)),
        ) {
            DeviceRow(SheetEditIcons.Camera, stringResource(Res.string.sheetedit_device_camera), stringResource(Res.string.sheetedit_device_camera_desc), onPick)
            HorizontalDivider(color = colors.lineSoft)
            DeviceRow(SheetEditIcons.Photo, stringResource(Res.string.sheetedit_device_photos), stringResource(Res.string.sheetedit_device_photos_desc), onPick)
            HorizontalDivider(color = colors.lineSoft)
            DeviceRow(SheetEditIcons.Folder, stringResource(Res.string.sheetedit_device_file), stringResource(Res.string.sheetedit_device_file_desc), onPick)
        }
    }
}

/** 기기 선택 창 머리: 가운데 맞춘 회색 13sp 제목과 11sp 용량 안내(와이어프레임 DevicePick). */
@Composable
private fun DeviceSheetHeader(title: String, subtitle: String) {
    val muted = EolssuTheme.colors.textMuted
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = muted, textAlign = TextAlign.Center)
        Text(
            subtitle,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** 와이어프레임의 점선 표시 '기기 기본 선택 창 자리 · 고르면 바로 …'. */
@Composable
private fun PlaceholderTag(text: String) {
    val colors = EolssuTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        colors.textMuted,
                        cornerRadius = CornerRadius(size.height / 2),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
        )
    }
}

@Composable
private fun DeviceRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), color = colors.background) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.textSub)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
                Text(
                    description,
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
            }
        }
    }
}
