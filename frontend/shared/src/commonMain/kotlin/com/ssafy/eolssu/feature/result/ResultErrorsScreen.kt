package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.score.errorCount
import com.ssafy.eolssu.core.ui.score.visibleErrors
import com.ssafy.eolssu.core.ui.score.visibleErrorKinds
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors

import com.ssafy.eolssu.core.ui.score.ErrorFilterChips
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.ssafy.eolssu.core.model.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.record_result_failed
import eolssu.shared.generated.resources.growth_retry
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.score.errorScoreCellHeights
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.core.ui.score.scoreStrips
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
    onRetry: () -> Unit = {},
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
                    stringResource(if (result.sample) Res.string.weak_errors_subtitle_sample else Res.string.weak_errors_subtitle, state.songTitle.orEmpty(), result.total)
                } else {
                    null
                },
            )
        },
        bottomBar = if (song != null && result != null) {
            { EolssuButton(stringResource(Res.string.weak_errors_go_select), onSelectSection, icon = EolssuIcons.Target) }
        } else null,
    ) {
        if (song == null || result == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(if (state.failed) Res.string.record_result_failed else Res.string.weak_load_missing))
                        EolssuButton(stringResource(Res.string.growth_retry), onRetry, small = true)
                    }
                }
            }
            return@EolssuScaffold
        }
        val section = state.section
        val longestStrip = remember(song) { scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 1 }
        val scoreMetrics = remember(song) {
            JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip)
        }
        // 소박으로 나뉜 정간은 늘려 율명이 잘리지 않게 하고, 정간 수가 같은 각끼리 위아래 끝을 맞춘다.
        val cellHeights = remember(song, scoreMetrics) { errorScoreCellHeights(song, scoreMetrics) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 짧은 창에서는 요약 때문에 악보가 0 높이가 되지 않도록 본문 전체를 스크롤한다.
            // 악보의 각·정간 크기와 오류 꾸밈은 그대로 사용한다.
            val scrollPage = maxHeight < 360.dp
            val pageScroll = rememberScrollState()
            val scoreScroll = rememberScrollState()
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)
                .then(if (scrollPage) Modifier.verticalScroll(pageScroll) else Modifier)) {
                ErrorFilterChips(result, state.filter, onSelectFilter, Modifier.padding(top = 4.dp, bottom = 10.dp), hideEmptyMissed = true, singleLine = false)
                if (section != null) {
                    Column(Modifier.fillMaxWidth().background(colors.accentTint, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(stringResource(Res.string.weak_errors_section_label), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                        Text(
                            scoreSectionLabel(section, song.score),
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            stringResource(Res.string.weak_errors_section_body, result.errorCount(ErrorFilter.All), state.sectionErrorCount),
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        )
                    }
                }
                val shown = state.shownCell
                JeongganboView(
                    song = song,
                    modifier = (if (scrollPage) Modifier else Modifier.weight(1f).verticalScroll(scoreScroll)).padding(top = 10.dp),
                    metrics = scoreMetrics,
                    look = JeongganboLook.practice(big = false).copy(softDaegang = true),
                    cellHeights = cellHeights,
                    errors = result.visibleErrors(state.filter),
                    errorKinds = result.visibleErrorKinds(state.filter),
                    unitErrors = result.visibleUnitErrors(song, state.filter),
                    selectedCell = shown?.let { it.column to it.row },
                    onUnitClick = { onSelectCell(ScoreCellPosition(it.column, it.row, it.key)) },
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
}
