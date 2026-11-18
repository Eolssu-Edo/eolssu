package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.edit.component.SheetImportGuideCard
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_completion_tuning
import eolssu.shared.generated.resources.sheet_completion_title
import eolssu.shared.generated.resources.sheet_completion_empty
import eolssu.shared.generated.resources.sheet_completion_review
import eolssu.shared.generated.resources.sheet_completion_pitch
import eolssu.shared.generated.resources.sheet_completion_structure
import eolssu.shared.generated.resources.sheet_completion_jangdan
import eolssu.shared.generated.resources.sheet_completion_fix
import eolssu.shared.generated.resources.sheet_completion_clear_jangdan
import eolssu.shared.generated.resources.sheet_completion_catalog_loading
import eolssu.shared.generated.resources.sheet_completion_catalog_failed
import eolssu.shared.generated.resources.sheet_completion_catalog_retry
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_change
import org.jetbrains.compose.resources.stringResource

/** 저장을 막는 현재 문제와 바로 실행할 수정 동작. 변환 알림 개수와 구분한다. */
@Composable
internal fun SheetCompletionNotice(
    state: SheetEditUiState,
    onFixCells: () -> Unit,
    onChangeJangdan: () -> Unit,
    onClearJangdan: () -> Unit,
    onRetryCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    showReviewSummary: Boolean = true,
    showCellAction: Boolean = true,
    onApplyRecommended: (() -> Unit)? = null,
    onOpenAdaptation: (() -> Unit)? = null,
) {
    val validation = state.completionValidation
    val rangeGuide = state.rangeFixGuide()?.takeIf { onApplyRecommended != null && onOpenAdaptation != null }
    val showCells = showReviewSummary && (validation.hasEmptyCells || validation.pendingReviewCount > 0)
    if (!showCells && validation.invalidPitchCount == 0 && !validation.hasInvalidStructure && !validation.hasJangdanMismatch && !validation.hasTuningMismatch && !validation.catalogLoading && !validation.catalogFailed) return
    Column(
        modifier.fillMaxWidth().heightIn(max = 232.dp)
            .background(EolssuTheme.colors.accentTint, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (validation.catalogLoading) {
            Text(stringResource(Res.string.sheet_completion_catalog_loading), style = MaterialTheme.typography.bodySmall)
        } else if (validation.catalogFailed) {
            Text(stringResource(Res.string.sheet_completion_catalog_failed), style = MaterialTheme.typography.bodySmall)
            EolssuButton(stringResource(Res.string.sheet_completion_catalog_retry), onRetryCatalog, style = EolssuButtonStyle.Secondary, compact = true, qaTarget = QaTarget.SHEET_COMPLETION_NOTICE_BUTTON_01)
        } else {
            Text(stringResource(Res.string.sheet_completion_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        if (showReviewSummary && validation.hasEmptyCells) {
            Text(stringResource(Res.string.sheet_completion_empty, validation.emptyCellCount), style = MaterialTheme.typography.bodySmall)
        }
        if (rangeGuide != null) {
            SheetImportGuideCard(rangeGuide, onApplyRecommended = requireNotNull(onApplyRecommended),
                onOpenAdaptation = requireNotNull(onOpenAdaptation), onMoveReview = {})
        } else {
            if (validation.hasTuningMismatch) {
                Text(stringResource(Res.string.sheet_completion_tuning), style = MaterialTheme.typography.bodySmall)
            }
            if (validation.invalidPitchCount > 0) {
                Text(stringResource(Res.string.sheet_completion_pitch, validation.invalidPitchCount), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (showReviewSummary && validation.pendingReviewCount > 0) {
            Text(stringResource(Res.string.sheet_completion_review, validation.pendingReviewCount), style = MaterialTheme.typography.bodySmall)
        }
        if (validation.hasInvalidStructure) {
            Text(stringResource(Res.string.sheet_completion_structure), style = MaterialTheme.typography.bodySmall)
        }
        // 높이로 한 번에 고치는 줄이 있어도 음을 하나씩 바꾸는 음 확인 창으로 가는 길은 남긴다.
        if (showCellAction && (showCells || validation.invalidPitchCount > 0 || validation.hasInvalidStructure)) {
            EolssuButton(stringResource(Res.string.sheet_completion_fix), onFixCells, style = EolssuButtonStyle.Secondary, compact = true, qaTarget = QaTarget.SHEET_COMPLETION_NOTICE_BUTTON_02)
        }
        if (validation.hasJangdanMismatch) {
            Text(stringResource(Res.string.sheet_completion_jangdan, jangdanLabel(state.draft.jangdanName)), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EolssuButton(stringResource(Res.string.sheetedit_cell_jangdan_change), onChangeJangdan, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, compact = true, enabled = !state.saving, qaTarget = QaTarget.SHEET_COMPLETION_NOTICE_BUTTON_03)
                EolssuButton(stringResource(Res.string.sheet_completion_clear_jangdan), onClearJangdan, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, compact = true, enabled = !state.saving, qaTarget = QaTarget.SHEET_COMPLETION_NOTICE_BUTTON_04)
            }
        }
    }
}

/**
 * 저장을 막는 낼 수 없는 음·기준음 문제를 편집 화면 위 안내와 같은 줄(악기·개수·바로 고치기 버튼)로
 * 저장 창과 음 확인 창에 보여 준다(S15P31E102-1085). 다시 볼 곳은 저장 창의 다른 줄이 맡으므로 뺀다.
 */
internal fun SheetEditUiState.rangeFixGuide(): SheetImportGuide? {
    val validation = completionValidation
    if (validation.invalidPitchCount == 0 && !validation.hasTuningMismatch) return null
    return importGuide?.takeIf { it.rangeNotes > 0 || it.recommendedTranspose != null }
        ?.copy(reviewCells = 0, reviewPosition = null)
}
