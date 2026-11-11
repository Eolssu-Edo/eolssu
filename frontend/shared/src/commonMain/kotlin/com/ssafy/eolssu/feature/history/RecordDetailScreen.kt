package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.core.ui.score.*
import com.ssafy.eolssu.feature.history.component.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 기록 결과와 녹음 재생이 공유하는 동작. 화면 이동은 Route에서 연결한다. */
class RecordDetailActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onSeekMillis: (Long) -> Unit,
    val onSkip: (Int) -> Unit,
    val onFollowNow: () -> Unit = {},
    val onDetachFollow: () -> Unit = {},
    val onRetrySong: () -> Unit = {},
    /** ‘지난 연주와 비교’: 기록 고르기 시트를 연다. */
    val onCompare: () -> Unit = {},
    val onCloseCompare: () -> Unit = {},
    /** 시트에서 고른 이전 기록과 비교 화면으로 간다. */
    val onPickCompare: (String) -> Unit = {},
    val onSelectFilter: (ErrorFilter) -> Unit = {},
    val onPractice: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onPlayAt: (Int) -> Unit = onSeek,
)

/**
 * RECORD-02 이전 연주 결과(S15P31E102-753, 시안 D안): 제목 ‘곡 · 날짜 시간’과 오른쪽 위 ‘기록 삭제’ · 점수 상자
 * (일원상 총점 · 항목 막대 · ‘지난 연주와 비교’ · 메모) · 5칸 종류 필터 · 정간보 · 아래 재생 묶음과 ‘취약 구간 연습’.
 */
@Composable
fun RecordDetailScreen(
    state: RecordDetailUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: RecordDetailActions,
) {
    val frame = historyFrame(widthClass)
    RecordDeleteConfirmation(state, actions)
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        snackbarHostState = snackbarHostState,
        contentMaxWidth = frame.contentMaxWidth,
        topBar = {
            EolssuStartTopBar(
                title = recordTitleLine(state) ?: stringResource(Res.string.record_result_title),
                onBack = actions.onBack,
                maxWidth = frame.contentMaxWidth,
                right = { if (state.record != null) RecordDeleteButton(state.delete == RecordDeleteState.Deleting, actions.onAskDelete) },
            )
        },
        bottomBar = if (state.record != null) {
            { PlaybackBarContent(state, actions) }
        } else null,
    ) {
        Box(Modifier.fillMaxSize()) {
            RecordResultContent(state, actions, wrapped = frame.isTablet,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp))
            if (state.playbackLoading) PlaybackLoadingOverlay()
        }
    }
    RecordComparePick(state, actions)
}

/** 제목 한 줄: ‘아리랑 · 11월 9일 오후 8:14’. 기록이 없으면 null. */
@Composable
internal fun recordTitleLine(state: RecordDetailUiState): String? = state.record?.let {
    stringResource(Res.string.record_title_line, state.song?.title ?: it.songTitle, monthDayLabel(it.recordedAt), timeLabel(it.recordedAt))
}

/** ‘지난 연주와 비교’로 연 기록 고르기 시트. */
@Composable
internal fun RecordComparePick(state: RecordDetailUiState, actions: RecordDetailActions) {
    val pick = state.comparePick
    if (state.comparePickOpen && pick != null) {
        ComparePickSheet(pick, actions.onCloseCompare, actions.onPickCompare, state.compareLoad, actions.onCompare)
    }
}

