package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.ElasticSquashSoftAmount
import com.ssafy.eolssu.core.ui.component.ElasticSquashTextWidth
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.elasticSquash
import com.ssafy.eolssu.feature.guide.ReferenceAudioController
import com.ssafy.eolssu.feature.guide.ReferenceAudioKind
import com.ssafy.eolssu.feature.guide.ReferenceAudioSelection
import com.ssafy.eolssu.feature.guide.ReferenceLoadState
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 음마다 기본음·요성 예시를 스피커 버튼으로 들려준다. 같은 버튼을 다시 누르면 멈춘다.
 * [showLoadingMessage]가 false면 불러오는 중 안내 문구를 숨긴다. 음원 목록을 받는 동안에도 문구 대신 버튼을 비활성으로
 * 먼저 보이고 목록이 오면 누를 수 있게 하며, 재생 준비 중 문구도 띄우지 않는다(버튼의 재생 중 표시는 그대로).
 */
@Composable
fun ReferenceAudioControls(
    selection: ReferenceAudioSelection,
    controller: ReferenceAudioController,
    basicOnly: Boolean = false,
    showLoadingMessage: Boolean = true,
) {
    val colors = EolssuTheme.colors
    val state = controller.state
    val basic = state.clipFor(selection, ReferenceAudioKind.Basic)
    val yoseong = if (basicOnly) null else state.clipFor(selection, ReferenceAudioKind.Yoseong)
    val playback = controller.playback.takeIf {
        it.status == AudioPlaybackStatus.Unavailable || it.sourceId in setOfNotNull(basic?.assetId, yoseong?.assetId)
    } ?: AudioPlaybackState()

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val listLoadingSilently = !showLoadingMessage && state.loadState == ReferenceLoadState.Loading
        if (!listLoadingSilently && (state.loadState != ReferenceLoadState.Ready || (basic == null && yoseong == null))) {
            val message = when (state.loadState) {
                ReferenceLoadState.Loading -> Res.string.reference_audio_loading_list
                ReferenceLoadState.Failed, ReferenceLoadState.NotRegistered -> Res.string.reference_audio_failed_list
                ReferenceLoadState.Ready -> Res.string.reference_audio_empty_note
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(message), Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                if (state.loadState == ReferenceLoadState.Failed) {
                    TextButton(onClick = controller.retry) { Text(stringResource(Res.string.guide_catalog_retry)) }
                }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val kinds = if (basicOnly) listOf(ReferenceAudioKind.Basic) else ReferenceAudioKind.entries
            kinds.forEach { kind ->
                val clip = if (kind == ReferenceAudioKind.Basic) basic else yoseong
                val active = clip != null && playback.sourceId == clip.assetId &&
                    playback.status in setOf(AudioPlaybackStatus.Loading, AudioPlaybackStatus.Playing)
                val label = stringResource(when {
                    basicOnly && !active -> Res.string.practice_listen_note
                    kind == ReferenceAudioKind.Basic -> Res.string.reference_audio_basic
                    else -> Res.string.reference_audio_yoseong
                })
                val buttonLabel = if (basicOnly && active) stringResource(Res.string.reference_audio_stop, label) else label
                SpeakerButton(
                    label = buttonLabel,
                    active = active,
                    actionLabel = basicOnly,
                    enabled = clip != null && playback.status != AudioPlaybackStatus.Unavailable,
                    onClick = { controller.play(selection, kind) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val message = when (playback.status) {
            AudioPlaybackStatus.Loading -> Res.string.reference_audio_loading_sound.takeIf { showLoadingMessage }
            AudioPlaybackStatus.Failed -> Res.string.reference_audio_failed_sound
            AudioPlaybackStatus.Interrupted -> Res.string.reference_audio_interrupted
            AudioPlaybackStatus.Unavailable -> Res.string.reference_audio_unavailable
            else -> null
        }
        if (message != null) Text(stringResource(message), Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

@Composable
private fun SpeakerButton(
    label: String,
    active: Boolean,
    actionLabel: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val description = when {
        actionLabel -> label
        active -> stringResource(Res.string.reference_audio_stop, label)
        else -> stringResource(Res.string.reference_audio_listen, label)
    }
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, enabled = enabled,
        // 음을 차례로 빠르게 눌러 비교하는 버튼이라 약하게 출렁인다(S15P31E102-891).
        modifier = modifier
            .elasticSquash(press, amount = ElasticSquashSoftAmount, widthFactor = ElasticSquashTextWidth)
            .heightIn(min = 48.dp).semantics { contentDescription = description },
        interactionSource = press,
        shape = RoundedCornerShape(10.dp),
        color = if (active) colors.primary else colors.accentTint,
        contentColor = when {
            active -> colors.onPrimary
            enabled -> colors.accentText
            else -> colors.textMuted
        },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Icon(EolssuIcons.Speaker, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
