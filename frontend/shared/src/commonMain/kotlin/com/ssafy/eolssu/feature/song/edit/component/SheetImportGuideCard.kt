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
 * 초록 상자 안에 `확인 필요: …` 한 줄과 바로 처리하는 흰 버튼 하나씩만 둔다. 음역 버튼은 글자가 결과까지 말하고,
 * 다시 볼 곳 버튼은 첫 칸으로 가서 고칠 방법을 연다.
 */
@Composable
internal fun SheetImportGuideCard(
    guide: SheetImportGuide,
    onApplyRecommended: () -> Unit,
    onOpenAdaptation: () -> Unit,
    onOpenReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val showRange = guide.rangeNotes > 0 || guide.recommendedTranspose != null
    val showReview = guide.reviewCells > 0
    Column(
        modifier.fillMaxWidth().background(colors.accentTint, shape).border(1.dp, colors.accentTintLine, shape)
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showRange) {
            GuideLine(stringResource(Res.string.sheet_import_guide_range, instrumentWithRo(guide.instrumentName), guide.rangeNotes),
                onDismiss = onDismiss)
            // 무엇을 하면 어떻게 되는지를 버튼 글자 하나로 말한다. 추천이 없으면 높이 맞추기 탭으로 보낸다.
            val shift = guide.recommendedShift?.takeIf { it != 0 && guide.recommendedTranspose != null }
            if (shift != null) {
                val fixed = guide.rangeNotes - (guide.recommendedRemaining ?: 0)
                val all = guide.recommendedRemaining == 0
                GuideAction(when {
                    shift > 0 && all -> stringResource(Res.string.sheet_import_guide_up_all, shift)
                    shift > 0 -> stringResource(Res.string.sheet_import_guide_up, shift, fixed)
                    all -> stringResource(Res.string.sheet_import_guide_down_all, abs(shift))
                    else -> stringResource(Res.string.sheet_import_guide_down, abs(shift), fixed)
                }, onClick = onApplyRecommended)
            } else GuideAction(stringResource(Res.string.sheet_import_guide_adapt), onClick = onOpenAdaptation)
        }
        if (showReview) {
            GuideLine(stringResource(Res.string.sheet_import_guide_review, guide.reviewCells),
                onDismiss = onDismiss.takeUnless { showRange }, top = if (showRange) 6.dp else 0.dp)
            GuideAction(stringResource(Res.string.sheet_import_guide_review_go), onClick = onOpenReview)
        }
    }
}

/** 초록 상자 안의 `확인 필요: …` 한 줄. 첫 줄에만 닫기 버튼을 둔다. */
@Composable
private fun GuideLine(text: String, onDismiss: (() -> Unit)?, top: Dp = 0.dp) {
    val colors = EolssuTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = top).heightIn(min = if (onDismiss != null) 48.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            fontWeight = FontWeight.SemiBold, color = colors.accentText)
        if (onDismiss != null) IconButton(onClick = onDismiss) {
            Icon(EolssuIcons.Close, contentDescription = stringResource(Res.string.sheet_import_guide_close),
                Modifier.size(18.dp), tint = colors.textSub)
        }
    }
}

/** 초록 상자 폭을 다 쓰는 흰 버튼. 문장 전체가 한 줄에 들어가게 한다. */
@Composable
private fun GuideAction(label: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(end = 10.dp).heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp), color = colors.surface, contentColor = colors.text) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
        }
    }
}

/** `소금으로`·`단소로`·`대금으로`처럼 받침에 맞춰 붙인다. 이름을 모르면 `악기로`. */
@Composable
private fun instrumentWithRo(name: String?): String {
    val word = name?.takeIf { it.isNotBlank() } ?: return stringResource(Res.string.sheet_import_guide_instrument_fallback)
    val code = word.last().code - 0xAC00
    val batchim = if (code in 0..11171) code % 28 else 0
    return word + if (batchim == 0 || batchim == 8) "로" else "으로"
}
