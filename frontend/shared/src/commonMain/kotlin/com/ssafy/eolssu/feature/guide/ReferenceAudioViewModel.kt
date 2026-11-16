package com.ssafy.eolssu.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.LocalAudioFile
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

/** 음마다 들려주는 예시음 종류. 깊은요성은 요성과 다른 주법이라 어느 쪽에도 넣지 않는다. */
enum class ReferenceAudioKind {
    Basic, Yoseong;

    fun matches(technique: String): Boolean = when (this) {
        Basic -> "요성" !in technique
        Yoseong -> "요성" in technique && "깊은요성" !in technique
    }
}

/** 이 접두사는 국립국악원 원음의 음높이를 가공한 예시용 라벨에만 쓴다. */
internal val ReferenceAudioClip.isPitchEdited: Boolean
    get() = labelId.startsWith("eolssu-603-") || labelId.startsWith("eolssu-620-")

data class ReferenceAudioUiState(
    val instrument: GuideInstrument? = null,
    val loadState: ReferenceLoadState = ReferenceLoadState.Loading,
    val clips: List<ReferenceAudioClip> = emptyList(),
) {
    fun clipsFor(selection: ReferenceAudioSelection): List<ReferenceAudioClip> = clips.filter {
        it.datasetId == selection.datasetId && it.register == selection.register && it.yulmyeong == selection.yulmyeong
    }

    /** 같은 음·주법의 실연 녹음을 우선한다. 강·중·약이 함께 있으면 강을 쓴다. */
    fun clipFor(selection: ReferenceAudioSelection, kind: ReferenceAudioKind): ReferenceAudioClip? {
        return clipsFor(selection).filter { it.category == "기본음계" && kind.matches(it.technique) }
            .minWithOrNull(compareBy<ReferenceAudioClip> { it.isPitchEdited }
                .thenByDescending { "강" in it.technique.split(' ') })
    }
}

/** 화면을 떠날 때까지 악기별 음원 목록을 재사용한다. */
class ReferenceAudioViewModel(private val repository: ReferenceAudioRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ReferenceAudioUiState())
    val uiState = mutableState.asStateFlow()
    private var request: Job? = null
    private val loadedClips = mutableMapOf<GuideInstrument, List<ReferenceAudioClip>>()
    /** 기기에 받아 둔 예시음 파일(S15P31E102-1005). 화면을 떠나면 비우고, 다시 오면 기기 파일을 확인해 채운다. */
    private val localFiles = mutableMapOf<String, LocalAudioFile>()

    /** 받아 둔 파일. 없으면 null이고 원격 주소로 재생한다. */
    fun localFile(clip: ReferenceAudioClip): LocalAudioFile? = localFiles[clip.assetId]

    /** 재생에 실패한 기기 파일은 다음 재생에서 원격 주소로 받는다. */
    fun forgetLocalFile(clip: ReferenceAudioClip) {
        localFiles.remove(clip.assetId)
    }

    /** 보이는 음의 예시음을 미리 받는다. 부른 쪽이 화면에서 사라져 취소되면 받기를 멈춘다. */
    suspend fun prefetch(clip: ReferenceAudioClip) {
        if (clip.assetId in localFiles) return
        val file = try { repository.localFile(clip) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (file != null && mutableState.value.clips.any { it.assetId == clip.assetId }) localFiles[clip.assetId] = file
    }

    /** 원격으로 재생한 예시음을 다음에 바로 낼 수 있게 받아 둔다. */
    fun keepForNext(clip: ReferenceAudioClip) {
        if (clip.assetId !in localFiles) viewModelScope.launch { prefetch(clip) }
    }

    fun open(instrument: GuideInstrument) {
        val state = mutableState.value
        // 목록의 조회 범위는 같은 악기 전체라 취법·학습 단계가 바뀌어도 다시 받지 않는다.
        if (state.instrument == instrument && state.loadState in setOf(ReferenceLoadState.Loading, ReferenceLoadState.Ready)) return
        request?.cancel()
        val cached = loadedClips[instrument]
        mutableState.value = ReferenceAudioUiState(
            instrument = instrument,
            loadState = if (cached != null) ReferenceLoadState.Ready else ReferenceLoadState.Loading,
            clips = cached.orEmpty(),
        )
        if (cached == null) retry()
    }

    fun retry() {
        val instrument = mutableState.value.instrument ?: return
        if (request?.isActive == true) return
        val query = ReferenceAudioQuery(ReferenceAudioSelection(instrument, null, "").datasetId)
        loadedClips.remove(instrument)
        mutableState.value = mutableState.value.copy(loadState = ReferenceLoadState.Loading, clips = emptyList())
        request = viewModelScope.launch {
            try {
                val clips = repository.clips(query)
                currentCoroutineContext().ensureActive()
                if (mutableState.value.instrument == instrument) {
                    loadedClips[instrument] = clips
                    mutableState.value = mutableState.value.copy(loadState = ReferenceLoadState.Ready, clips = clips)
                }
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
        loadedClips.clear()
        localFiles.clear()
        mutableState.value = ReferenceAudioUiState()
    }
}
