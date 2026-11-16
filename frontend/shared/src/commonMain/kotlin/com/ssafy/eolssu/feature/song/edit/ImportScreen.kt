package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.feature.song.edit.component.sheetAddBottomBar
import com.ssafy.eolssu.feature.song.edit.component.sheetAddContentMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.song.edit.component.NoticeBox
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_import_cancel
import eolssu.shared.generated.resources.sheetedit_import_gif
import eolssu.shared.generated.resources.sheetedit_import_gif_note_jg
import eolssu.shared.generated.resources.sheetedit_import_gif_note_staff
import eolssu.shared.generated.resources.sheetedit_import_notice
import eolssu.shared.generated.resources.sheetedit_import_page
import eolssu.shared.generated.resources.sheetedit_import_progress
import eolssu.shared.generated.resources.sheetedit_import_result
import eolssu.shared.generated.resources.sheetedit_import_step_jg_1
import eolssu.shared.generated.resources.sheetedit_import_step_jg_1_desc
import eolssu.shared.generated.resources.sheetedit_import_step_jg_2
import eolssu.shared.generated.resources.sheetedit_import_step_jg_2_desc
import eolssu.shared.generated.resources.sheetedit_import_step_jg_3
import eolssu.shared.generated.resources.sheetedit_import_step_jg_3_desc
import eolssu.shared.generated.resources.sheetedit_import_step_jg_4
import eolssu.shared.generated.resources.sheetedit_import_step_jg_4_desc
import eolssu.shared.generated.resources.sheetedit_import_step_staff_1
import eolssu.shared.generated.resources.sheetedit_import_step_staff_1_desc
import eolssu.shared.generated.resources.sheetedit_import_step_staff_2
import eolssu.shared.generated.resources.sheetedit_import_step_staff_2_desc
import eolssu.shared.generated.resources.sheetedit_import_step_staff_3
import eolssu.shared.generated.resources.sheetedit_import_step_staff_3_desc
import eolssu.shared.generated.resources.sheetedit_import_step_staff_4
import eolssu.shared.generated.resources.sheetedit_import_step_staff_4_desc
import eolssu.shared.generated.resources.sheetedit_import_title_jg
import eolssu.shared.generated.resources.sheetedit_import_title_staff
import eolssu.shared.generated.resources.tiger_play
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 인식 단계의 진행 상태. */
private enum class StepState { Done, Now, Todo }

/**
 * EDIT-05 인식 중(정간보 인식 · 오선보 → 정간보 바꾸기). 인식 API를 붙이기 전이라 와이어프레임의 진행 모습(정간보 62% · 오선보 42%)을 그대로 보이고,
 * '결과 확인'으로 인식 결과(EDIT-06)에 간다. 마스코트 움직이는 그림(GIF)이 들어갈 자리에는 임시 그림을 둔다.
 */
