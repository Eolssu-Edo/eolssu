package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.song.edit.SheetImportGuide
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * 가져온 악보 확인 카드(S15P31E102-979·1042). 악보 위에 두어 처음 열 때 바로 보이게 한다.
 *
 * 문제 종류마다 한 줄씩, 악보 칸 표시와 같은 바탕·점선·태그(갈색 ↕ = 낼 수 없는 음, 빨간 ! = 다시 볼 곳)로 보여
 * 카드와 악보의 표시를 바로 잇게 한다. 음역 줄은 버튼 하나로 고치고, 다시 볼 곳 줄은 이전·다음으로 칸을 옮겨 다닌다.
 */
@Composable
internal fun SheetImportGuideCard(
    guide: SheetImportGuide,
    onApplyRecommended: () -> Unit,
    onOpenAdaptation: () -> Unit,
    onMoveReview: (step: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val showRange = guide.rangeNotes > 0 || guide.recommendedTranspose != null
    val showReview = guide.reviewCells > 0
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showRange) GuideRow(RANGE_TAG, colors.pitchError, colors.pitchErrorSoft,
                stringResource(Res.string.sheet_import_guide_range, instrumentWithRo(guide.instrumentName), guide.rangeNotes)) {
                // 무엇을 하면 어떻게 되는지를 버튼 글자 하나로 말한다. 추천이 없으면 높이 맞추기 탭으로 보낸다.
                val shift = guide.recommendedShift?.takeIf { it != 0 && guide.recommendedTranspose != null }
                if (shift != null) {
                    val fixed = guide.rangeNotes - (guide.recommendedRemaining ?: 0)
                    val all = guide.recommendedRemaining == 0
                    GuideButton(when {
                        shift > 0 && all -> stringResource(Res.string.sheet_import_guide_up_all, shift)
                        shift > 0 -> stringResource(Res.string.sheet_import_guide_up, shift, fixed)
                        all -> stringResource(Res.string.sheet_import_guide_down_all, abs(shift))
                        else -> stringResource(Res.string.sheet_import_guide_down, abs(shift), fixed)
                    }, onApplyRecommended)
                } else GuideButton(stringResource(Res.string.sheet_import_guide_adapt), onOpenAdaptation)
            }
            if (showReview) GuideRow("!", colors.rec, colors.recSoft,
                guide.reviewPosition?.let { stringResource(Res.string.sheet_import_guide_review_position, it, guide.reviewCells) }
                    ?: stringResource(Res.string.sheet_import_guide_review, guide.reviewCells)) {
                // 처음엔 첫 칸으로 가고, 다시 볼 칸에 있으면 이전·다음으로 옮긴다.
                val position = guide.reviewPosition
                if (position == null) GuideButton(stringResource(Res.string.sheet_import_guide_review_go)) { onMoveReview(1) }
                else {
                    StepButton(stringResource(Res.string.sheet_import_guide_review_prev), 90f, enabled = position > 1) { onMoveReview(-1) }
                    StepButton(stringResource(Res.string.sheet_import_guide_review_next), -90f, enabled = position < guide.reviewCells) { onMoveReview(1) }
                }
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(EolssuIcons.Close, contentDescription = stringResource(Res.string.sheet_import_guide_close),
                Modifier.size(16.dp), tint = colors.textSub)
        }
    }
}

/** 악보 칸 표시와 같은 바탕·점선·태그를 한 줄에 담는다. 오른쪽에 바로 처리할 버튼을 둔다. */
@Composable
private fun GuideRow(tag: String, ring: Color, fill: Color, text: String, actions: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).background(fill, shape).dashedBorder(ring, shape)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(22.dp).background(ring, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(tag, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = EolssuTheme.colors.text, maxLines = 2)
        actions()
    }
}

/** 줄 오른쪽의 작은 흰 버튼. */
@Composable
private fun GuideButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.heightIn(min = 36.dp), shape = RoundedCornerShape(8.dp),
        color = EolssuTheme.colors.surface, contentColor = EolssuTheme.colors.text) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** 다시 볼 곳 이전·다음(보이는 칸 36dp). 아래 화살표 그림을 돌려 왼쪽·오른쪽을 가리킨다. */
@Composable
private fun StepButton(label: String, rotation: Float, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp),
        color = EolssuTheme.colors.surface, contentColor = EolssuTheme.colors.text) {
        Box(contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.ChevronDown, contentDescription = label, modifier = Modifier.size(18.dp).rotate(rotation)
                .alpha(if (enabled) 1f else 0.3f))
        }
    }
}

/** 악보 칸 표시와 같은 점선 테두리. */
private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, color, style = Stroke(width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
}

/** `소금으로`·`단소로`·`대금으로`처럼 받침에 맞춰 붙인다. 이름을 모르면 `악기로`. */
@Composable
private fun instrumentWithRo(name: String?): String {
    val word = name?.takeIf { it.isNotBlank() } ?: return stringResource(Res.string.sheet_import_guide_instrument_fallback)
    val code = word.last().code - 0xAC00
    val batchim = if (code in 0..11171) code % 28 else 0
    return word + if (batchim == 0 || batchim == 8) "로" else "으로"
}
