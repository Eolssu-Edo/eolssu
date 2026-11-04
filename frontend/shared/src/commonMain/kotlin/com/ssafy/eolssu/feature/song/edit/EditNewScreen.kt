package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.song.edit.component.ChoiceCard
import com.ssafy.eolssu.feature.song.edit.component.DevicePickSheet
import com.ssafy.eolssu.feature.song.edit.component.HintText
import com.ssafy.eolssu.feature.song.edit.component.PillChip
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_new_copy_desc
import eolssu.shared.generated.resources.sheetedit_new_copy_title
import eolssu.shared.generated.resources.sheetedit_new_file_hint
import eolssu.shared.generated.resources.sheetedit_new_hwang_default
import eolssu.shared.generated.resources.sheetedit_new_hwang_desc
import eolssu.shared.generated.resources.sheetedit_new_hwang_title
import eolssu.shared.generated.resources.sheetedit_new_jg_desc
import eolssu.shared.generated.resources.sheetedit_new_jg_title
import eolssu.shared.generated.resources.sheetedit_new_pick_file
import eolssu.shared.generated.resources.sheetedit_new_question
import eolssu.shared.generated.resources.sheetedit_new_staff_desc
import eolssu.shared.generated.resources.sheetedit_new_staff_title
import eolssu.shared.generated.resources.sheetedit_new_subtitle
import eolssu.shared.generated.resources.sheetedit_new_title
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-01 악보 추가: 정간보 가져오기 · 오선보 가져오기(함께 묶음)와 조금 떨어진 '내 악보·제공 곡 불러오기' 카드.
 * 오선보만 황으로 읽을 음 칩을 보인다. 장단은 인식한 뒤 정하므로 고르지 않는다.
 * 넓은 창에서는 [EolssuScaffold]가 휴대폰 너비로 가운데에 둔다.
 */
@Composable
fun EditNewScreen(
    state: EditNewUiState,
    onBack: () -> Unit,
    onSelectKind: (ImportKind) -> Unit,
    onSelectHwang: (HwangPitch) -> Unit,
    onLoadExisting: () -> Unit,
    onOpenDevicePick: () -> Unit,
    onCloseDevicePick: () -> Unit,
    onDevicePicked: () -> Unit,
) {
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        topBar = {
            EolssuTopBar(stringResource(Res.string.sheetedit_new_title), onBack = onBack, subtitle = stringResource(Res.string.sheetedit_new_subtitle))
        },
        bottomBar = { EolssuButton(stringResource(Res.string.sheetedit_new_pick_file), onOpenDevicePick) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Res.string.sheetedit_new_question),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
            ChoiceCard(
                stringResource(Res.string.sheetedit_new_jg_title),
                stringResource(Res.string.sheetedit_new_jg_desc),
                selected = state.kind == ImportKind.Jeongganbo,
                onClick = { onSelectKind(ImportKind.Jeongganbo) },
            )
            ChoiceCard(
                stringResource(Res.string.sheetedit_new_staff_title),
                stringResource(Res.string.sheetedit_new_staff_desc),
                selected = state.kind == ImportKind.Staff,
                onClick = { onSelectKind(ImportKind.Staff) },
            )
            if (state.kind == ImportKind.Staff) HwangPicker(state.hwang, onSelectHwang)
            HintText(stringResource(Res.string.sheetedit_new_file_hint), Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp), size = 12)
            // 와이어프레임: 가져오기 두 카드와 불러오기 카드 사이를 띄운다.
            Spacer(Modifier.height(14.dp))
            ChoiceCard(
                stringResource(Res.string.sheetedit_new_copy_title),
                stringResource(Res.string.sheetedit_new_copy_desc),
                selected = false,
                onClick = onLoadExisting,
                radio = false,
            )
        }
    }
    if (state.devicePickOpen) {
        DevicePickSheet(kind = state.kind, onPick = onDevicePicked, onDismiss = onCloseDevicePick)
    }
}

/** 오선보 가져오기의 '황(黃)으로 읽을 음' 칩. 단소 기본값 E♭4에 '단소 기본'을 붙인다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HwangPicker(selected: HwangPitch, onSelect: (HwangPitch) -> Unit) {
    val colors = EolssuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val title = stringResource(Res.string.sheetedit_new_hwang_title)
        val desc = stringResource(Res.string.sheetedit_new_hwang_desc)
        Text(
            buildAnnotatedString {
                append(title)
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = colors.textMuted)) { append(" · $desc") }
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HwangPitch.entries.forEach { pitch ->
                val label = if (pitch == HwangPitch.Default) stringResource(Res.string.sheetedit_new_hwang_default, pitch.label) else pitch.label
                PillChip(label, selected = pitch == selected, onClick = { onSelect(pitch) })
            }
        }
    }
}