@Composable
fun ImportScreen(
    kind: ImportKind,
    hwang: HwangPitch,
    onBack: () -> Unit,
    onResult: () -> Unit,
) {
    val staff = kind == ImportKind.Staff
    val percent = if (staff) 42 else 62
    val contentMaxWidth = sheetAddContentMaxWidth()
    EolssuScaffold(
        contentMaxWidth = contentMaxWidth,
        containerColor = EolssuTheme.colors.surface,
        topBar = {
            val fileName = SheetEditSamples.importFileName(kind)
            EolssuTopBar(
                stringResource(if (staff) Res.string.sheetedit_import_title_staff else Res.string.sheetedit_import_title_jg),
                onBack = onBack,
                maxWidth = contentMaxWidth,
                subtitle = if (staff) stringResource(Res.string.sheetedit_import_page, fileName, 1, 2) else fileName,
            )
        },
        bottomBar = sheetAddBottomBar(contentMaxWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EolssuButton(stringResource(Res.string.sheetedit_import_cancel), onBack, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, compact = true)
                EolssuButton(stringResource(Res.string.sheetedit_import_result), onResult, Modifier.weight(1.4f), compact = true)
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MascotSpot(
                note = stringResource(if (staff) Res.string.sheetedit_import_gif_note_staff else Res.string.sheetedit_import_gif_note_jg),
                progress = stringResource(Res.string.sheetedit_import_progress, "$percent%", 1, if (staff) 2 else 1),
            )
            // 진행 막대(높이 6 · 모서리 3).
            Box(Modifier.fillMaxWidth().height(6.dp).background(EolssuTheme.colors.surfaceStrong, CircleShape)) {
                Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(EolssuTheme.colors.primary, CircleShape))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (staff) {
                    ImportStep(stringResource(Res.string.sheetedit_import_step_staff_1), stringResource(Res.string.sheetedit_import_step_staff_1_desc, 14), StepState.Done)
                    ImportStep(stringResource(Res.string.sheetedit_import_step_staff_2), stringResource(Res.string.sheetedit_import_step_staff_2_desc, 3), StepState.Now)
                    ImportStep(stringResource(Res.string.sheetedit_import_step_staff_3), stringResource(Res.string.sheetedit_import_step_staff_3_desc, hwang.label), StepState.Todo)
                    ImportStep(stringResource(Res.string.sheetedit_import_step_staff_4), stringResource(Res.string.sheetedit_import_step_staff_4_desc), StepState.Todo)
                } else {
                    ImportStep(stringResource(Res.string.sheetedit_import_step_jg_1), stringResource(Res.string.sheetedit_import_step_jg_1_desc, 6), StepState.Done)
                    ImportStep(stringResource(Res.string.sheetedit_import_step_jg_2), stringResource(Res.string.sheetedit_import_step_jg_2_desc, 9), StepState.Done)
                    ImportStep(stringResource(Res.string.sheetedit_import_step_jg_3), stringResource(Res.string.sheetedit_import_step_jg_3_desc, 4), StepState.Now)
                    ImportStep(stringResource(Res.string.sheetedit_import_step_jg_4), stringResource(Res.string.sheetedit_import_step_jg_4_desc), StepState.Todo)
                }
            }
            NoticeBox(stringResource(Res.string.sheetedit_import_notice))
        }
    }
}

/** 마스코트 GIF 자리(높이 300 · 점선 안쪽 틀)와 왼쪽 아래 진행 알약. */
@Composable
private fun MascotSpot(note: String, progress: String) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(colors.surfaceStrong.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
            .padding(10.dp)
            .drawBehind {
                drawRoundRect(
                    colors.line,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            },
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(painterResource(Res.drawable.tiger_play), contentDescription = null, modifier = Modifier.size(132.dp).alpha(0.85f), contentScale = ContentScale.Fit)
            Text(stringResource(Res.string.sheetedit_import_gif), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(2.dp)
                .background(colors.background.copy(alpha = 0.93f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), color = colors.accentText, strokeWidth = 2.dp)
            Text(progress, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** 인식 단계 한 줄: 24dp 원(완료 ✓ 연두 · 진행 중 … 옅은 연두 · 대기 빈 원)과 이름·설명. */
@Composable
private fun ImportStep(label: String, description: String, state: StepState) {
    val colors = EolssuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val (fill, mark) = when (state) {
            StepState.Done -> colors.primary to "✓"
            StepState.Now -> colors.accentTint to "…"
            StepState.Todo -> colors.surface to ""
        }
        Box(
            Modifier
                .size(24.dp)
                .background(fill, CircleShape)
                .then(
                    when (state) {
                        StepState.Now -> Modifier.border(2.dp, colors.primary, CircleShape)
                        StepState.Todo -> Modifier.border(1.5.dp, colors.line, CircleShape)
                        StepState.Done -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                mark,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = if (state == StepState.Now) colors.accentText else colors.onPrimary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = if (state == StepState.Todo) colors.textDisabled else Color.Unspecified,
            )
            Text(description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        }
    }
}
