package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import org.jetbrains.compose.resources.stringResource

/**
 * 아래에서 올라오는 패널(`.sheet`). 창 높이의 [maxHeightFraction]까지 커지고, 본문은 [body]에서 스크롤된다.
 * [footer]는 본문 아래에 고정된다(`.sheet-foot`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EolssuBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.78f,
    containerColor: Color? = null,
    header: @Composable () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // 연두 보드 `.sheet`: 흰 바탕, 위 반지름 24, 손잡이 40×4(위 12 · 아래 2), 안쪽 여백 20 · 아래 34, 스크림 먹색 45%.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = containerColor ?: EolssuTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = EolssuTheme.colors.text.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 2.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(EolssuTheme.colors.line, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = windowHeight * maxHeightFraction)
                .padding(start = 20.dp, end = 20.dp, bottom = 34.dp),
        ) {
            header()
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = body,
            )
            if (footer != null) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = footer)
            }
        }
    }
}

/** 패널 머리(`.sheet-head`): 제목(Song Myung 18sp)·보조 문구와 닫기 버튼. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.4.sp))
            if (subtitle != null) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                )
            }
        }
        EolssuIconButton(EolssuIcons.Close, stringResource(Res.string.common_close), onClose, bordered = false)
    }
}

/**
 * 켜고 끄는 스위치(연두 보드 `.switch`): 켜면 연두, 끄면 옅은 테두리 색 바탕에 흰 손잡이.
 * 크기는 화면마다 다르다 — 연습 설정 시트 50×30(기본), 기록 상세 40×24. 손잡이는 높이에서 6dp를 뺀 원이다.
 */
@Composable
fun EolssuSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 50.dp,
    height: Dp = 30.dp,
    offColor: Color? = null,
) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier
            .size(width = width, height = height)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                this.contentDescription = label
            },
        shape = CircleShape,
        color = if (checked) colors.primary else offColor ?: colors.line,
    ) {
        Box(Modifier.padding(3.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            // 앱 전체 스위치를 그림자 없는 흰 손잡이로 맞춘다(S15P31E102-768).
            Box(Modifier.size(height - 6.dp).background(Color.White, CircleShape))
        }
    }
}

/** 설명이 붙은 켜기·끄기 줄(`.toggle-row`). [enabled]가 아니면 흐리게 보이고 누를 수 없다. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            if (description != null) {
                Text(
                    description,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                )
            }
        }
        EolssuSwitch(checked, onToggle, label, enabled = enabled)
    }
}

/**
 * 확인 창(연두 보드 `.dialog`): 양옆 28dp 안쪽의 흰 상자(반지름 20, 여백 24·20·20)에 48dp 아이콘 고리(`.ico-ring`) ·
 * 19sp 제목 · 14sp 설명 · 세로로 쌓은 버튼(간격 8). 기본은 바깥을 누르거나 뒤로 가도 닫히지 않는다.
 *
 * @param onDismissRequest 바깥(가림막)을 누르거나 뒤로 갈 때. 닫기 버튼 없이 이렇게 닫는 창만 넘긴다.
 * @param iconBackground 아이콘 고리 바탕. 기본 연두 20%(`accentTint`), 경고는 `recSoft`, 완료는 `accentTintStrong`.
 * @param iconTint 아이콘 색. 기본 먹색, 경고는 `recText`.
 */
@Composable
fun EolssuDialog(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    maxWidth: Dp = 340.dp,
    iconBackground: Color? = null,
    iconTint: Color? = null,
    onDismissRequest: () -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit,
) {
    val colors = EolssuTheme.colors
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // 휴대폰 가로·큰 글꼴·키보드로 높이가 모자라면 위쪽 내용만 스크롤하고 버튼은 아래에 고정한다.
        Column(
            modifier
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 16.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .background(colors.surface, RoundedCornerShape(20.dp))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Box(
                        Modifier.size(48.dp).background(iconBackground ?: colors.accentTint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint ?: colors.text)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.7.sp), color = colors.text)
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = colors.textSub,
                    )
                }
            }
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}
