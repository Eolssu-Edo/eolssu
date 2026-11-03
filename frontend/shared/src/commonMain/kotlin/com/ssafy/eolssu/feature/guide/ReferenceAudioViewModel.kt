package com.ssafy.eolssu.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.song.ReferenceAudioClip
import com.ssafy.eolssu.data.song.ReferenceAudioQuery
import com.ssafy.eolssu.data.song.ReferenceAudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 표시 율명과 원본 라벨을 구분한다. 문서에 근거가 있는 악기·취법별 대응만 녹음 검색에 사용한다. */
data class ReferenceAudioSelection(val instrument: GuideInstrument, val register: String?, val yulmyeong: String,
    val displayYulmyeong: String = yulmyeong) {
    val datasetId: String get() = when (instrument) {
        GuideInstrument.Danso -> "danso"
        GuideInstrument.Sogeum -> "sogeum"
        GuideInstrument.DaegeumJeongak -> "jeongak-daegeum"
        GuideInstrument.DaegeumSanjo -> "sanjo-daegeum"
    }

    companion object {
        fun forNote(instrument: GuideInstrument, note: FingeringNote, blow: Blow? = null): ReferenceAudioSelection {
            val exactBlow = blow ?: instrument.blows.singleOrNull { note in instrument.notesByPitch(it) }
            return ReferenceAudioSelection(instrument, when (exactBlow) {
                Blow.Low -> "저취"
                Blow.Mid -> "평취"
                Blow.High -> "역취"
                null -> null
            }, recordingLabel(instrument, exactBlow, note.reading), note.reading)
        }
    }
}

/** docs/knowledge/data/{danso,jeongak-daegeum,sanjo-daegeum,sogeum}.md의 음역표에 한정한다. */
private fun recordingLabel(instrument: GuideInstrument, blow: Blow?, reading: String): String {
    val aliases = when (instrument) {
        GuideInstrument.Danso -> when (blow) {
            Blow.High -> mapOf("청중" to "중", "청임" to "임", "청무" to "무", "청황" to "황", "청태" to "태")
            else -> emptyMap()
        }
        GuideInstrument.DaegeumJeongak, GuideInstrument.DaegeumSanjo -> when (blow) {
            Blow.Low -> mapOf("탁임" to "임", "탁남" to "남", "탁무" to "무")
            Blow.Mid -> mapOf("청황" to "황", "청태" to "태", "청고" to "고", "청중" to "중")
            Blow.High -> mapOf("청임" to "임", "청남" to "남", "청무" to "무", "중청황" to "황")
            null -> emptyMap()
        }
        GuideInstrument.Sogeum -> when (blow) {
            Blow.Mid -> mapOf("청황" to "황", "청태" to "태", "청협" to "협", "청고" to "고", "청중" to "중")
            // 원본의 가장 높은 임은 중청임이다. 청임→임과 합치지 않고, 없는 중청남도 추정하지 않는다.
            Blow.High -> mapOf("청임" to "임", "청남" to "남", "청무" to "무", "중청황" to "황",
                "중청태" to "태", "중청고" to "고", "중청중" to "중")
            else -> emptyMap()
        }
    }
    return aliases[reading] ?: reading
}

data class ReferenceAudioUiState(
    val instrument: GuideInstrument? = null,
    val loadState: ReferenceLoadState = ReferenceLoadState.Loading,
    val clips: List<ReferenceAudioClip> = emptyList(),
    val selectedAssets: Map<ReferenceAudioSelection, String> = emptyMap(),
) {
    fun clipsFor(selection: ReferenceAudioSelection): List<ReferenceAudioClip> = clips.filter {
        it.datasetId == selection.datasetId && it.register == selection.register && it.yulmyeong == selection.yulmyeong
    }
    fun selectedClip(selection: ReferenceAudioSelection): ReferenceAudioClip? =
        clipsFor(selection).firstOrNull { it.assetId == selectedAssets[selection] }
}

/** 한 화면에서 악기 목록을 공유하고, 카드 선택만으로 재생하지 않는다. */
class ReferenceAudioViewModel(private val repository: ReferenceAudioRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ReferenceAudioUiState())
    val uiState = mutableState.asStateFlow()
    private var request: Job? = null

    fun open(instrument: GuideInstrument) {
        mutableState.value = ReferenceAudioUiState(instrument = instrument)
        retry()
    }

    fun nextOption(selection: ReferenceAudioSelection) {
        val state = mutableState.value
        if (state.loadState != ReferenceLoadState.Ready || state.instrument != selection.instrument) return
        val options = state.clipsFor(selection)
        if (options.isEmpty()) return
        val current = options.indexOfFirst { it.assetId == state.selectedAssets[selection] }
        val next = options[(current + 1) % options.size]
        mutableState.value = state.copy(selectedAssets = state.selectedAssets + (selection to next.assetId))
    }

    fun retry() {
        val instrument = mutableState.value.instrument ?: return
        val query = ReferenceAudioQuery(ReferenceAudioSelection(instrument, null, "").datasetId)
        request?.cancel()
        mutableState.value = mutableState.value.copy(loadState = ReferenceLoadState.Loading, clips = emptyList(), selectedAssets = emptyMap())
        request = viewModelScope.launch {
            try {
                val clips = repository.clips(query)
                currentCoroutineContext().ensureActive()
                if (mutableState.value.instrument == instrument) mutableState.value = mutableState.value.copy(loadState = ReferenceLoadState.Ready, clips = clips)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (mutableState.value.instrument == instrument) mutableState.value = mutableState.value.copy(loadState = ReferenceLoadState.Failed)
            }
        }
    }

    fun dismiss() {
        request?.cancel()
        request = null
        mutableState.value = ReferenceAudioUiState()
    }
}
