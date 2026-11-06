package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.result.component.MetricCards
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import eolssu.shared.generated.resources.record_result_practice
import eolssu.shared.generated.resources.record_result_title
import eolssu.shared.generated.resources.record_result_recording
import eolssu.shared.generated.resources.record_result_info
import eolssu.shared.generated.resources.record_result_failed
import eolssu.shared.generated.resources.growth_retry
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
 * 이전 연주 결과는 두 설명 카드를 생략해 악보 공간을 확보하고, 하단에 연습·기록 버튼을 나란히 둔다.
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
    historical: Boolean = false,
    onOpenRecording: (() -> Unit)? = null,
) {
    val colors = EolssuTheme.colors
    val song = state.song
    val result = state.result
    EolssuScaffold(
        containerColor = colors.surface,
        topBar = {
            EolssuTopBar(
                stringResource(if (historical) Res.string.record_result_title else Res.string.weak_errors_title),
                onBack = onBack,
                subtitle = if (song != null && result != null) {
                    stringResource(if (result.sample) Res.string.weak_errors_subtitle_sample else Res.string.weak_errors_subtitle, state.songTitle.orEmpty(), result.total)
                } else {
                    null
                },
            )
        },
        bottomBar = if ((song != null && result != null) || onOpenRecording != null) {
            {
                if (historical && onOpenRecording != null) {
                    RecordResultActions(
                        canPractice = song != null && result != null,
                        onPractice = onSelectSection,
                        onOpenRecording = onOpenRecording,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (song != null && result != null) {
                            EolssuButton(stringResource(Res.string.weak_errors_go_select), onSelectSection, icon = EolssuIcons.Target)
                        }
                        if (onOpenRecording != null) {
                            EolssuButton(stringResource(Res.string.record_result_recording), onOpenRecording, style = EolssuButtonStyle.Secondary)
                        }
                    }
                }
            }
        } else {
            null
        },
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 짧은 창에서는 요약 때문에 악보가 0 높이가 되지 않도록 본문 전체를 스크롤한다.
            // 악보의 각·정간 크기와 오류 꾸밈은 그대로 사용한다.
            val scrollPage = historical && maxHeight < 360.dp
            val pageScroll = rememberScrollState()
            val scoreScroll = rememberScrollState()
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)
                .then(if (scrollPage) Modifier.verticalScroll(pageScroll) else Modifier)) {
                if (historical) {
                    Text(
                        stringResource(Res.string.record_result_info, monthDayLabel(result.recordedAt), timeLabel(result.recordedAt), result.bpm, result.recordedSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    MetricCards(result, weak = emptySet(), modifier = Modifier.padding(vertical = 8.dp))
                }
                ErrorFilterChips(result, state.filter, onSelectFilter, Modifier.padding(top = 4.dp, bottom = 10.dp), hideEmptyMissed = true)
                if (!historical && section != null) {
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
                    modifier = (if (scrollPage) Modifier else Modifier.weight(1f).verticalScroll(scoreScroll)).padding(top = 10.dp),
                    metrics = JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip),
                    look = JeongganboLook.practice(big = false),
                    errors = result.visibleErrors(state.filter),
                    selectedCell = shown?.let { it.column to it.row },
                    onUnitClick = { onSelectCell(ScoreCellPosition(it.column, it.row)) },
                    sectionColumns = section?.fullColumns(song.score),
                )
                if (!historical && shown != null) {
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

/** 같은 너비의 독립 버튼 두 개. 사이에 간격을 두고 각 버튼의 둥근 테두리를 표시한다. */
@Composable
private fun RecordResultActions(canPractice: Boolean, onPractice: () -> Unit, onOpenRecording: () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onPractice,
            enabled = canPractice,
            modifier = Modifier.weight(1f),
            shape = shape,
            border = BorderStroke(1.dp, if (canPractice) colors.primary else colors.line),
            color = if (canPractice) colors.primary else colors.surface,
            contentColor = if (canPractice) colors.onPrimary else colors.textDisabled,
        ) {
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.record_result_practice), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
        Surface(
            onClick = onOpenRecording,
            modifier = Modifier.weight(1f),
            shape = shape,
            border = BorderStroke(1.dp, colors.line),
            color = colors.surface,
            contentColor = colors.text,
        ) {
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.record_result_recording), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}
