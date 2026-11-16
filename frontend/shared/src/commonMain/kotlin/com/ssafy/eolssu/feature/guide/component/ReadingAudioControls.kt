package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.guide.ReadingAudioClip
import com.ssafy.eolssu.feature.guide.ReadingAudioState
import com.ssafy.eolssu.feature.guide.ReadingBeatMs
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.reading_audio_failed
import eolssu.shared.generated.resources.reading_audio_listen
import eolssu.shared.generated.resources.reading_audio_listen_named
import eolssu.shared.generated.resources.reading_audio_loading
import eolssu.shared.generated.resources.reading_audio_playing
import eolssu.shared.generated.resources.reading_audio_stop
import eolssu.shared.generated.resources.reading_audio_stop_named
import eolssu.shared.generated.resources.reading_beat_one
import eolssu.shared.generated.resources.reading_beat_two
import org.jetbrains.compose.resources.stringResource

/** 읽는 순서 그림처럼 글자 뒤로 퍼지는 녹색 빛. 칸 경계와 기호 모양은 유지한다. */
@Composable
internal fun Modifier.readingNoteGlow(active: Boolean): Modifier {
    val glow = EolssuTheme.colors.glow
    return if (!active) this else drawBehind {
        val radius = size.maxDimension * 0.45f
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0f)), center, radius), radius)
    }
}

@Composable
internal fun ReadingBeatMarkers(positionMs: Long?) {
    val colors = EolssuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(stringResource(Res.string.reading_beat_one), stringResource(Res.string.reading_beat_two)).forEachIndexed { i, text ->
            val on = positionMs != null && positionMs / ReadingBeatMs == i.toLong()
            Text(text,
                Modifier.background(if (on) colors.primary else colors.background, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                color = if (on) colors.onPrimary else colors.textMuted)
        }
    }
}

@Composable
internal fun ReadingListenButton(
    clip: ReadingAudioClip, name: String, audio: ReadingAudioState, onClick: () -> Unit, enabled: Boolean,
) {
    val active = audio.active(clip)
    val description = stringResource(if (active) Res.string.reading_audio_stop_named else Res.string.reading_audio_listen_named, name)
    val status = stringResource(if (audio.loading) Res.string.reading_audio_loading else Res.string.reading_audio_playing)
    EolssuButton(
        stringResource(if (active) Res.string.reading_audio_stop else Res.string.reading_audio_listen), onClick,
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics {
            contentDescription = description
            if (active) stateDescription = status
        },
        style = if (active) EolssuButtonStyle.Primary else EolssuButtonStyle.Secondary,
        icon = if (active) EolssuIcons.Stop else EolssuIcons.Speaker,
        enabled = enabled, small = true,
    )
}

@Composable
internal fun ReadingSpeakerButton(
    clip: ReadingAudioClip, name: String, audio: ReadingAudioState, onClick: () -> Unit, enabled: Boolean,
) {
    val colors = EolssuTheme.colors
    val active = audio.active(clip)
    val description = stringResource(if (active) Res.string.reading_audio_stop_named else Res.string.reading_audio_listen_named, name)
    val status = stringResource(if (audio.loading) Res.string.reading_audio_loading else Res.string.reading_audio_playing)
    IconButton(
        onClick, enabled = enabled,
        modifier = Modifier.size(48.dp)
            .background(if (active) colors.primary else colors.surface, CircleShape)
            .border(1.dp, if (active) colors.primary else colors.lineSoft, CircleShape)
            .semantics { if (active) stateDescription = status },
    ) {
        Icon(if (active) EolssuIcons.Stop else EolssuIcons.Speaker, description,
            Modifier.size(22.dp), tint = if (active) colors.onPrimary else colors.text)
    }
}

@Composable
internal fun ReadingAudioError(audio: ReadingAudioState, clips: List<ReadingAudioClip>) {
    if (audio.failed && audio.clip in clips) Text(
        stringResource(Res.string.reading_audio_failed), Modifier.fillMaxWidth().padding(top = 8.dp),
        color = EolssuTheme.colors.textMuted,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
    )
}
