package com.ssafy.eolssu.feature.history.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ssafy.eolssu.core.ui.component.PlaybackActionButton
import com.ssafy.eolssu.core.ui.component.PlaybackControlRow
import com.ssafy.eolssu.core.ui.component.PlaybackPositionSlider
import com.ssafy.eolssu.core.ui.component.PlaybackSkipGroup
import com.ssafy.eolssu.core.ui.component.PlaybackTimeText
import com.ssafy.eolssu.core.ui.component.PlaybackTimeline
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.playbackElapsedText
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_pause
import eolssu.shared.generated.resources.practice_play
import eolssu.shared.generated.resources.record_detail_back5_label
import eolssu.shared.generated.resources.record_detail_fwd5_label
import eolssu.shared.generated.resources.record_detail_position
import eolssu.shared.generated.resources.record_detail_seek
import eolssu.shared.generated.resources.weak_select_practice
import org.jetbrains.compose.resources.stringResource


/** 지금 위치(시간 · 각·칸)와 전체 길이. */
@Composable
internal fun PlaybackTimeRow(state: RecordDetailUiState) {
    val event = state.song?.timeline?.getOrNull(state.position)
    PlaybackTimeText(
        playbackElapsedText(
            formatClock(state.elapsedSeconds),
            event?.let { stringResource(Res.string.record_detail_position, it.column + 1, it.row + 1) },
        ),
        formatClock(state.durationMillis / 1000.0),
    )
}

/** 재생 위치 막대. 50ms 단위 오디오 보고 사이를 보간한다. */
@Composable
internal fun PlaybackSlider(state: RecordDetailUiState, actions: RecordDetailActions, modifier: Modifier = Modifier) {
    var draft by remember(state.record?.id) { mutableStateOf<Float?>(null) }
    var seekRevision by remember(state.record?.id) { mutableStateOf(0) }
    // 드래그·일시정지는 즉시 반영한다.
    val animated = key(state.record?.id, seekRevision) {
        animateFloatAsState(
            state.playbackPositionMs.toFloat(),
            animationSpec = if (state.playing && draft == null) tween(60, easing = LinearEasing) else snap(),
        ).value
    }
    PlaybackPositionSlider(
        value = draft ?: animated,
        onValueChange = { draft = it },
        onValueChangeFinished = {
            draft?.let { actions.onSeekMillis(it.toLong()) }
            draft = null
            seekRevision++
        },
        enabled = state.canPlay,
        label = stringResource(Res.string.record_detail_seek),
        modifier = modifier,
        valueRange = 0f..state.durationMillis.coerceAtLeast(1L).toFloat(),
    )
}

/**
 * 재생 묶음(↺5 · 재생 · ↻5, S15P31E102-768). 녹음을 다시 들을 수 없는 기록(`recordingAvailable` false)은 모두 쓸 수 없다.
 */
@Composable
internal fun PlaybackButtons(state: RecordDetailUiState, actions: RecordDetailActions) {
    PlaybackSkipGroup(
        playing = state.playing,
        playEnabled = state.canPlay,
        playLabel = stringResource(if (state.playing) Res.string.practice_pause else Res.string.practice_play),
        onTogglePlay = actions.onTogglePlay,
        backLabel = stringResource(Res.string.record_detail_back5_label),
        onBack = { actions.onSkip(-5) },
        forwardLabel = stringResource(Res.string.record_detail_fwd5_label),
        onForward = { actions.onSkip(5) },
    )
}

/**
 * 하단 재생 영역 내용(시간 · 막대 · 재생 묶음과 그 오른쪽 ‘구간 연습’, S15P31E102-794). 휴대폰·태블릿이 같은 구성이다.
 * 연습 화면 플레이어도 같은 부품을 쓴다(S15P31E102-873).
 */
@Composable
internal fun PlaybackBarContent(state: RecordDetailUiState, actions: RecordDetailActions) {
    PlaybackTimeline {
        PlaybackTimeRow(state)
        PlaybackSlider(state, actions)
    }
    PlaybackControlRow {
        PlaybackButtons(state, actions)
        // 구간 재평가는 구간 고르기에서 함께 시작한다(S15P31E102-794).
        PlaybackActionButton(
            stringResource(Res.string.weak_select_practice), actions.onPractice, Modifier.weight(1f), enabled = state.canPractice,
        )
    }
}
