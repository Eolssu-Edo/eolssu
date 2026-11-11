package com.ssafy.eolssu.feature.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.layout.FingeringCompactLayout
import com.ssafy.eolssu.feature.guide.layout.FingeringExpandedLayout
import com.ssafy.eolssu.feature.guide.layout.FingeringMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_fingering_title
import eolssu.shared.generated.resources.guide_instruments_loading
import eolssu.shared.generated.resources.guide_instruments_failed
import eolssu.shared.generated.resources.guide_instruments_empty
import eolssu.shared.generated.resources.guide_catalog_retry
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.Blow

/** 운지법 보기의 동작. 배치마다 같은 동작을 넘긴다. */
internal class FingeringActions(
    val onToggleDropdown: () -> Unit,
    val onDismissDropdown: () -> Unit,
    val onSelectInstrument: (GuideInstrument) -> Unit,
    val onSelectBlow: (Blow) -> Unit,
)

/**
 * GUIDE-01 운지법 보기. 악기·취법을 고르면 그 취법의 모든 음을 낮은 음부터 운지 그림과 함께 보여준다.
 * 같은 상태를 휴대폰은 한 줄 목록, 태블릿 세로는 위 조작 + 카드 두 칸, 태블릿 가로는 왼쪽 조작 칸 + 오른쪽 카드 격자로 보여준다.
 */
@Composable
fun FingeringScreen(
    state: FingeringUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleDropdown: () -> Unit,
    onDismissDropdown: () -> Unit,
    onSelectInstrument: (GuideInstrument) -> Unit,
    onSelectBlow: (Blow) -> Unit,
    onRetryInstruments: () -> Unit,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
) {
    val actions = FingeringActions(onToggleDropdown, onDismissDropdown, onSelectInstrument, onSelectBlow)
    EolssuScaffold(
        topBar = { GuideTopBar(stringResource(Res.string.guide_fingering_title), onBack = onBack, widthClass = widthClass) },
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.groupedBackground,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        if (!state.hasSelectedInstrument) {
            val message = when (state.instrumentListState) {
                ReferenceLoadState.Loading -> Res.string.guide_instruments_loading
                ReferenceLoadState.Failed -> Res.string.guide_instruments_failed
                else -> Res.string.guide_instruments_empty
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium, color = EolssuTheme.colors.textMuted)
                if (state.instrumentListState != ReferenceLoadState.Loading) {
                    EolssuButton(stringResource(Res.string.guide_catalog_retry), onRetryInstruments, style = EolssuButtonStyle.Secondary)
                }
            }
        } else when (widthClass) {
            WindowWidthClass.Compact -> FingeringCompactLayout(state, actions, noteAudioContent)
            WindowWidthClass.Medium -> FingeringMediumLayout(state, actions, noteAudioContent)
            WindowWidthClass.Expanded -> FingeringExpandedLayout(state, actions, noteAudioContent)
        }
    }
}
