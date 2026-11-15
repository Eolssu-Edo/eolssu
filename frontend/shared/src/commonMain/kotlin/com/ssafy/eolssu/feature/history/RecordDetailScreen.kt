package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    /** ‘구간 연습’: 이 곡 연습 화면의 구간 연습(S15P31E102-776). */
    val onPractice: () -> Unit = {},
    /** ‘구간 재평가’: 구간 고르기 → 구간 재평가. */
    val onReevaluate: () -> Unit = {},
    /** ‘메모’: 메모 창을 연다. */
    val onOpenMemo: () -> Unit = {},
    val onCloseMemo: () -> Unit = {},
    /** 메모 창의 연필 버튼: 메모를 고치기 시작한다. */
    val onEditMemo: () -> Unit = {},
    val onCancelMemo: () -> Unit = {},
    /** 고친 메모 저장(REC-01). */
    val onSaveMemo: (String) -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onPlayAt: (Int) -> Unit = onSeek,
)

/**
 * RECORD-02 이전 연주 결과(S15P31E102-753, 시안 D안): 제목 ‘곡 · 날짜 시간’과 오른쪽 위 ‘기록 삭제’ · 점수 상자
 * (일원상 총점 · 항목 막대 · ‘지난 연주와 비교’ · 메모) · 5칸 종류 필터 · 정간보 · 아래 재생 묶음과 ‘취약 구간 연습’.
 * 상단 제목은 ‘날짜 시간’만 두고, 곡 제목은 점수 상자 왼쪽 위로 옮겼다(S15P31E102-810).
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
        containerColor = EolssuTheme.colors.groupedBackground,
        bottomDivider = false,
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
                modifier = Modifier.fillMaxSize().padding(horizontal = if (frame.isTablet) 16.dp else 20.dp))
            if (state.playbackLoading) PlaybackLoadingOverlay()
        }
    }
    RecordComparePick(state, actions)
}

/** 상단 제목 한 줄: ‘11월 9일 오후 8:14’(곡 제목은 점수 상자에 둔다, S15P31E102-810). 기록이 없으면 null. */
@Composable
internal fun recordTitleLine(state: RecordDetailUiState): String? = state.record?.let {
    stringResource(Res.string.record_title_line, monthDayLabel(it.recordedAt), timeLabel(it.recordedAt))
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
            // 메모가 없는 기록도 버튼을 두어 창에서 연필 버튼으로 새로 적게 한다(S15P31E102-819·825).
            val showMemo = record.memo.isNotBlank() || state.canEditMemo
            ScoreSummaryCard(
                total = record.score,
                metrics = record.metrics,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                title = song?.title ?: record.songTitle,
                filled = true,
                compact = true,
                action = if (state.canCompare || showMemo) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (showMemo) {
                                TintPillButton(stringResource(Res.string.record_memo_button), actions.onOpenMemo, icon = EolssuIcons.Chat)
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
            state.memo?.let { memo -> RecordMemoDialog(record.id, record.memo, memo, actions) }
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
            // 다른 화면과 같은 공통 정간보 형식(가사가 있으면 곁줄까지).
            val format = rememberReadingScoreFormat(song)
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

/**
 * 메모 창(S15P31E102-819). 읽을 때는 옅은 바탕 상자에 메모를 보이고, 상자 오른쪽 위 연필 버튼으로 고치기에 들어간다.
 * 메모가 없으면 안내 문구 없이 빈 상자와 연필 버튼만 보인다.
 * 고칠 때는 같은 자리의 상자가 저장 시트 메모 입력칸 모양(흰 바탕 · 1dp 테두리 · 모서리 12)의 여러 줄 입력칸이 되고,
 * 그 아래에 실패 안내와 ‘n/500’, 맨 아래에 ‘취소’ · ‘저장’을 둔다. 저장 중에는 입력·취소·창 닫기를 막는다.
 */
@Composable
private fun RecordMemoDialog(recordId: String, memo: String, dialog: RecordMemoState, actions: RecordDetailActions) {
    val colors = EolssuTheme.colors
    EolssuDialog(
        title = stringResource(Res.string.record_memo_button),
        icon = EolssuIcons.Chat,
        onDismissRequest = actions.onCloseMemo,
    ) {
        if (dialog.editing) {
            RecordMemoEditor(recordId, memo, dialog, actions)
            return@EolssuDialog
        }
        val editLabel = stringResource(Res.string.record_memo_edit)
        // 메모가 없는 기록은 안내 문구 없이 같은 상자를 비워 두고, 연필 버튼을 눌러야 입력칸으로 바뀐다(S15P31E102-846).
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                memo,
                Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 240.dp)
                    .background(colors.background, MemoFieldShape)
                    .verticalScroll(rememberScrollState())
                    // 오른쪽 위 연필 버튼과 첫 줄이 겹치지 않게 오른쪽을 비운다.
                    .padding(start = 14.dp, top = 14.dp, end = 40.dp, bottom = 14.dp),
                style = memoTextStyle(),
                color = colors.text,
            )
            Box(
                Modifier.align(Alignment.TopEnd).size(44.dp)
                    .clickable(role = Role.Button, onClick = actions.onEditMemo)
                    .semantics { contentDescription = editLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(EolssuIcons.Pencil, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.accentText)
            }
        }
        EolssuButton(
            stringResource(Res.string.common_close),
            actions.onCloseMemo,
            style = EolssuButtonStyle.Secondary,
            containerColor = colors.groupedBackground,
            borderColor = Color.Transparent,
        )
    }
}

/** 메모 고치기: 여러 줄 입력칸(500자까지) · 실패 안내 · 글자 수 · ‘취소’ · ‘저장’. */
@Composable
private fun RecordMemoEditor(recordId: String, memo: String, dialog: RecordMemoState, actions: RecordDetailActions) {
    val colors = EolssuTheme.colors
    // 고치기를 새로 시작할 때마다 지금 메모로 채운다. 저장에 실패하면 적던 내용을 그대로 둔다.
    var draft by rememberSaveable(recordId, memo, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(memo, TextRange(memo.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val textStyle = memoTextStyle().copy(color = colors.text)
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)
                .background(colors.surface, MemoFieldShape)
                .border(1.dp, if (dialog.failed) colors.rec else colors.line, MemoFieldShape)
                .padding(14.dp),
        ) {
            // 기록 상세에서 고칠 때는 예시 문구를 두지 않고 빈 입력칸만 보인다(S15P31E102-830).
            BasicTextField(
                value = draft,
                // 서버는 500자를 넘는 메모를 받지 않으므로(400) 그 이상은 입력하지 않는다.
                onValueChange = { if (it.text.length <= RECORD_MEMO_MAX_LENGTH) draft = it },
                enabled = !dialog.saving,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.text),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                if (dialog.failed) {
                    Text(
                        stringResource(Res.string.record_memo_save_failed),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = small,
                        color = colors.recText,
                    )
                }
            }
            Text(
                stringResource(Res.string.record_memo_count, draft.text.length, RECORD_MEMO_MAX_LENGTH),
                Modifier.padding(start = 8.dp),
                style = small,
                color = if (draft.text.length >= RECORD_MEMO_MAX_LENGTH) colors.recText else colors.textMuted,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EolssuButton(
            stringResource(Res.string.common_cancel),
            actions.onCancelMemo,
            Modifier.weight(1f),
            style = EolssuButtonStyle.Secondary,
            enabled = !dialog.saving,
            containerColor = colors.groupedBackground,
            borderColor = Color.Transparent,
        )
        EolssuButton(
            stringResource(Res.string.record_memo_save),
            { actions.onSaveMemo(draft.text) },
            Modifier.weight(1f),
            busy = dialog.saving,
        )
    }
}

private val MemoFieldShape = RoundedCornerShape(12.dp)

@Composable
private fun memoTextStyle() = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp)

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
