package com.ssafy.eolssu.feature.result

import androidx.compose.ui.text.input.ImeAction
import com.ssafy.eolssu.core.ui.component.EolssuTextLink
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.ssafy.eolssu.core.model.KoreanDateTime
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
import com.ssafy.eolssu.core.ui.component.EolssuTextField
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.ScoreSummaryCard
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
import com.ssafy.eolssu.feature.result.layout.ResultButtonRow
import com.ssafy.eolssu.feature.result.layout.ResultExpandedPanes
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
import eolssu.shared.generated.resources.result_saved_open_record
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
    val onOpenRecord: () -> Unit = {},
)

/**
 * 연주 결과(S15P31E102-753, 시안 연주 평가 결과 · D안 바탕). 요약(RESULT-02)과 틀린 부분 상세(RESULT-03)를 한 화면에 둔다:
 * 제목 ‘곡 · 악기 · 난이도’와 오른쪽 위 홈 · 점수 상자(일원상 총점 · 항목 막대) · 5칸 종류 필터 ·
 * 취약 구간 줄 · 곡 전체 정간보(보기 전용) · ‘다른 곡 고르기’, 아래 한 줄에 반씩 ‘같은 곡 다시 연습’ · ‘연습 기록 저장’.
 * 태블릿 세로는 같은 배치를 가운데에 넓게 두고, 태블릿 가로는 요약 왼쪽 · 악보 오른쪽 두 칸으로 둔다(S15P31E102-974). 바탕은 다른 화면과 같은 회색이고 점수 상자는 흰 카드다(S15P31E102-815).
 */
