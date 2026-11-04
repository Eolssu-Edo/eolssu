package com.ssafy.eolssu.core.ui.score

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_cell_where
import eolssu.shared.generated.resources.weak_section_cells
import eolssu.shared.generated.resources.weak_section_column
import eolssu.shared.generated.resources.weak_section_columns
import org.jetbrains.compose.resources.stringResource

/** ‘3각 4정간’. 번호는 0부터 받는다. */
@Composable
fun scorePositionLabel(position: ScorePosition): String = stringResource(Res.string.weak_cell_where, position.column + 1, position.row + 1)

/** 구간 이름: 각 전체면 ‘3–4각’(한 각이면 ‘3각’), 아니면 ‘3각 1정간 – 4각 5정간’. */
@Composable
fun scoreSectionLabel(section: ScoreSection, score: Score): String {
    val full = section.fullColumns(score)
    return when {
        full == section.columns && full.first == full.last -> stringResource(Res.string.weak_section_column, full.first + 1)
        full == section.columns -> stringResource(Res.string.weak_section_columns, full.first + 1, full.last + 1)
        else -> stringResource(
            Res.string.weak_section_cells,
            section.start.column + 1, section.start.row + 1, section.end.column + 1, section.end.row + 1,
        )
    }
}

/** 처음·끝을 정간까지 적은 구간 이름(‘3각 1정간 – 4각 9정간’). */
@Composable
fun scoreSectionCellsLabel(section: ScoreSection): String = stringResource(
    Res.string.weak_section_cells,
    section.start.column + 1, section.start.row + 1, section.end.column + 1, section.end.row + 1,
)
