package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuStartTopBar
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.ScoreSummaryCard
import com.ssafy.eolssu.core.ui.component.TintPillButton
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.ui.score.ErrorKindSegments
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.core.ui.score.visibleErrorKinds
import com.ssafy.eolssu.core.ui.score.visibleErrors
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors
import com.ssafy.eolssu.feature.practice.component.PracticeSplitLineFill
import com.ssafy.eolssu.feature.practice.component.PracticeStackGap
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.feature.practice.toSection
import com.ssafy.eolssu.feature.result.component.LiteBadge
import com.ssafy.eolssu.feature.result.component.weakSectionScoreHeight
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_home
import eolssu.shared.generated.resources.result_memo_label
import eolssu.shared.generated.resources.result_memo_placeholder
import eolssu.shared.generated.resources.result_other_song_more
import eolssu.shared.generated.resources.result_practice_again
import eolssu.shared.generated.resources.result_sample_badge
import eolssu.shared.generated.resources.result_save
import eolssu.shared.generated.resources.result_save_confirm
import eolssu.shared.generated.resources.result_saved_button
import eolssu.shared.generated.resources.result_saved_meta
import eolssu.shared.generated.resources.result_saved_title
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.result_subtitle
import eolssu.shared.generated.resources.result_title
import eolssu.shared.generated.resources.result_weak_line
import org.jetbrains.compose.resources.stringResource

/** 결과 화면에서 부르는 동작. */
class ResultActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onConfirmSave: () -> Unit,
    val onPracticeAgain: () -> Unit,
    val onOtherSong: () -> Unit,
    val onHome: () -> Unit,
    val onMemoChange: (String) -> Unit,
    val onCloseSheet: () -> Unit,
    val onSelectFilter: (ErrorFilter) -> Unit = {},
    val onRetrySong: () -> Unit = {},
    val onRetryResult: () -> Unit = {},
)

/**
 * 연주 결과(S15P31E102-753, 시안 연주 평가 결과 · D안 바탕). 요약(RESULT-02)과 틀린 부분 상세(RESULT-03)를 한 화면에 둔다:
 * 제목 ‘곡 · 악기 · 난이도’와 오른쪽 위 홈 · 점수 상자(일원상 총점 · 항목 막대 · ‘같은 곡 다시 연습’) · 5칸 종류 필터 ·
 * 취약 구간 줄 · 곡 전체 정간보(보기 전용) · ‘다른 곡 고르기’, 아래에 ‘연습 기록 저장’ · ‘취약 구간 연습’.
 * 태블릿은 같은 배치를 가운데에 넓게 둔다.
 */
@Composable
fun ResultScreen(
    state: ResultUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: ResultActions,
) {
    if (state.result == null || state.song == null) {
        EolssuScaffold(topBar = { EolssuTopBar(stringResource(Res.string.result_title), onBack = actions.onBack) }) {
            SongLookupContent(
                if (state.result == null) state.resultLookup else state.songLookup,
                if (state.result == null) actions.onRetryResult else actions.onRetrySong,
                actions.onBack,
                resultLookup = state.result == null && state.scorePreview == null,
                header = state.scorePreview?.let { preview ->
                    {
                        Column(Modifier.widthIn(max = CompactContentMaxWidth).fillMaxWidth()) {
                            ResultScoreCard(preview, actions)
                        }
                    }
                },
            )
        }
        return
    }
    val result = state.result
    val song = state.song
    val maxWidth = if (widthClass == WindowWidthClass.Compact) CompactContentMaxWidth else WindowBreakpoints.ContentMaxWidth
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.surface,
        contentMaxWidth = maxWidth,
        topBar = {
            EolssuStartTopBar(
                stringResource(Res.string.result_subtitle, song.title, songInstrumentLabel(song), songLevelLabel(song.level)),
                onBack = null,
                subtitle = stringResource(Res.string.result_title),
                maxWidth = maxWidth,
                right = { EolssuIconButton(EolssuIcons.House, stringResource(Res.string.result_home), actions.onHome, bordered = false) },
            )
        },
        bottomBar = {
            // 취약 구간 연습은 연습 기록의 ‘구간 연습’에서만 들어간다(S15P31E102-794).
            EolssuButton(
                stringResource(if (state.savedRecord != null) Res.string.result_saved_button else Res.string.result_save),
                actions.onSave, Modifier.fillMaxWidth(),
                icon = if (state.savedRecord != null) EolssuIcons.Check else EolssuIcons.Save, compact = true,
            )
        },
    ) {
        ResultContent(state, result, song, actions)
    }
    when (state.saveSheet) {
        SaveSheet.Form -> SaveFormSheet(state, result, song, actions)
        SaveSheet.Saved -> SavedSheet(actions.onCloseSheet)
        null -> Unit
    }
}

/** 점수 상자: 일원상 총점 · 항목 막대 · ‘같은 곡 다시 연습’. 예시 결과면 버튼 왼쪽에 표시한다. */
@Composable
private fun ResultScoreCard(result: EvaluationResult, actions: ResultActions, modifier: Modifier = Modifier) {
    ScoreSummaryCard(
        total = result.total,
        metrics = result.metrics,
        modifier = modifier,
        action = { TintPillButton(stringResource(Res.string.result_practice_again), actions.onPracticeAgain) },
        extra = if (result.sample) {
            { LiteBadge(stringResource(Res.string.result_sample_badge)) }
        } else null,
    )
}

