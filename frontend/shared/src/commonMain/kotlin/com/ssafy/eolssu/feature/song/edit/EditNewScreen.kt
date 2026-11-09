package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import com.ssafy.eolssu.feature.song.edit.component.KindCard
import com.ssafy.eolssu.feature.song.edit.component.LinkCard
import com.ssafy.eolssu.feature.song.edit.component.PillChip
import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblemDialog
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_new_copy_title
import eolssu.shared.generated.resources.sheetedit_new_hwang_default
import eolssu.shared.generated.resources.sheetedit_new_hwang_desc
import eolssu.shared.generated.resources.sheetedit_new_hwang_title
import eolssu.shared.generated.resources.sheetedit_new_jg_title
import eolssu.shared.generated.resources.sheetedit_new_pick_file
import eolssu.shared.generated.resources.sheetedit_new_question
import eolssu.shared.generated.resources.sheetedit_new_staff_title
import eolssu.shared.generated.resources.sheetedit_new_subtitle
import eolssu.shared.generated.resources.sheetedit_new_title
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-01 악보 추가: 같은 크기로 나란히 둔 정간보 가져오기 · 오선보 가져오기 카드와 그 아래 따로 둔 '내 악보·제공 곡 불러오기' 카드.
 * 오선보만 황으로 읽을 음 칩을 보인다. 아래 고정 버튼 '사진·PDF 선택하기'는 고른 종류로 파일 선택에 간다.
 * 파일 형식·촬영 안내는 화면에 늘 두지 않고, 문제가 생겼을 때만 [EditNewUiState.problem] 안내 창으로 알린다.
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
    onDismissProblem: () -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.sheetedit_new_question),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KindCard(
                    stringResource(Res.string.sheetedit_new_jg_title),
                    selected = state.kind == ImportKind.Jeongganbo,
                    onClick = { onSelectKind(ImportKind.Jeongganbo) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                KindCard(
                    stringResource(Res.string.sheetedit_new_staff_title),
                    selected = state.kind == ImportKind.Staff,
                    onClick = { onSelectKind(ImportKind.Staff) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            if (state.kind == ImportKind.Staff) HwangPicker(state.hwang, onSelectHwang)
            // 파일 업로드와 다른 기능이라 두 카드와 조금 떼어 둔다.
            Spacer(Modifier.height(6.dp))
            LinkCard(stringResource(Res.string.sheetedit_new_copy_title), onLoadExisting)
        }
    }
    state.problem?.let { problem ->
        SheetFileProblemDialog(
            problem,
            onRepick = {
                onDismissProblem()
                onOpenDevicePick()
            },
            onDismiss = onDismissProblem,
        )
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
