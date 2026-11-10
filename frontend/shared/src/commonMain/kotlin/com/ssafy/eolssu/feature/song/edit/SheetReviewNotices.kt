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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import eolssu.shared.generated.resources.sheet_review_notice_count
import eolssu.shared.generated.resources.sheet_review_notice_grid
import eolssu.shared.generated.resources.sheet_review_notice_jangdan
import eolssu.shared.generated.resources.sheet_review_notice_jangdan_mismatch
import eolssu.shared.generated.resources.sheet_review_notice_other
import eolssu.shared.generated.resources.sheet_review_notice_page
import eolssu.shared.generated.resources.sheet_review_notice_staff
import eolssu.shared.generated.resources.sheet_review_notice_text
import eolssu.shared.generated.resources.sheet_review_notice_title
import eolssu.shared.generated.resources.sheet_review_notice_transposed
import eolssu.shared.generated.resources.sheet_review_notice_transposed_unknown
import eolssu.shared.generated.resources.sheet_review_notice_unreadable
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SheetReviewNotices(notices: List<SheetReviewNotice>) {
    var open by remember(notices) { mutableStateOf(false) }
    EolssuButton(
        stringResource(Res.string.sheet_review_notice_count, notices.size),
        { open = true },
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        style = EolssuButtonStyle.Secondary,
        compact = true,
    )
    if (open) {
        EolssuDialog(
            title = stringResource(Res.string.sheet_review_notice_title),
            onDismissRequest = { open = false },
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (notice in notices) {
                    val label = notice.label()
                    Text(
                        notice.page?.let { stringResource(Res.string.sheet_review_notice_page, it, label) } ?: label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EolssuTheme.colors.text,
                    )
                }
            }
            EolssuButton(stringResource(Res.string.common_close), { open = false })
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