/** 세 배치에서 같은 요약·필터·악보를 쓴다. 짧은 창은 본문 전체를 스크롤한다. */
@Composable
internal fun RecordResultContent(
    state: RecordDetailUiState,
    actions: RecordDetailActions,
    wrapped: Boolean,
    modifier: Modifier = Modifier,
) {
    val record = state.record
    val song = state.song
    if (record == null) {
        RecordSongLookupContent(state, actions)
        return
    }
    BoxWithConstraints(modifier) {
        val scrollPage = maxHeight < 360.dp
        Column(Modifier.fillMaxSize().then(if (scrollPage) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            // 메모는 상자 안에 펼치지 않고 ‘메모’ 버튼(‘지난 연주와 비교’와 같은 줄)을 누르면 창으로 보인다.
            var memoOpen by rememberSaveable(record.id) { mutableStateOf(false) }
            val hasMemo = record.memo.isNotBlank()
            ScoreSummaryCard(
                total = record.score,
                metrics = record.metrics,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                action = if (state.canCompare || hasMemo) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (hasMemo) {
                                TintPillButton(stringResource(Res.string.record_memo_button), { memoOpen = true }, icon = EolssuIcons.Chat)
                            }
                            if (state.canCompare) {
                                TintPillButton(
                                    stringResource(Res.string.weak_record_compare), actions.onCompare,
                                    icon = EolssuIcons.Calendar, trailing = EolssuIcons.Chevron,
                                )
                            }
                        }
                    }
                } else null,
            )
            if (memoOpen && hasMemo) RecordMemoDialog(record.memo) { memoOpen = false }
            val evaluation = state.evaluation
            if (evaluation != null && song != null) {
                ErrorKindSegments(evaluation, state.filter, actions.onSelectFilter, Modifier.padding(bottom = 6.dp))
            }
            if (!state.canPlay) {
                Text(
                    stringResource(Res.string.record_play_unavailable),
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = EolssuTheme.colors.textMuted,
                )
            }
            if (song == null) {
                Box(if (scrollPage) Modifier.height(240.dp) else Modifier.weight(1f)) { RecordSongLookupContent(state, actions) }
                return@Column
            }
            // 다른 화면과 같은 공통 정간보 형식. 좁은 기록 화면이라 곁줄(가사 칸)만 두지 않는다.
            val format = rememberReadingScoreFormat(song, lyrics = false)
            val errors = evaluation?.visibleErrors(state.filter).orEmpty()
            val kinds = evaluation?.visibleErrorKinds(state.filter).orEmpty()
            val units = evaluation?.visibleUnitErrors(song, state.filter).orEmpty()
            val current = state.position.takeIf { state.canPlay }
            val onEventClick: ((Int) -> Unit)? = actions.onPlayAt.takeIf { state.canPlay }
            // 재생 중에는 늘 따라가고, 사용자가 악보를 넘기면 멈춘 뒤 ‘지금 위치로’로 다시 따라간다.
            val follow = state.canPlay && state.follow
            Box(if (scrollPage) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()) {
                if (wrapped && !scrollPage) {
                    WrappedJeongganbo(song, format, current, follow, errors, onEventClick,
                        Modifier.fillMaxSize(), errorKinds = kinds, unitErrors = units, onUserScroll = actions.onDetachFollow)
                } else {
                    JeongganboView(
                        song = song, look = format.look, metrics = format.metrics,
                        cellHeights = format.cellHeights,
                        modifier = (if (scrollPage) Modifier else Modifier.fillMaxSize().verticalScroll(rememberScrollState())).padding(top = 4.dp),
                        errors = errors, errorKinds = kinds, unitErrors = units,
                        currentIndex = current, follow = follow, onUserScroll = actions.onDetachFollow,
                        onEventClick = onEventClick?.let { seek -> { event -> seek(event.index) } },
                    )
                }
                if (state.showFollowNow) FollowNowButton(actions.onFollowNow, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
        }
    }
}

/** 메모 창: 제목 ‘메모’ · 적은 메모 · ‘닫기’. 바깥을 눌러도 닫힌다. */
@Composable
private fun RecordMemoDialog(memo: String, onDismiss: () -> Unit) {
    EolssuDialog(
        title = stringResource(Res.string.record_memo_button),
        message = memo,
        icon = EolssuIcons.Chat,
        onDismissRequest = onDismiss,
    ) {
        EolssuButton(stringResource(Res.string.common_close), onDismiss, style = EolssuButtonStyle.Secondary)
    }
}

/** ‘지금 위치로’(먹색 알약 36dp · 흰 글자 13sp): 사용자가 악보를 넘긴 뒤 다시 재생 위치를 따라가게 한다. */
@Composable
private fun FollowNowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            modifier = Modifier.height(36.dp).shadow(6.dp, shape),
            shape = shape,
            color = colors.text,
            contentColor = colors.onInk,
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(EolssuIcons.Locate, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(Res.string.record_follow_now),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
