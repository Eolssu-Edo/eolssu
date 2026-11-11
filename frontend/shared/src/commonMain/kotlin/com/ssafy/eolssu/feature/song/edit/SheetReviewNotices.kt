package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_review_notice_grid
import eolssu.shared.generated.resources.sheet_review_notice_jangdan
import eolssu.shared.generated.resources.sheet_review_notice_jangdan_mismatch
import eolssu.shared.generated.resources.sheet_review_notice_other
import eolssu.shared.generated.resources.sheet_review_notice_page
import eolssu.shared.generated.resources.sheet_review_notice_staff
import eolssu.shared.generated.resources.sheet_review_notice_text
import eolssu.shared.generated.resources.sheet_review_notice_transposed
import eolssu.shared.generated.resources.sheet_review_notice_transposed_unknown
import eolssu.shared.generated.resources.sheet_review_notice_unreadable
import org.jetbrains.compose.resources.stringResource

/** 변환 당시 참고 정보는 저장 화면에 바로 표시한다. 현재 오류는 SheetCompletionNotice에서 다룬다. */
@Composable
internal fun SheetConversionNotes(notices: List<SheetReviewNotice>) {
    val information = notices.filterNot { it.type == "JANGDAN_MISMATCH" }
    if (information.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        information.forEach { notice ->
            val label = notice.label()
            Text(
                notice.page?.let { stringResource(Res.string.sheet_review_notice_page, it, label) } ?: label,
                style = MaterialTheme.typography.bodySmall,
                color = EolssuTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SheetReviewNotice.label(): String = when (type) {
    "UNREADABLE" -> stringResource(
        when (reason) {
            "NO_GRID" -> Res.string.sheet_review_notice_grid
            "NO_STAFF" -> Res.string.sheet_review_notice_staff
            "NO_TEXT" -> Res.string.sheet_review_notice_text
            else -> Res.string.sheet_review_notice_unreadable
        },
    )
    "JANGDAN_GUESSED" -> stringResource(Res.string.sheet_review_notice_jangdan, jangdanLabel(jangdan.orEmpty()))
    "JANGDAN_MISMATCH" -> stringResource(Res.string.sheet_review_notice_jangdan_mismatch)
    "TRANSPOSED" -> semitones?.let { stringResource(Res.string.sheet_review_notice_transposed, if (it > 0) "+$it" else it.toString()) }
        ?: stringResource(Res.string.sheet_review_notice_transposed_unknown)
    else -> stringResource(Res.string.sheet_review_notice_other)
}
