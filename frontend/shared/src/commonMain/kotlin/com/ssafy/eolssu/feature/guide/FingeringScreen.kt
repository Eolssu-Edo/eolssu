package com.ssafy.eolssu.feature.guide

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.feature.guide.component.GuideText
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.layout.FingeringCompactLayout
import com.ssafy.eolssu.feature.guide.layout.FingeringExpandedLayout
import com.ssafy.eolssu.feature.guide.layout.FingeringMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_fingering_title
import eolssu.shared.generated.resources.guide_catalog_fingering_title
import org.jetbrains.compose.resources.stringResource

/** 운지법 보기의 동작. 배치마다 같은 동작을 넘긴다. */
internal class FingeringActions(
    val onToggleDropdown: () -> Unit,
    val onDismissDropdown: () -> Unit,
    val onSelectInstrument: (GuideInstrument) -> Unit,
    val onSelectBlow: (Blow) -> Unit,
    val onListen: (FingeringNote) -> Unit,
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
    onListen: (FingeringNote) -> Unit,
    onOpenCatalog: (() -> Unit)? = null,
) {
    val actions = FingeringActions(onToggleDropdown, onDismissDropdown, onSelectInstrument, onSelectBlow, onListen)
    EolssuScaffold(
        topBar = { GuideTopBar(stringResource(Res.string.guide_fingering_title), onBack = onBack, widthClass = widthClass) },
        snackbarHostState = snackbarHostState,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (widthClass) {
                    WindowWidthClass.Compact -> FingeringCompactLayout(state, actions)
                    WindowWidthClass.Medium -> FingeringMediumLayout(state, actions)
                    WindowWidthClass.Expanded -> FingeringExpandedLayout(state, actions)
                }
            }
            if (onOpenCatalog != null) EolssuButton(stringResource(Res.string.guide_catalog_fingering_title), onOpenCatalog,
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = EolssuButtonStyle.Secondary)
        }
    }
}

/** 악기별 안내 문구(`.fg-note`, 13sp · 줄 간격 1.45). */
@Composable
internal fun FingeringInstrumentNote(instrument: GuideInstrument, modifier: Modifier = Modifier) {
    GuideText(
        instrument.note,
        modifier,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.85.sp),
        color = EolssuTheme.colors.textMuted,
    )
}

/** 운지표 출처(`.fg-source`). */
@Composable
internal fun FingeringSource(instrument: GuideInstrument, modifier: Modifier = Modifier) {
    GuideText(
        instrument.source,
        modifier,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.5.sp),
        color = EolssuTheme.colors.textMuted,
    )
}
