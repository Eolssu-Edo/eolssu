package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.feature.guide.FingeringUiState
import com.ssafy.eolssu.feature.guide.ReadingCatalogUiState
import com.ssafy.eolssu.feature.guide.ReferenceLoadState
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CatalogStatus(state: ReferenceLoadState, empty: Boolean = false, onRetry: () -> Unit) {
    val message = when (state) {
        ReferenceLoadState.Loading -> Res.string.guide_catalog_loading
        ReferenceLoadState.Failed -> Res.string.guide_catalog_failed
        ReferenceLoadState.NotRegistered -> Res.string.guide_catalog_not_registered
        ReferenceLoadState.Ready -> if (empty) Res.string.guide_catalog_empty else null
    }
    if (message != null) Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = EolssuTheme.colors.textMuted)
    if (state == ReferenceLoadState.Failed || state == ReferenceLoadState.NotRegistered) {
        EolssuButton(stringResource(Res.string.guide_catalog_retry), onRetry, style = EolssuButtonStyle.Secondary)
    }
}

/** 검증된 HTTPS 자료는 사용자 동작에서만 연다. 외부 앱이 없으면 오류를 화면에 남긴다. */
@Composable
internal fun CatalogLink(url: String?, label: String) {
    if (url == null) return
    val uriHandler = LocalUriHandler.current
    var failed by remember(url) { mutableStateOf(false) }
    EolssuButton(label, { failed = runCatching { uriHandler.openUri(url) }.isFailure }, style = EolssuButtonStyle.Secondary)
    if (failed) Text(stringResource(Res.string.guide_catalog_link_failed), color = EolssuTheme.colors.textMuted)
}

@Composable
internal fun FingeringCatalogDialog(state: FingeringUiState, onRetry: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.guide_catalog_fingering_title)) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CatalogStatus(state.catalogState, empty = state.catalogPitches.isEmpty(), onRetry = onRetry)
                if (state.catalogState == ReferenceLoadState.Ready) {
                    CatalogLink(state.catalogFingerings.structureImageUrl, stringResource(Res.string.guide_catalog_structure_image))
                    state.catalogPitches.forEach { pitch ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GuideText("${pitch.hanja} · ${pitch.hangul}", style = MaterialTheme.typography.titleMedium)
                            pitch.description?.takeIf { it.isNotBlank() }?.let { GuideText(it) }
                            CatalogLink(pitch.soundUrl, stringResource(Res.string.guide_catalog_open_sound))
                            state.catalogFingerings.items.filter { it.pitchId == pitch.pitchId }.forEach { fingering ->
                                fingering.description?.takeIf { it.isNotBlank() }?.let { GuideText(it) }
                                CatalogLink(fingering.imageUrl, stringResource(Res.string.guide_catalog_fingering_image))
                            }
                        }
                    }
                    if (state.catalogFingerings.items.isEmpty()) Text(stringResource(Res.string.guide_catalog_no_fingerings), color = EolssuTheme.colors.textMuted)
                }
            }
        },
        confirmButton = { EolssuButton(stringResource(Res.string.guide_catalog_close), onClose, style = EolssuButtonStyle.Text, small = true) },
    )
}

@Composable
internal fun ReadingCatalogDialog(
    state: ReadingCatalogUiState,
    playback: AudioPlaybackState,
    playbackAvailable: Boolean,
    onPlayJangdan: (String) -> Unit,
    onJangdansRetry: () -> Unit,
    onSymbolsRetry: () -> Unit,
    onSelectJangdan: (String) -> Unit,
    onDetailRetry: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.guide_catalog_title)) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.guide_catalog_jangdans), style = MaterialTheme.typography.titleMedium)
                CatalogStatus(state.jangdansState, empty = state.jangdans.isEmpty(), onRetry = onJangdansRetry)
                state.jangdans.forEach { jangdan ->
                    EolssuButton(jangdan.name, { onSelectJangdan(jangdan.jangdanId) }, style = EolssuButtonStyle.Secondary)
                    if (state.selectedJangdanId != jangdan.jangdanId) return@forEach
                    CatalogStatus(state.detailState, onRetry = onDetailRetry)
                    state.detail?.let { detail ->
                        Text(detail.name, style = MaterialTheme.typography.titleMedium)
                        val unit = if (detail.bpmUnit == "DOTTED_QUARTER") Res.string.guide_catalog_dotted_quarter else Res.string.guide_catalog_quarter
                        Text(stringResource(Res.string.guide_catalog_bpm, detail.defaultBpm, stringResource(unit)))
                        GuideText(detail.pattern.beats.joinToString(" · "))
                        Text(stringResource(Res.string.guide_catalog_groups, detail.pattern.group.joinToString(" + ")))
                        detail.source?.takeIf { it.isNotBlank() }?.let { GuideText(it, style = MaterialTheme.typography.bodySmall) }
                        when {
                            detail.soundUrl == null -> GuideText(stringResource(Res.string.guide_catalog_no_jangdan_sound))
                            !playbackAvailable -> GuideText(stringResource(Res.string.reference_audio_unavailable))
                            else -> {
                                val status = playback.status.takeIf { playback.sourceId == detail.jangdanId }
                                val label = when (status) {
                                    AudioPlaybackStatus.Loading -> Res.string.reference_audio_cancel
                                    AudioPlaybackStatus.Playing -> Res.string.guide_catalog_stop_sound
                                    AudioPlaybackStatus.Completed -> Res.string.reference_audio_replay
                                    else -> Res.string.reference_audio_play
                                }
                                EolssuButton(stringResource(label), { onPlayJangdan(detail.jangdanId) }, style = EolssuButtonStyle.Secondary)
                                when (status) {
                                    AudioPlaybackStatus.Loading -> GuideText(stringResource(Res.string.reference_audio_loading_sound))
                                    AudioPlaybackStatus.Failed -> GuideText(stringResource(Res.string.reference_audio_failed_sound))
                                    AudioPlaybackStatus.Interrupted -> GuideText(stringResource(Res.string.reference_audio_interrupted))
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
                Text(stringResource(Res.string.guide_catalog_symbols), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                CatalogStatus(state.symbolsState, empty = state.symbols.isEmpty(), onRetry = onSymbolsRetry)
                state.symbols.forEach { symbol ->
                    GuideText("${symbol.symbol} · ${symbol.name}", style = MaterialTheme.typography.titleMedium)
                    GuideText(symbol.description)
                    when (symbol.descriptionStatus) {
                        "INFERRED" -> GuideText(stringResource(Res.string.guide_catalog_symbol_inferred))
                        "EDUCATIONAL" -> GuideText(stringResource(Res.string.guide_catalog_symbol_educational))
                    }
                    symbol.source?.let { GuideText(it, style = MaterialTheme.typography.bodySmall) }
                    symbol.imageLicense?.let { Text(stringResource(Res.string.guide_catalog_symbol_license, it), style = MaterialTheme.typography.bodySmall) }
                    CatalogLink(symbol.imageUrl, stringResource(Res.string.guide_catalog_symbol_image))
                }
            }
        },
        confirmButton = { EolssuButton(stringResource(Res.string.guide_catalog_close), onClose, style = EolssuButtonStyle.Text, small = true) },
    )
}
