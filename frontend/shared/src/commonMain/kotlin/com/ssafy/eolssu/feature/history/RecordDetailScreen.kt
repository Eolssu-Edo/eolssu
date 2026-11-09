package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val onToggleFollow: () -> Unit,
    val onRetrySong: () -> Unit = {},
    val onCompare: () -> Unit = {},
    val onSelectFilter: (ErrorFilter) -> Unit = {},
    val onPractice: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onPlayAt: (Int) -> Unit = onSeek,
)

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
            EolssuTopBar(
                title = stringResource(Res.string.record_result_title),
                subtitle = recordResultSubtitle(state),
                onBack = actions.onBack,
                maxWidth = frame.contentMaxWidth,
                right = { if (state.record != null) RecordDeleteButton(state.delete == RecordDeleteState.Deleting, actions.onAskDelete) },
            )
        },
        bottomBar = if (state.record != null && state.song != null) {
            { PlaybackBarContent(state, actions, gap = 8.dp) }
        } else null,
    ) {
        RecordResultContent(state, actions, wrapped = frame.isTablet,
            modifier = Modifier.fillMaxSize().padding(horizontal = frame.gutter))
    }
}

@Composable
internal fun recordResultSubtitle(state: RecordDetailUiState): String? = state.record?.let {
    stringResource(Res.string.weak_errors_subtitle, state.songTitle.orEmpty(), it.score)
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
            Text(
                stringResource(Res.string.record_result_info, monthDayLabel(record.recordedAt), timeLabel(record.recordedAt), record.bpm, record.recordedSeconds),
                style = MaterialTheme.typography.bodySmall, color = EolssuTheme.colors.textMuted,
            )
            val evaluation = state.evaluation
            if (evaluation != null) {
                MetricCards(evaluation, weak = emptySet(), modifier = Modifier.padding(vertical = 8.dp))
            } else {
                RecordScoreText(record, 32.sp)
            }
            if (record.memo.isNotEmpty()) {
                Text(stringResource(Res.string.records_memo, record.memo), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (record.section == null) CompareEntry(state.previous, actions.onCompare, Modifier.padding(bottom = 8.dp))
            if (evaluation != null) {
                ErrorFilterChips(evaluation, state.filter, actions.onSelectFilter, Modifier.padding(bottom = 8.dp), hideEmptyMissed = true, singleLine = true)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(if (state.canPlay) Res.string.record_result_seek_hint else Res.string.record_play_unavailable),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = EolssuTheme.colors.textMuted)
                if (state.canPlay) {
                    val label = stringResource(Res.string.record_detail_follow)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    EolssuSwitch(state.follow, actions.onToggleFollow, label, width = 32.dp, height = 20.dp)
                }
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
            if (wrapped && !scrollPage) {
                WrappedJeongganbo(song, format, current, state.canPlay && state.follow, errors, onEventClick,
                    Modifier.weight(1f).fillMaxWidth(), errorKinds = kinds, unitErrors = units)
            } else {
                JeongganboView(
                    song = song, look = format.look, metrics = format.metrics,
                    cellHeights = format.cellHeights,
                    modifier = (if (scrollPage) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState())).padding(top = 4.dp),
                    errors = errors, errorKinds = kinds, unitErrors = units,
                    currentIndex = current, follow = state.canPlay && state.follow,
                    onEventClick = onEventClick?.let { seek -> { event -> seek(event.index) } },
                )
            }
        }
    }
}
