package com.ssafy.eolssu.feature.song.edit

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
import eolssu.shared.generated.resources.Res
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
) {
    val validation = state.completionValidation
    val showCells = showReviewSummary && (validation.hasEmptyCells || validation.pendingReviewCount > 0)
    if (!showCells && validation.invalidPitchCount == 0 && !validation.hasInvalidStructure && !validation.hasJangdanMismatch && !validation.catalogLoading && !validation.catalogFailed) return
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
            EolssuButton(stringResource(Res.string.sheet_completion_catalog_retry), onRetryCatalog, style = EolssuButtonStyle.Secondary, compact = true)
        } else {
            Text(stringResource(Res.string.sheet_completion_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        if (showReviewSummary && validation.hasEmptyCells) {
            Text(stringResource(Res.string.sheet_completion_empty, validation.emptyCellCount), style = MaterialTheme.typography.bodySmall)
        }
        if (validation.invalidPitchCount > 0) {
            Text(stringResource(Res.string.sheet_completion_pitch, validation.invalidPitchCount), style = MaterialTheme.typography.bodySmall)
        }
        if (showReviewSummary && validation.pendingReviewCount > 0) {
            Text(stringResource(Res.string.sheet_completion_review, validation.pendingReviewCount), style = MaterialTheme.typography.bodySmall)
        }
        if (validation.hasInvalidStructure) {
            Text(stringResource(Res.string.sheet_completion_structure), style = MaterialTheme.typography.bodySmall)
        }
        if (showCellAction && (showCells || validation.invalidPitchCount > 0 || validation.hasInvalidStructure)) {
            EolssuButton(stringResource(Res.string.sheet_completion_fix), onFixCells, style = EolssuButtonStyle.Secondary, compact = true)
        }
        if (validation.hasJangdanMismatch) {
            Text(stringResource(Res.string.sheet_completion_jangdan, jangdanLabel(state.draft.jangdanName)), style = MaterialTheme.typography.bodySmall)
            SheetJangdanTimingText(state.draft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EolssuButton(stringResource(Res.string.sheetedit_cell_jangdan_change), onChangeJangdan, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, compact = true, enabled = !state.saving)
                EolssuButton(stringResource(Res.string.sheet_completion_clear_jangdan), onClearJangdan, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, compact = true, enabled = !state.saving)
            }
        }
    }
}
