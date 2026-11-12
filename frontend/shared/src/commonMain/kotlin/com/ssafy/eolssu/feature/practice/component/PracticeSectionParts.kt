package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.TintPillButton
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.SectionRepeat
import com.ssafy.eolssu.feature.practice.SectionSelectStep
import com.ssafy.eolssu.feature.practice.stepBusy
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_loop_round
import eolssu.shared.generated.resources.weak_loop_round_endless
import eolssu.shared.generated.resources.weak_select_endless
import eolssu.shared.generated.resources.weak_select_hint
import eolssu.shared.generated.resources.weak_select_hint_end
import eolssu.shared.generated.resources.weak_select_hint_ready
import eolssu.shared.generated.resources.weak_select_repeat
import eolssu.shared.generated.resources.weak_select_reset
import eolssu.shared.generated.resources.weak_select_times
import org.jetbrains.compose.resources.stringResource

/**
 * 구간 연습(S15P31E102-776)의 안내 줄: 연두 견본 · 안내(시작 정간 → 끝 정간 → 연습, 재생 중이면 ‘구간 반복 · 2/5회’) ·
 * 추천 구간이 있으면 ‘취약 구간으로’, 그 아래 ‘반복 횟수’(3회 · 5회 · 계속). 구간 연습이 꺼져 있으면 아무것도 그리지 않는다.
 */
@Composable
fun PracticeSectionBar(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier) {
    val section = state.sectionPractice ?: return
    val colors = EolssuTheme.colors
    val total = section.repeat.count
    val text = when {
        state.playing || state.stepBusy -> if (total == null) stringResource(Res.string.weak_loop_round_endless, section.round)
            else stringResource(Res.string.weak_loop_round, section.round, total)
        else -> stringResource(
            when (section.step) {
                SectionSelectStep.Start -> Res.string.weak_select_hint
                SectionSelectStep.End -> Res.string.weak_select_hint_end
                SectionSelectStep.Ready -> Res.string.weak_select_hint_ready
            },
        )
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(14.dp).background(colors.accentSoft, RoundedCornerShape(3.dp)).border(2.dp, colors.accent, RoundedCornerShape(3.dp)))
            Text(
                text,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.textSub,
            )
            if (section.recommended != null) {
                TintPillButton(stringResource(Res.string.weak_select_reset), actions.onResetSection, enabled = !state.playing && !state.stepBusy)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.weak_select_repeat), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textSub,
            )
            Row(Modifier.width(180.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionRepeat.entries.forEach { repeat ->
                    SegmentButton(
                        text = repeat.count?.let { stringResource(Res.string.weak_select_times, it) } ?: stringResource(Res.string.weak_select_endless),
                        selected = repeat == section.repeat,
                        enabled = true,
                        onClick = { actions.onSectionRepeat(repeat) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
