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
    header: @Composable () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EolssuTheme.colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.35f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
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
                .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
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

/** 패널 머리(`.sheet-head`): 제목·보조 문구와 닫기 버튼. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
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

/** 켜고 끄는 스위치(`.switch`, 44×26). */
@Composable
fun EolssuSwitch(checked: Boolean, onToggle: () -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                this.contentDescription = label
            },
        shape = CircleShape,
        color = if (checked) colors.text else colors.surfaceStrong,
    ) {
        Box(Modifier.padding(3.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(20.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
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
 * 확인 창(`.dialog`): 아이콘 칸 · 제목 · 설명 · 세로로 쌓은 버튼. 바깥을 눌러도 닫히지 않는다.
 */
@Composable
fun EolssuDialog(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    maxWidth: Dp = 340.dp,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .background(EolssuTheme.colors.background, RoundedCornerShape(20.dp))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Box(
                    Modifier.padding(bottom = 4.dp).size(44.dp).background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = EolssuTheme.colors.text)
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp))
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.7.sp),
                    color = EolssuTheme.colors.textMuted,
                )
            }
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}
