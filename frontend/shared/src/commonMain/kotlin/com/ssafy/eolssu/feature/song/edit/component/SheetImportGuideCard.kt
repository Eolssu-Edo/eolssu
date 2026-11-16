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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.song.edit.SheetImportGuide
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * 가져온 악보 확인 카드(S15P31E102-979). 악보 위에 두어 처음 열 때 바로 보이게 한다.
 *
 * 처음 쓰는 사람도 한 번에 읽도록 할 일을 흰 칸 하나씩에 나눈다. 각 칸의 그림은 악보 칸 표시(갈색 ↕, 빨간 !)와 같고,
 * 굵은 한 줄로 무엇이 문제인지 말한 뒤 버튼 하나로 바로 처리하게 한다. 음역 칸은 버튼 글자가 결과까지 말한다.
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
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.sheet_import_guide_title, listOf(showRange, showReview).count { it }),
                Modifier.weight(1f).padding(start = 2.dp), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = colors.accentText)
            IconButton(onClick = onDismiss) {
                Icon(EolssuIcons.Close, contentDescription = stringResource(Res.string.sheet_import_guide_close),
                    Modifier.size(18.dp), tint = colors.textSub)
            }
        }
        if (showRange) GuideTile {
            GuideHeadline(RANGE_TAG, colors.pitchError,
                stringResource(Res.string.sheet_import_guide_range, instrumentWithRo(guide.instrumentName), guide.rangeNotes))
            // 무엇을 하면 어떻게 되는지를 버튼 글자 하나로 말한다. 추천이 없으면 음역·이조 탭으로 보낸다.
            val shift = guide.recommendedShift?.takeIf { it != 0 && guide.recommendedTranspose != null }
            if (shift != null) {
                val fixed = guide.rangeNotes - (guide.recommendedRemaining ?: 0)
                val all = guide.recommendedRemaining == 0
                GuideAction(when {
                    shift > 0 && all -> stringResource(Res.string.sheet_import_guide_up_all, shift)
                    shift > 0 -> stringResource(Res.string.sheet_import_guide_up, shift, fixed)
                    all -> stringResource(Res.string.sheet_import_guide_down_all, abs(shift))
                    else -> stringResource(Res.string.sheet_import_guide_down, abs(shift), fixed)
                }, primary = true, onClick = onApplyRecommended)
            } else GuideAction(stringResource(Res.string.sheet_import_guide_adapt), primary = false, onClick = onOpenAdaptation)
        }
        if (showReview) GuideTile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GuideHeadline("!", colors.rec, stringResource(Res.string.sheet_import_guide_review, guide.reviewCells),
                        stringResource(Res.string.sheet_import_guide_review_help))
                }
                PillButton(stringResource(Res.string.sheet_import_guide_review_go), onOpenReview, compact = true, secondary = true)
            }
        }
    }
}

@Composable
private fun GuideTile(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(end = 8.dp).background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp))
        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** 악보 칸 표시와 같은 그림 + 굵은 한 줄(+ 설명 한 줄). */
@Composable
private fun GuideHeadline(badge: String, badgeColor: Color, title: String, detail: String? = null) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).background(badgeColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(badge, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = colors.text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = colors.textSub)
        }
    }
}

/** 타일 폭을 다 쓰는 버튼. 문장 전체가 한 줄에 들어가게 한다. */
@Composable
private fun GuideAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp),
        color = if (primary) colors.primary else colors.groupedBackground,
        contentColor = if (primary) colors.onPrimary else colors.text) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
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
