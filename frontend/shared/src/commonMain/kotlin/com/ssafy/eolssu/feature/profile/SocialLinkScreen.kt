package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.my_link_placeholder_body
import eolssu.shared.generated.resources.my_link_placeholder_title
import eolssu.shared.generated.resources.my_link_result_done
import eolssu.shared.generated.resources.my_link_result_hint
import eolssu.shared.generated.resources.my_link_result_taken
import eolssu.shared.generated.resources.my_link_taken_message
import eolssu.shared.generated.resources.my_link_taken_title
import eolssu.shared.generated.resources.my_link_title
import org.jetbrains.compose.resources.stringResource

/**
 * MY-05 간편 로그인 연결. 실제로는 제공자 화면이 뜨는 자리라 점선 자리 표시만 두고, 제공자 로그인 뒤 서버 결과는 아래 버튼으로 고른다(예시).
 * 연결되면 [onLinked], 이미 다른 얼쑤 계정에 연결된 제공자 계정이면 안내 창을 띄운 뒤 [onBack]으로 돌아간다(오류 코드는 BE 확인).
 */
@Composable
fun SocialLinkScreen(
    provider: SocialProvider,
    onBack: () -> Unit,
    onLinked: () -> Unit,
    widthClass: WindowWidthClass = currentWindowLayout().widthClass,
) {
    val colors = EolssuTheme.colors
    val name = stringResource(provider.displayName)
    var taken by rememberSaveable { mutableStateOf(false) }
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_link_title, name),
        onBack,
        bottomBar = {
            Text(
                stringResource(Res.string.my_link_result_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            EolssuButton(stringResource(Res.string.my_link_result_done), onClick = onLinked)
            EolssuButton(stringResource(Res.string.my_link_result_taken), onClick = { taken = true }, style = EolssuButtonStyle.Secondary)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (widthClass == WindowWidthClass.Compact) 360.dp else 560.dp)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = colors.lineStrong,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.my_link_placeholder_title, name),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.my_link_placeholder_body),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.8.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (taken) {
        EolssuDialog(
            title = stringResource(Res.string.my_link_taken_title, name),
            message = stringResource(Res.string.my_link_taken_message, name),
            icon = EolssuIcons.Info,
        ) {
            EolssuButton(stringResource(Res.string.common_confirm), onClick = onBack)
        }
    }
}
