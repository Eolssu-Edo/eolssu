package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.score.errorCount
import com.ssafy.eolssu.core.ui.score.visibleErrors
import com.ssafy.eolssu.core.ui.score.visibleErrorKinds
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors

import com.ssafy.eolssu.core.ui.score.ErrorFilterChips
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
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
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.practice.component.PracticeSplitLineFill
import com.ssafy.eolssu.feature.practice.component.PracticeStackGap
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.feature.result.component.weakSectionScoreHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
 * RESULT-03 오류 위치(2차 `ResultErrors`): 종류 필터 칩 · 취약 구간 크림 카드 · 곡 전체 정간보(악보 연습·AI 연주 평가와 같은 배치,
 * 가로 스크롤, 틀린 정간은 종류별 색·아이콘, 취약 구간 각은 연두 테두리·옅은 바깥 띠·크림 머리) · ‘취약 구간 골라서 연습하기’.
 * 태블릿은 휴대폰 배치를 가운데에 둔다.
 */
@Composable
fun ResultErrorsScreen(
    state: ResultErrorsUiState,
    onBack: () -> Unit,
    onSelectFilter: (ErrorFilter) -> Unit,
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 페이지는 세로로, 악보는 그 안에서 가로로만 움직인다. 악보 영역은 남은 높이를 채우되
            // 각을 두 줄로 나누지 않고 연습·평가 최소 정간보다 작아지지 않게 하며, 모자라면 페이지 전체가 세로로 스크롤된다.
            val pageHeight = maxHeight
            val density = LocalDensity.current
            var headerHeight by remember { mutableStateOf<Dp?>(null) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
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
                                stringResource(Res.string.weak_errors_section_body, result.errorCount(ErrorFilter.All), state.sectionErrorCount),
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            )
                        }
                    }
                }
                // 머리 높이를 잰 뒤에 배치해 묶음 수가 첫 프레임과 달라지지 않게 한다.
                val header = headerHeight
                if (header != null && song.score.columns.isNotEmpty()) {
                    ErrorDetailScore(
                        song = song,
                        result = result,
                        filter = state.filter,
                        section = section,
                        availableHeight = pageHeight - header - ScoreAreaPadding * 2,
                        modifier = Modifier.fillMaxWidth().padding(vertical = ScoreAreaPadding),
                    )
                }
            }
        }
    }
}

/** 정간보와 위의 필터·취약 구간 카드, 아래 버튼 사이 간격. */
private val ScoreAreaPadding = 12.dp

/**
 * 곡 전체 정간보. 악보 연습·AI 연주 평가 휴대폰 악보와 같은 크기·모양(연습 보드 [JeongganboMetrics.practiceBoard] ·
 * [practiceScorePacking] · [JeongganboLook.practice])으로 그린다. 연습 화면과 달리 긴 각도 두 줄로 나누지 않고 한 줄에 두며
 * (S15P31E102-740, 화면보다 길면 페이지를 세로로 스크롤), 짧은 각은 위아래로 쌓는다. 악보 전체는 가로로 넘긴다(처음에는 오른쪽 끝의 첫 각).
 * 정간 크기는 연습 최소 정간 아래로 줄이지 않는다([weakSectionScoreHeight]).
 * 정보 표시용이라 정간을 누르거나 고를 수 없다. 틀린 정간은 [filter] 종류만 색·아이콘으로, 취약 구간 각은 연두 테두리로 표시한다.
 */
@Composable
private fun ErrorDetailScore(
    song: Song,
    result: EvaluationResult,
    filter: ErrorFilter,
    section: ScoreSection?,
    availableHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val longestStrip = remember(song) { scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 0 }
    val base = remember(longestStrip) {
        JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip).copy(splitLineFill = PracticeSplitLineFill)
    }
    val minimum = remember(song, base, fontScale) { weakSectionScoreHeight(song, base, fontScale, oneLine = true) }
    val height = maxOf(availableHeight, minimum)
    val packing = remember(song, base, height, fontScale) { practiceScorePacking(song.score, base, height, fontScale, song.jangdan) }
    JeongganboView(
        song = song,
        modifier = modifier.height(height).clipToBounds(),
        metrics = packing.metrics,
        scrollState = rememberScrollState(),
        errors = result.visibleErrors(filter),
        errorKinds = result.visibleErrorKinds(filter),
        unitErrors = result.visibleUnitErrors(song, filter),
        look = JeongganboLook.practice(big = false),
        columnsPerStack = packing.columnsPerStack,
        splitColumns = packing.splitColumns,
        stackGap = PracticeStackGap,
        cellHeights = packing.cellHeights,
        stackRowHeights = packing.stackRowHeights,
        sectionColumns = section?.fullColumns(song.score),
    )
}