/**
 * 본문. 페이지는 세로로, 악보는 그 안에서 가로로 움직인다. 악보 영역은 남은 높이를 채우되 각을 두 줄로 나누지 않고
 * 연습·평가 최소 정간보다 작아지지 않게 하며, 모자라면 페이지 전체가 세로로 스크롤된다.
 */
@Composable
private fun ResultContent(state: ResultUiState, result: EvaluationResult, song: Song, actions: ResultActions) {
    val colors = EolssuTheme.colors
    val section = remember(state.weakSection, song) { state.weakSection?.toSection(song.score) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf<Dp?>(null) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                ResultScoreCard(result, actions, Modifier.padding(top = 2.dp, bottom = 8.dp))
                ErrorKindSegments(result, state.filter, actions.onSelectFilter, Modifier.padding(bottom = 6.dp))
                val pick = state.weakSection
                if (section != null && pick != null) {
                    WeakSectionLine(scoreSectionLabel(section, song.score), pick.errorCount)
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
                    availableHeight = pageHeight - header - OtherSongHeight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(Res.string.result_other_song_more),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp, bottom = 10.dp)
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = actions.onOtherSong)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textSub,
            )
        }
    }
}

/** 악보 아래 ‘다른 곡 고르기’ 줄 높이(위 2 + 48 + 아래 10). */
private val OtherSongHeight = 60.dp

/** ‘취약 구간 2–3각 · 틀린 정간 2개’: 14dp 연두 테두리 견본과 12sp 문구. 각 이름만 굵게. */
@Composable
private fun WeakSectionLine(columns: String, errorCount: Int) {
    val colors = EolssuTheme.colors
    val text = stringResource(Res.string.result_weak_line, columns, errorCount)
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).background(colors.accentSoft, RoundedCornerShape(3.dp)).border(2.dp, colors.accent, RoundedCornerShape(3.dp)))
        Text(
            buildAnnotatedString {
                val at = text.indexOf(columns)
                if (at < 0) {
                    append(text)
                } else {
                    append(text.substring(0, at))
                    withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Bold)) { append(columns) }
                    append(text.substring(at + columns.length))
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textSub,
        )
    }
}

/**
 * 곡 전체 정간보. 악보 연습·AI 연주 평가 휴대폰 악보와 같은 크기·모양(연습 보드 [JeongganboMetrics.practiceBoard] ·
 * [practiceScorePacking] · [JeongganboLook.practice])으로 그린다. 연습 화면과 달리 긴 각도 두 줄로 나누지 않고 한 줄에 두며
 * (S15P31E102-740), 짧은 각은 위아래로 쌓는다. 악보 전체는 가로로 넘긴다(처음에는 오른쪽 끝의 첫 각).
 * 정간 크기는 연습 최소 정간 아래로 줄이지 않는다([weakSectionScoreHeight]).
 * 보기 전용이라 정간을 누르거나 고를 수 없다. 틀린 정간은 [filter] 종류만 색·아이콘으로, 취약 구간 각은 연두 테두리로 표시한다.
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

/**
 * 저장 ①: ‘연습 기록 저장’ 제목 · 연두 카드(곡 이름 · 날짜 시간 · 녹음 길이 · BPM · 점수) · 메모(선택) · ‘저장’.
 * ‘저장’을 누르면 메모와 함께 한 번에 저장한다.
 */
@Composable
private fun SaveFormSheet(state: ResultUiState, result: EvaluationResult, song: Song, actions: ResultActions) {
    val colors = EolssuTheme.colors
    EolssuBottomSheet(
        onDismiss = actions.onCloseSheet,
        header = {
            Text(
                stringResource(Res.string.result_save),
                modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
        },
        footer = {
            EolssuButton(stringResource(Res.string.result_save_confirm), actions.onConfirmSave, busy = state.savingRecord)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().background(colors.accentTint, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
                        color = colors.text,
                    )
                    Text(
                        stringResource(
                            Res.string.result_saved_meta,
                            "${monthDayLabel(result.recordedAt)} ${timeLabel(result.recordedAt)}",
                            formatClock(result.recordedSeconds.toDouble()),
                            result.bpm,
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.textMuted,
                    )
                }
                Text(
                    buildAnnotatedString {
                        append("${result.total}")
                        withStyle(SpanStyle(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = 12.sp, color = colors.textMuted)) {
                            append(stringResource(Res.string.result_score_unit))
                        }
                    },
                    style = EolssuTheme.serif.yul.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.result_memo_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textSub,
                )
                val fieldShape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.fillMaxWidth().height(52.dp).background(colors.background, fieldShape).border(1.dp, colors.line, fieldShape).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = colors.text)
                    if (state.memo.isEmpty()) Text(stringResource(Res.string.result_memo_placeholder), style = textStyle, color = colors.textDisabled)
                    BasicTextField(
                        value = state.memo,
                        onValueChange = actions.onMemoChange,
                        singleLine = true,
                        enabled = !state.savingRecord,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(colors.text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 저장 ②: ‘연습 기록에 저장했어요’만. 바깥을 누르면 닫힌다. */
@Composable
private fun SavedSheet(onDismiss: () -> Unit) {
    EolssuBottomSheet(
        onDismiss = onDismiss,
        header = {},
    ) {
        Text(
            stringResource(Res.string.result_saved_title),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
