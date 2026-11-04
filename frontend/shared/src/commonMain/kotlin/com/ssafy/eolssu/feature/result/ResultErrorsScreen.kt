package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.result.component.ErrorFilterChips
import com.ssafy.eolssu.feature.result.component.SelectedCellCard
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_errors_go_select
import eolssu.shared.generated.resources.weak_errors_section_body
import eolssu.shared.generated.resources.weak_errors_section_label
import eolssu.shared.generated.resources.weak_errors_subtitle
import eolssu.shared.generated.resources.weak_errors_subtitle_sample
import eolssu.shared.generated.resources.weak_errors_title
import eolssu.shared.generated.resources.weak_load_missing
import org.jetbrains.compose.resources.stringResource

/**
 * RESULT-03 오류 위치(2차 `ResultErrors`): 종류 필터 칩 · 취약 구간 크림 카드 · 곡 전체 정간보(연습 화면과 같은 부품, 틀린 정간은 종류별 색·아이콘,
 * 취약 구간 각은 연두 테두리·옅은 바깥 띠·크림 머리) · 고른 정간 설명 · ‘취약 구간 골라서 연습하기’.
 * 태블릿은 휴대폰 배치를 가운데에 둔다.
 */
@Composable
fun ResultErrorsScreen(
    state: ResultErrorsUiState,
    onBack: () -> Unit,
    onSelectFilter: (ErrorFilter) -> Unit,
    onSelectCell: (ScoreCellPosition) -> Unit,
    onSelectSection: () -> Unit,
) {
    val colors = EolssuTheme.colors
    val song = state.song
    val result = state.result
    EolssuScaffold(
        containerColor = colors.surface,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.weak_errors_title),
                onBack = onBack,
                subtitle = if (song != null && result != null) {
                    stringResource(if (result.sample) Res.string.weak_errors_subtitle_sample else Res.string.weak_errors_subtitle, song.title, result.total)
                } else {
                    null
                },
            )
        },
        bottomBar = if (song != null && result != null) {
            { EolssuButton(stringResource(Res.string.weak_errors_go_select), onSelectSection, icon = EolssuIcons.Target) }
        } else {
            null
        },
    ) {
        if (song == null || result == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(Res.string.weak_load_missing))
            }
            return@EolssuScaffold
        }
        val section = state.section
        val longestStrip = remember(song) { scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 1 }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            ErrorFilterChips(result, state.filter, onSelectFilter, Modifier.padding(top = 4.dp, bottom = 10.dp), hideEmptyMissed = true)
            if (section != null) {
                Column(Modifier.fillMaxWidth().background(colors.accentTint, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(stringResource(Res.string.weak_errors_section_label), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                    Text(
                        scoreSectionLabel(section, song.score),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        stringResource(Res.string.weak_errors_section_body, result.errors.size, state.sectionErrorCount),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    )
                }
            }
            val shown = state.shownCell
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).padding(top = 10.dp).verticalScroll(rememberScrollState()),
                metrics = JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip),
                look = JeongganboLook.practice(big = false),
                errors = result.visibleErrors(state.filter),
                selectedCell = shown?.let { it.column to it.row },
                onUnitClick = { onSelectCell(ScoreCellPosition(it.column, it.row)) },
                sectionColumns = section?.fullColumns(song.score),
            )
            if (shown != null) {
                SelectedCellCard(
                    song, result, shown,
                    Modifier.padding(top = 6.dp, bottom = 8.dp).fillMaxWidth()
                        .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    markFallback = colors.surface,
                )
            }
        }
    }
}