@Composable
fun ResultScreen(
    state: ResultUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: ResultActions,
) {
    if (state.result == null || state.song == null) {
        EolssuScaffold(
            containerColor = EolssuTheme.colors.groupedBackground,
            topBar = { EolssuTopBar(stringResource(Res.string.result_title), onBack = actions.onBack) },
        ) {
            SongLookupContent(
                if (state.result == null) state.resultLookup else state.songLookup,
                if (state.result == null) actions.onRetryResult else actions.onRetrySong,
                actions.onBack,
                resultLookup = state.result == null && state.scorePreview == null,
                header = state.scorePreview?.let { preview ->
                    {
                        Column(Modifier.widthIn(max = CompactContentMaxWidth).fillMaxWidth()) {
                            ResultScoreCard(preview)
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
        containerColor = EolssuTheme.colors.groupedBackground,
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
            // ‘같은 곡 다시 연습’(보조)과 ‘연습 기록 저장’(주)을 한 줄에 반씩 둔다(S15P31E102-832).
            // 태블릿 가로는 줄을 최대 640dp로 묶어 버튼이 늘어나지 않게 한다(S15P31E102-974).
            ResultButtonRow(expanded = widthClass == WindowWidthClass.Expanded) {
                EolssuButton(
                    stringResource(Res.string.result_practice_again), actions.onPracticeAgain, Modifier.weight(1f).fillMaxHeight(),
                    style = EolssuButtonStyle.Secondary, compact = true,
                )
                RecordSaveButton(state.savedRecord != null, actions.onSave, Modifier.weight(1f).fillMaxHeight())
            }
        },
    ) {
        if (widthClass == WindowWidthClass.Expanded) {
            ResultExpandedContent(state, result, song, actions)
        } else {
            ResultContent(state, result, song, actions)
        }
    }
    when (state.saveSheet) {
        SaveSheet.Form -> SaveFormSheet(
            title = song.title, recordedAt = result.recordedAt, total = result.total, memo = state.memo, saving = state.savingRecord,
            onMemoChange = actions.onMemoChange, onConfirm = actions.onConfirmSave, onDismiss = actions.onCloseSheet,
        )
        SaveSheet.Saved -> SavedSheet(actions.onCloseSheet, actions.onOpenRecord)
        null -> Unit
    }
}

/**
 * ‘연습 기록 저장’ 버튼. 저장했으면 ‘저장됨’과 체크가 된다. 연주 결과(주)와 구간 재평가 결과(보조)가 함께 쓴다.
 */
@Composable
internal fun RecordSaveButton(
    saved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EolssuButtonStyle = EolssuButtonStyle.Primary,
) {
    EolssuButton(
        stringResource(if (saved) Res.string.result_saved_button else Res.string.result_save),
        onClick, modifier,
        style = style, icon = if (saved) EolssuIcons.Check else EolssuIcons.Save, compact = true,
    )
}

/** 점수 상자: 일원상 총점 · 항목 막대. 예시 결과면 표시를 붙인다. 회색 바탕 위 흰 카드다. */
@Composable
private fun ResultScoreCard(result: EvaluationResult, modifier: Modifier = Modifier) {
    ScoreSummaryCard(
        total = result.total,
        metrics = result.metrics,
        modifier = modifier,
        filled = true,
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
    val section = remember(state.weakSection, song) { state.weakSection?.toSection(song.score) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf<Dp?>(null) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                ResultScoreCard(result, Modifier.padding(top = 2.dp, bottom = 8.dp))
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
            OtherSongLink(actions.onOtherSong, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/**
 * 태블릿 가로(Expanded) 본문(S15P31E102-974). 왼쪽 칸에 점수 상자 · 종류 필터 · 취약 구간 줄 · ‘다른 곡 고르기’를,
 * 오른쪽 칸에 곡 전체 정간보를 남은 높이만큼 둔다. 악보가 최소 크기보다 높으면 오른쪽 칸만 세로로 스크롤된다.
 */
@Composable
private fun ResultExpandedContent(state: ResultUiState, result: EvaluationResult, song: Song, actions: ResultActions) {
    val section = remember(state.weakSection, song) { state.weakSection?.toSection(song.score) }
    ResultExpandedPanes(
        summary = {
            ResultScoreCard(result, Modifier.padding(bottom = 8.dp))
            ErrorKindSegments(result, state.filter, actions.onSelectFilter, Modifier.padding(bottom = 6.dp))
            val pick = state.weakSection
            if (section != null && pick != null) {
                WeakSectionLine(scoreSectionLabel(section, song.score), pick.errorCount)
            }
            OtherSongLink(actions.onOtherSong, Modifier.align(Alignment.CenterHorizontally))
        },
        score = {
            val paneHeight = maxHeight
            if (song.score.columns.isNotEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ErrorDetailScore(
                        song = song,
                        result = result,
                        filter = state.filter,
                        section = section,
                        availableHeight = paneHeight - ExpandedScoreBottomGap,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

/** 태블릿 가로 오른쪽 악보 칸 아래 여백. */
private val ExpandedScoreBottomGap = 12.dp

/** 악보 아래(태블릿 가로는 왼쪽 칸 아래) ‘다른 곡 고르기’ 글자 버튼. */
@Composable
private fun OtherSongLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    EolssuTextLink(onClick, modifier.padding(top = 2.dp, bottom = 10.dp)) {
        Text(
            stringResource(Res.string.result_other_song_more),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = EolssuTheme.colors.textSub,
        )
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
                    withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) { append(columns) }
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
 * 저장 ①: ‘연습 기록 저장’ 제목 · 연두 카드(곡 이름 · 날짜 시간 · 점수) · 메모(선택) · ‘저장’.
 * ‘저장’을 누르면 메모와 함께 한 번에 저장한다. 연주 결과와 구간 재평가 결과가 함께 쓴다(S15P31E102-832).
 */
@Composable
internal fun SaveFormSheet(
    title: String,
    recordedAt: KoreanDateTime,
    total: Int,
    memo: String,
    saving: Boolean,
    onMemoChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = EolssuTheme.colors
    EolssuBottomSheet(
        onDismiss = onDismiss,
        header = {
            Text(
                stringResource(Res.string.result_save),
                modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
        },
        footer = {
            EolssuButton(stringResource(Res.string.result_save_confirm), onConfirm, busy = saving)
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
                        title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                    )
                    Text(
                        "${monthDayLabel(recordedAt)} ${timeLabel(recordedAt)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.textMuted,
                    )
                }
                Text(
                    buildAnnotatedString {
                        append("$total")
                        withStyle(SpanStyle(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = 12.sp, color = colors.textMuted)) {
                            append(stringResource(Res.string.result_score_unit))
                        }
                    },
                    style = EolssuTheme.serif.yul.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                )
            }
            // 공용 입력칸: 선택 테두리·큰 글꼴 높이 맞춤을 다른 입력칸과 같게 한다(S15P31E102-972). 저장 중에는 바꾸지 않는다.
            EolssuTextField(
                value = memo,
                onValueChange = { if (!saving) onMemoChange(it) },
                label = stringResource(Res.string.result_memo_label),
                placeholder = stringResource(Res.string.result_memo_placeholder),
                imeAction = ImeAction.Done,
                containerColor = colors.background,
            )
        }
    }
}

/** 저장 ②: ‘연습 기록에 저장했어요’와 그 아래 ‘기록으로 이동’(방금 저장한 기록 상세, S15P31E102-815). 바깥을 누르면 닫힌다. */
@Composable
internal fun SavedSheet(onDismiss: () -> Unit, onOpenRecord: () -> Unit) {
    EolssuBottomSheet(
        onDismiss = onDismiss,
        header = {},
        footer = {
            EolssuButton(stringResource(Res.string.result_saved_open_record), onOpenRecord)
        },
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
