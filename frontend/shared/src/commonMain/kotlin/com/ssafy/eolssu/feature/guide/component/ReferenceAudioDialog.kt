package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ssafy.eolssu.core.audio.*
import com.ssafy.eolssu.data.song.ReferenceAudioClip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.feature.guide.ReferenceAudioUiState
import com.ssafy.eolssu.feature.guide.ReferenceLoadState
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReferenceAudioDialog(
    state: ReferenceAudioUiState,
    playback: AudioPlaybackState,
    onPlay: (ReferenceAudioClip) -> Unit,
    onShowAll: (Boolean) -> Unit,
    onCategory: (String?) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val selection = state.selection ?: return
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.reference_audio_title, stringResource(selection.instrument.label))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.reference_audio_explanation), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!state.allNotes, { onShowAll(false) }, label = {
                        Text(listOfNotNull(selection.register, selection.displayYulmyeong).joinToString(" · "))
                    })
                    FilterChip(state.allNotes, { onShowAll(true) }, label = { Text(stringResource(Res.string.reference_audio_all_notes)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, "기본음계", "시김새").forEach { category ->
                        FilterChip(state.category == category, { onCategory(category) }, label = {
                            Text(category ?: stringResource(Res.string.reference_audio_all_categories))
                        })
                    }
                }
                val message = when (state.loadState) {
                    ReferenceLoadState.Loading -> Res.string.guide_catalog_loading
                    ReferenceLoadState.Failed, ReferenceLoadState.NotRegistered -> Res.string.guide_catalog_failed
                    ReferenceLoadState.Ready -> if (state.clips.isEmpty()) {
                        if (state.allNotes) Res.string.reference_audio_empty_all else Res.string.reference_audio_empty_note
                    } else null
                }
                if (message != null) Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                if (state.loadState == ReferenceLoadState.Failed) {
                    EolssuButton(stringResource(Res.string.guide_catalog_retry), onRetry, style = EolssuButtonStyle.Secondary)
                }
                if (state.loadState == ReferenceLoadState.Ready && state.clips.isNotEmpty()) {
                    Text(stringResource(Res.string.reference_audio_count, state.clips.size), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(state.clips, key = { _, clip -> clip.assetId }) { index, clip ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${clip.technique} · ${clip.displayLabel}", style = MaterialTheme.typography.titleSmall)
                                Text("${clip.category} · ${clip.register} · ${clip.yulmyeong}", style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(Res.string.reference_audio_take, index + 1), style = MaterialTheme.typography.bodySmall)
                                val selected = playback.sourceId == clip.assetId
                                val active = selected && playback.status in setOf(AudioPlaybackStatus.Loading, AudioPlaybackStatus.Playing)
                                EolssuButton(stringResource(if (active) Res.string.reference_audio_stop else Res.string.reference_audio_play), { onPlay(clip) }, style = EolssuButtonStyle.Secondary)
                                if (selected && playback.status == AudioPlaybackStatus.Loading) Text(stringResource(Res.string.reference_audio_loading_sound))
                                if (selected && playback.status == AudioPlaybackStatus.Failed) Text(stringResource(Res.string.reference_audio_failed_sound), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { EolssuButton(stringResource(Res.string.guide_catalog_close), onClose, style = EolssuButtonStyle.Text, small = true) },
    )
}
