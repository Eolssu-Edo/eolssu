package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.data.song.ReferenceAudioClip
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
    onOpenSource: () -> Unit,
    sourceLinkFailed: Boolean,
    onClose: () -> Unit,
) {
    val selection = state.selection ?: return
    val colors = EolssuTheme.colors
    val selectedIndex = state.clips.indexOfFirst { it.assetId == playback.sourceId }
    var sourceInfoOpen by remember(selection) { mutableStateOf(false) }
    val rawNoteLabel = stringResource(
        Res.string.reference_audio_note_label,
        selection.register.orEmpty(),
        selection.displayYulmyeong.orEmpty(),
    ).trim()
    val noteLabel = if (rawNoteLabel.isEmpty()) stringResource(Res.string.reference_audio_selected_note) else rawNoteLabel

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.heightIn(max = 560.dp),
        title = { Text(stringResource(Res.string.reference_audio_title, stringResource(selection.instrument.label))) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "filters") {
                        Column {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!state.allNotes, { onShowAll(false) }, label = { Text(noteLabel) })
                                FilterChip(state.allNotes, { onShowAll(true) }, label = {
                                    Text(stringResource(Res.string.reference_audio_all_notes))
                                })
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(null, "기본음계", "시김새").forEach { category ->
                                    val label = when (category) {
                                        "기본음계" -> Res.string.reference_audio_basic
                                        "시김새" -> Res.string.reference_audio_sigimsae
                                        else -> Res.string.reference_audio_all_categories
                                    }
                                    FilterChip(state.category == category, { onCategory(category) }, label = {
                                        Text(stringResource(label))
                                    })
                                }
                            }
                        }
                    }
                    item(key = "list_status") {
                        val message = when (state.loadState) {
                            ReferenceLoadState.Loading -> Res.string.guide_catalog_loading
                            ReferenceLoadState.Failed, ReferenceLoadState.NotRegistered -> Res.string.guide_catalog_failed
                            ReferenceLoadState.Ready -> if (state.clips.isEmpty()) {
                                if (state.allNotes) Res.string.reference_audio_empty_all else Res.string.reference_audio_empty_note
                            } else null
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (message != null) {
                                Text(
                                    stringResource(message),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            if (state.loadState == ReferenceLoadState.Failed) {
                                EolssuButton(stringResource(Res.string.guide_catalog_retry), onRetry, style = EolssuButtonStyle.Secondary)
                            }
                            if (state.loadState == ReferenceLoadState.Ready && state.clips.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.reference_audio_count, state.clips.size),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                    if (state.loadState == ReferenceLoadState.Ready) {
                        itemsIndexed(state.clips, key = { _, clip -> clip.assetId }) { index, clip ->
                            val isSelected = clip.assetId == playback.sourceId
                            Surface(
                                onClick = { onPlay(clip) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { selected = isSelected },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) colors.accentSoft else colors.surface,
                                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.lineSoft),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            referenceAudioTitle(clip),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            stringResource(
                                                Res.string.reference_audio_take_duration,
                                                stringResource(Res.string.reference_audio_take, index + 1),
                                                formatClock(clip.durationMs / 1000.0),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textMuted,
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            EolssuIcons.Check,
                                            contentDescription = stringResource(Res.string.reference_audio_selected),
                                            modifier = Modifier.size(20.dp),
                                            tint = colors.accentText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ReferenceAudioPlayerPanel(
                    clip = state.clips.getOrNull(selectedIndex),
                    takeNumber = selectedIndex + 1,
                    playback = playback,
                    onPlay = onPlay,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { sourceInfoOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.reference_audio_source_info), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        },
        confirmButton = {
            EolssuButton(stringResource(Res.string.guide_catalog_close), onClose, style = EolssuButtonStyle.Text, small = true)
        },
    )
    if (sourceInfoOpen) {
        AlertDialog(
            onDismissRequest = { sourceInfoOpen = false },
            modifier = Modifier.heightIn(max = 480.dp),
            title = { Text(stringResource(Res.string.reference_audio_source_info)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.reference_audio_source), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(Res.string.reference_audio_processing), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(Res.string.reference_audio_explanation), style = MaterialTheme.typography.bodySmall)
                    EolssuButton(stringResource(Res.string.reference_audio_source_open), onOpenSource, style = EolssuButtonStyle.Text, small = true)
                    if (sourceLinkFailed) {
                        Text(
                            stringResource(Res.string.reference_audio_source_failed),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                EolssuButton(stringResource(Res.string.guide_catalog_close), { sourceInfoOpen = false }, style = EolssuButtonStyle.Text, small = true)
            },
        )
    }
}
