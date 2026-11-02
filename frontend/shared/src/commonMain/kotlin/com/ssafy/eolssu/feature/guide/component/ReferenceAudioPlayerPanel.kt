package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.data.song.ReferenceAudioClip
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun referenceAudioTitle(clip: ReferenceAudioClip): String =
    stringResource(Res.string.reference_audio_clip_title, clip.technique, clip.displayLabel)

@Composable
internal fun ReferenceAudioPlayerPanel(
    clip: ReferenceAudioClip?,
    takeNumber: Int,
    playback: AudioPlaybackState,
    onPlay: (ReferenceAudioClip) -> Unit,
) {
    val colors = EolssuTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        if (clip == null) {
            Box(Modifier.heightIn(min = 80.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.reference_audio_choose), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            return@Surface
        }
        val durationMs = playback.durationMs.takeIf { it > 0 } ?: clip.durationMs.toLong().coerceAtLeast(0)
        val positionMs = if (playback.status == AudioPlaybackStatus.Completed) durationMs else playback.positionMs.coerceIn(0, durationMs)
        val progress = if (durationMs == 0L) 0f else (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        val currentTime = formatClock(positionMs / 1000.0)
        val totalTime = formatClock(durationMs / 1000.0)
        val timeDescription = stringResource(Res.string.reference_audio_time_description, currentTime, totalTime)
        val progressDescription = stringResource(Res.string.reference_audio_progress)
        val action = stringResource(
            when (playback.status) {
                AudioPlaybackStatus.Playing -> Res.string.reference_audio_pause
                AudioPlaybackStatus.Paused -> Res.string.reference_audio_resume
                AudioPlaybackStatus.Loading -> Res.string.reference_audio_cancel
                AudioPlaybackStatus.Completed, AudioPlaybackStatus.Interrupted -> Res.string.reference_audio_replay
                AudioPlaybackStatus.Failed -> Res.string.guide_catalog_retry
                else -> Res.string.reference_audio_play
            },
        )
        val statusMessage = when (playback.status) {
            AudioPlaybackStatus.Loading -> Res.string.reference_audio_loading_sound
            AudioPlaybackStatus.Paused -> Res.string.reference_audio_paused
            AudioPlaybackStatus.Completed -> Res.string.reference_audio_completed
            AudioPlaybackStatus.Interrupted -> Res.string.reference_audio_interrupted
            AudioPlaybackStatus.Failed -> Res.string.reference_audio_failed_sound
            AudioPlaybackStatus.Unavailable -> Res.string.reference_audio_unavailable
            else -> null
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { onPlay(clip) },
                    enabled = playback.status != AudioPlaybackStatus.Unavailable,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = action },
                    shape = CircleShape,
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            when (playback.status) {
                                AudioPlaybackStatus.Playing -> EolssuIcons.Pause
                                AudioPlaybackStatus.Loading -> EolssuIcons.Close
                                else -> EolssuIcons.Play
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.reference_audio_take, takeNumber),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(Res.string.reference_audio_time, currentTime, totalTime),
                        modifier = Modifier.semantics { contentDescription = timeDescription },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription },
                color = colors.play,
                trackColor = colors.lineSoft,
            )
            if (statusMessage != null) {
                Text(
                    stringResource(statusMessage),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}
