package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.feature.guide.layout.FingeringCompactLayout
import com.ssafy.eolssu.feature.guide.layout.FingeringExpandedLayout
import com.ssafy.eolssu.feature.guide.layout.FingeringMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_fingering_title
import eolssu.shared.generated.resources.guide_instruments_failed
import eolssu.shared.generated.resources.guide_instruments_empty
import eolssu.shared.generated.resources.guide_catalog_retry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.Blow

/** 운지법 보기 본문의 동작. 배치마다 같은 동작을 넘긴다. 악기 선택은 상단 바에 있어 여기에 두지 않는다. */
internal class FingeringActions(
    val onSelectBlow: (Blow) -> Unit,
)

/**
 * GUIDE-01 운지법 보기. 악기·취법을 고르면 그 취법의 모든 음을 낮은 음부터 운지 그림과 함께 보여준다.
 * 악기 선택은 곡 고르기 화면처럼 상단 바 오른쪽 알약에 두고, 제목은 알약 너비와 관계없이 가운데에 둔다(S15P31E102-1245).
 * 본문은 휴대폰은 한 줄 목록, 태블릿 세로는 위 취법 탭 + 카드 두 칸, 태블릿 가로는 왼쪽 취법 칸 + 오른쪽 카드 격자로 보여준다.
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
    val actions = FingeringActions(onSelectBlow)
    EolssuScaffold(
        topBar = {
            GuideTopBar(stringResource(Res.string.guide_fingering_title), onBack = onBack, widthClass = widthClass, shrinkRight = true) {
                // 고를 악기가 하나도 없으면 본문 안내만 두고 알약은 숨긴다.
                if (!state.hasNoInstruments) {
                    InstrumentDropdown(
                        options = state.instrumentOptions,
                        selected = state.instrument,
                        open = state.isDropdownOpen,
                        onToggle = onToggleDropdown,
                        onSelect = onSelectInstrument,
                        onDismiss = onDismissDropdown,
                        enabled = state.hasSelectedInstrument,
                        loading = state.isInstrumentListLoading,
                    )
                }
            }
        },
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.groupedBackground,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        // 운지 카드는 앱 안의 데이터로 그리므로 악기 목록을 기다리지 않고 처음 악기로 바로 보여준다(S15P31E102-1036).
        // 목록을 받는 동안에는 악기 고르기 칸만 막고, 실패하면 화면 위에 안내와 다시 시도를 둔다.
        if (state.hasNoInstruments) {
            InstrumentListNotice(Res.string.guide_instruments_empty, onRetryInstruments, Modifier.padding(20.dp))
        } else Column(Modifier.fillMaxSize()) {
            if (state.instrumentListState == ReferenceLoadState.Failed) {
                InstrumentListNotice(
                    Res.string.guide_instruments_failed,
                    onRetryInstruments,
                    Modifier.padding(start = widthClass.guideSidePadding, end = widthClass.guideSidePadding, top = 4.dp, bottom = 12.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                when (widthClass) {
                    WindowWidthClass.Compact -> FingeringCompactLayout(state, actions, noteAudioContent)
                    WindowWidthClass.Medium -> FingeringMediumLayout(state, actions, noteAudioContent)
                    WindowWidthClass.Expanded -> FingeringExpandedLayout(state, actions, noteAudioContent)
                }
            }
        }
    }
}

/** 악기 목록을 받지 못했거나 비었을 때의 안내와 다시 시도 버튼. */
@Composable
private fun InstrumentListNotice(message: StringResource, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium, color = EolssuTheme.colors.textMuted)
        EolssuButton(stringResource(Res.string.guide_catalog_retry), onRetry, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.FINGERING_SCREEN_BUTTON_01)
    }
}
