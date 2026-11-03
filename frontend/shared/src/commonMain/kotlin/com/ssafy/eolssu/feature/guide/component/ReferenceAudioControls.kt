package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.guide.referenceAudioTime
import com.ssafy.eolssu.feature.guide.ReferenceAudioController
import com.ssafy.eolssu.feature.guide.ReferenceAudioSelection
import com.ssafy.eolssu.feature.guide.ReferenceLoadState
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 옵션은 한 버튼으로 순환하고, 재생과 위치 조절은 현재 카드 안에서 끝낸다. */
@Composable
fun ReferenceAudioControls(selection: ReferenceAudioSelection, controller: ReferenceAudioController) {
    val colors = EolssuTheme.colors
    val state = controller.state
    val clips = state.clipsFor(selection)
    val clip = state.selectedClip(selection)
    val playback = controller.playback.takeIf {
        it.status == AudioPlaybackStatus.Unavailable || (clip != null && it.sourceId == clip.assetId)
    } ?: AudioPlaybackState()
    var sourceOpen by remember(selection) { mutableStateOf(false) }
    var scrubPosition by remember(selection, clip?.assetId, playback.sourceId) { mutableStateOf<Float?>(null) }
    val duration = playback.durationMs.takeIf { it > 0 } ?: clip?.durationMs?.toLong() ?: 0L
    val position = (scrubPosition?.toLong() ?: if (playback.status == AudioPlaybackStatus.Completed) duration else playback.positionMs).coerceIn(0, duration)
    val seekable = playback.durationMs > 0 && playback.status in setOf(
        AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused, AudioPlaybackStatus.Completed,
    )
    val nextDescription = stringResource(Res.string.reference_audio_next_option)
    val progressDescription = stringResource(Res.string.reference_audio_progress)

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (state.loadState != ReferenceLoadState.Ready || clips.isEmpty()) {
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
        val optionLabel = if (clip == null) stringResource(Res.string.reference_audio_choose_option) else clip.technique
        val optionNumber = if (clip == null) stringResource(Res.string.reference_audio_options, clips.size)
            else stringResource(Res.string.reference_audio_option_number, clips.indexOf(clip) + 1, clips.size)
        val action = stringResource(when (playback.status) {
            AudioPlaybackStatus.Playing -> Res.string.reference_audio_pause
            AudioPlaybackStatus.Paused -> Res.string.reference_audio_resume
            AudioPlaybackStatus.Loading -> Res.string.reference_audio_cancel
            AudioPlaybackStatus.Completed, AudioPlaybackStatus.Interrupted -> Res.string.reference_audio_replay
            AudioPlaybackStatus.Failed -> Res.string.guide_catalog_retry
            else -> Res.string.reference_audio_play
        })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { controller.nextOption(selection) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = nextDescription },
                shape = RoundedCornerShape(10.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.lineSoft),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(optionLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(optionNumber, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    }
                    Icon(EolssuIcons.Restart, null, Modifier.size(18.dp), tint = colors.textMuted)
                }
            }
            Surface(
                onClick = { controller.play(selection) }, enabled = clip != null && playback.status != AudioPlaybackStatus.Unavailable,
                modifier = Modifier.size(48.dp).semantics { contentDescription = action },
                shape = CircleShape,
                color = if (clip == null) colors.lineSoft else colors.primary,
                contentColor = if (clip == null) colors.textMuted else colors.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(when (playback.status) {
                        AudioPlaybackStatus.Playing -> EolssuIcons.Pause
                        AudioPlaybackStatus.Loading -> EolssuIcons.Close
                        else -> EolssuIcons.Play
                    }, null, Modifier.size(22.dp))
                }
            }
        }
        if (clip != null) {
            Slider(
                value = position.toFloat(),
                onValueChange = { scrubPosition = it },
                onValueChangeFinished = {
                    scrubPosition?.let { controller.seek(selection, it.toLong()) }
                    scrubPosition = null
                },
                enabled = seekable,
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription },
                colors = SliderDefaults.colors(thumbColor = colors.play, activeTrackColor = colors.play, inactiveTrackColor = colors.lineSoft),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (clip != null) Text(
                stringResource(Res.string.reference_audio_time, referenceAudioTime(position), referenceAudioTime(duration)),
                style = MaterialTheme.typography.labelSmall, color = colors.textMuted,
            ) else Spacer(Modifier.weight(1f))
            TextButton(onClick = { sourceOpen = !sourceOpen }) {
                Text(stringResource(Res.string.reference_audio_source_info), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
        val message = when (playback.status) {
            AudioPlaybackStatus.Loading -> Res.string.reference_audio_loading_sound
            AudioPlaybackStatus.Failed -> Res.string.reference_audio_failed_sound
            AudioPlaybackStatus.Interrupted -> Res.string.reference_audio_interrupted
            AudioPlaybackStatus.Unavailable -> Res.string.reference_audio_unavailable
            else -> null
        }
        if (message != null) Text(stringResource(message), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        if (sourceOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.reference_audio_source), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Text(stringResource(Res.string.reference_audio_processing), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Text(stringResource(Res.string.reference_audio_explanation), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                TextButton(onClick = controller.openSource) { Text(stringResource(Res.string.reference_audio_source_open), style = MaterialTheme.typography.labelSmall, color = colors.accentText) }
                if (controller.sourceLinkFailed) Text(stringResource(Res.string.reference_audio_source_failed),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
