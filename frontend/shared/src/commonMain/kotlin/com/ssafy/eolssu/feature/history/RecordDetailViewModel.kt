package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.InputLimit
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.audio.UnavailableAudioPlayer
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.data.practice.RecordDeleteOutcome
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.RecordPlayback
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RECORD-02 상태. 녹음은 서버가 준 임시 주소(REC-03)로 재생하고, 재생 위치(ms)를 기록할 때의 속도로 정간에 놓는다.
 *
 * @property position 지금 듣는 음의 연주 순서.
 * @property notice 한 번 보여 줄 안내(스낵바). 보여 준 뒤 [RecordDetailViewModel.consumeNotice]로 지운다.
 * @property follow 재생 위치를 악보가 따라가는지. 사용자가 악보를 직접 넘기면 false가 되고 ‘지금 위치로’로 다시 켠다.
 * @property compareCandidates 이 기록보다 먼저 같은 곡을 처음부터 끝까지 연주한 기록(최신순). 기록 목록에서 찾는다.
 * @property comparePickOpen ‘지난 연주와 비교’로 연 기록 고르기 시트가 열려 있는지.
 * @property playbackLoading 녹음 주소를 받아 처음 재생을 준비하는 중(‘로딩 중’ 막).
 * @property memo 메모 창 상태. 창이 닫혀 있으면 null.
 */
data class RecordDetailUiState(
    val record: PracticeRecord? = null,
    val song: Song? = null,
    val position: Int = 0,
    val playbackPositionMs: Long = 0L,
    val audioDurationMs: Long? = null,
    val playing: Boolean = false,
    val filter: ErrorFilter = ErrorFilter.All,
    val detailLoaded: Boolean = false,
    val instrumentName: String? = null,
    val delete: RecordDeleteState = RecordDeleteState.Idle,
    val follow: Boolean = true,
    val songLookup: RecordSongLookup = RecordSongLookup.Ready,
    val notice: RecordNotice? = null,
    val compareCandidates: List<PracticeRecord> = emptyList(),
    val comparePickOpen: Boolean = false,
    val compareLoad: RecordCompareLoad = RecordCompareLoad.Ready,
    val playbackLoading: Boolean = false,
    val memo: RecordMemoState? = null,
) {
    val eventCount: Int get() = song?.timeline?.size ?: 0

    /** 녹음을 다시 들을 수 있을 때만 재생 버튼을 쓴다(S15P31E102-369: `recordingAvailable`이 false면 재생을 숨긴다). */
    val canPlay: Boolean get() = record?.recordingAvailable == true && delete != RecordDeleteState.Deleted

    val canPractice: Boolean get() = song != null && detailLoaded && delete != RecordDeleteState.Deleted

    /**
     * 기록·악보를 불러오는 중이라 ‘로딩 중’ 막을 보일 때(S15P31E102-810). 목록에서 가져온 기록만 있고
     * 상세를 받는 동안(악보 없음 · 아직 실패 아님)도 불러오는 중으로 본다.
     */
    val lookupLoading: Boolean get() = songLookup == RecordSongLookup.Loading ||
        (record != null && song == null && songLookup == RecordSongLookup.Ready)

    /** 메모는 지우지 않은 기록에서만 고친다. 메모가 없는 기록도 새로 적을 수 있다. */
    val canEditMemo: Boolean get() = record != null && delete != RecordDeleteState.Deleted && delete != RecordDeleteState.Deleting

    /** ‘지난 연주와 비교’는 곡 전체 연주 기록에만 있다. */
    val canCompare: Boolean get() = record?.section == null && record != null && delete != RecordDeleteState.Deleted

    /** 기록 고르기 시트의 바탕. 아직 비교 중인 기록이 없어 처음에는 바로 전 기록의 날을 보인다. */
    val comparePick: ComparePick? get() = record?.takeIf { canCompare }?.let { ComparePick(it, compareCandidates, selectedId = null) }

    /** ‘지금 위치로’ 버튼: 다시 들을 수 있는 기록에서 사용자가 악보를 넘겨 따라가기가 꺼졌을 때. */
    val showFollowNow: Boolean get() = canPlay && !follow

    val songTitle: String? get() = (song?.title ?: record?.songTitle)?.let { title ->
        instrumentName?.let { "$title · $it" } ?: title
    }

    /** 목록의 부분 데이터는 평가로 표시하지 않고, PRA-03 상세로 채운 결과만 공용 표시에 넘긴다. */
    val evaluation: EvaluationResult? get() = record?.takeIf { detailLoaded }?.let {
        EvaluationResult(
            id = it.id, songId = it.songId, total = it.score, feedback = "", metrics = it.metrics,
            errors = it.errors, bpm = it.bpm, recordedSeconds = it.recordedSeconds, recordedAt = it.recordedAt,
            sample = false, eventErrors = it.eventErrors, scoreVersion = it.scoreVersion, notes = it.notes,
        )
    }

    /** 정간 하나의 길이(ms). 기록할 때의 속도를 쓴다. */
    private val cellMillis: Double get() = song?.let { it.cellDurationMs(record?.bpm ?: it.baseBpm) } ?: 0.0

    fun eventMillis(index: Int = position): Double = (song?.timeline?.getOrNull(index)?.unitDuration ?: 0.0) * cellMillis

    /** [index] 음이 시작하는 녹음 위치(ms). 녹음은 첫 음에서 시작한다고 본다. */
    fun startMillis(index: Int = position): Long = ((song?.timeline?.getOrNull(index)?.unitStart ?: 0.0) * cellMillis).toLong()

    /** 지금 위치까지 흐른 시간(초). */
    val elapsedSeconds: Double get() = playbackPositionMs / 1000.0

    val durationMillis: Long get() = audioDurationMs?.takeIf { it > 0 }
        ?: ((record?.recordedSeconds ?: 0) * 1000L).coerceAtLeast(0L)

    /** [seconds] 시점에 소리 나는 음의 연주 순서. */
    fun indexAt(seconds: Double): Int {
        val timeline = song?.timeline ?: return 0
        if (cellMillis <= 0 || timeline.isEmpty()) return 0
        val unit = seconds * 1000 / cellMillis
        return timeline.indexOfLast { it.unitStart <= unit }.coerceIn(0, timeline.lastIndex)
    }
}

/**
 * 메모 창. 메모를 읽다가 연필 버튼으로 고치고 저장한다(REC-01 `PUT /practice-sessions/{sessionId}/record`).
 * 메모가 없는 기록도 읽기(빈 메모 안내)로 열고, 연필 버튼을 눌러야 고친다(S15P31E102-825).
 *
 * @property editing 입력칸으로 고치는 중. 연필 버튼([RecordDetailViewModel.editMemo])으로만 들어간다.
 * @property saving 저장 요청 중. 이때는 입력·취소·창 닫기를 막는다.
 * @property failed 바로 전 저장이 실패함. 적던 내용은 그대로 둔다.
 */
data class RecordMemoState(val editing: Boolean = false, val saving: Boolean = false, val failed: Boolean = false)

/** 서버가 받는 메모 최대 길이(REC-01, 넘으면 400). 입력칸도 이 길이까지만 받는다. */
const val RECORD_MEMO_MAX_LENGTH = InputLimit.MEMO

/** REC-01과 같이 유니코드 코드 포인트를 센다. UTF-16 서로게이트 쌍은 한 글자다. */
internal fun recordMemoLength(text: String): Int = InputLimit.length(text)

enum class RecordCompareLoad { Loading, Ready, Failed }

enum class RecordSongLookup { Loading, Ready, Missing, Failed }

enum class RecordDeleteState { Idle, Confirm, Deleting, Deleted, Busy, Failed }

/** 기록 상세의 한 번짜리 안내. */
enum class RecordNotice {
    /** 녹음이 없거나 지운 기록이라 다시 들을 수 없음. */
    PlaybackUnavailable,

    /** 서버가 지금 녹음을 내줄 수 없음(503). */
    PlaybackBusy,

    /** 녹음 주소를 받지 못했거나 재생하지 못함. */
    PlaybackFailed,

    /** 기록 상세(메모·점수·오류 위치)를 받지 못함. 목록의 값은 그대로 보인다. */
    DetailFailed,
}

/**
 * RECORD-02. 태블릿 가로의 두 칸 배치에서는 한 ViewModel이 [show]로 오른쪽 상세의 기록을 바꾼다.
 * 기록을 열면 서버 상세(PRA-03·EVA-01)를 받아 메모·점수·오류 위치·다시 듣기 여부를 채운다(처음 열면 서버가 NEW를 끈다).
 * 녹음 주소는 15분 동안 쓸 수 있어, 만료 30초 전부터는 재생할 때 다시 받는다.
 */
class RecordDetailViewModel(
    recordId: String,
    private val recordRepository: PracticeRecordRepository,
    private val songRepository: SongRepository,
    private val player: AudioPlayer = UnavailableAudioPlayer(),
    private val now: () -> Long = { 0L },
    member: SignedInMember? = null,
    private val loadInstruments: suspend () -> List<CatalogInstrument> = { emptyList() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()
    private var songJob: Job? = null
    private var detailJob: Job? = null
    private var playJob: Job? = null
    private var instrumentJob: Job? = null
    private var compareJob: Job? = null
    private var memoJob: Job? = null
    private var songRequest = 0L
    private var source: Pair<AudioSource, Long>? = null
    private var sourceRequest = 0
    private var currentId = ""
    private var selection = 0L

    private val accountScope = member.screenScope(viewModelScope) {
        source = null
        currentId = ""
        _uiState.value = RecordDetailUiState()
        viewModelScope.launch { player.stop() }
    }

    init {
        accountScope.launch { player.state.collect(::onPlayer) }
        show(recordId)
    }

    /**
     * [recordId] 기록을 보여준다. 다른 기록으로 바꾸면 재생을 멈추고 처음 위치로 돌린다.
     * 오류 종류 필터·악보 따라가기 설정은 그대로 둔다.
     */
    fun show(recordId: String) {
        if (!accountScope.isActive) return
        if (currentId == recordId) return
        currentId = recordId
        selection++
        stopPlayback()
        source = null
        songJob?.cancel()
        detailJob?.cancel()
        instrumentJob?.cancel()
        compareJob?.cancel()
        memoJob?.cancel()
        songRequest++
        if (recordId.isEmpty()) {
            _uiState.value = RecordDetailUiState()
            return
        }
        val record = recordRepository.detailPreview(recordId)
        _uiState.update { state ->
            state.copy(
                record = record, song = state.song?.takeIf { it.id == record?.songId && it.scoreVersion == record.scoreVersion }, position = 0, playing = false,
                playbackPositionMs = 0L, audioDurationMs = null,
                compareCandidates = record?.compareCandidates(recordRepository.records.value).orEmpty(),
                comparePickOpen = false, compareLoad = RecordCompareLoad.Ready, playbackLoading = false, follow = true, memo = null,
                detailLoaded = false, instrumentName = null, delete = RecordDeleteState.Idle, notice = null,
                songLookup = if (record == null) RecordSongLookup.Loading else RecordSongLookup.Ready,
            )
        }
        loadDetail(recordId)
        _uiState.value.song?.let(::loadInstrumentName)
    }

    private fun loadDetail(recordId: String) {
        if (!accountScope.isActive) return
        detailJob = accountScope.launch {
            val detail = try {
                recordRepository.detail(recordId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    it.copy(song = null, songLookup = RecordSongLookup.Failed,
                        notice = if (it.record == null) null else RecordNotice.DetailFailed)
                }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            if (detail == null) {
                _uiState.update { it.copy(record = null, song = null, songLookup = RecordSongLookup.Missing, playing = false) }
                return@launch
            }
            val retained = _uiState.value.song?.takeIf { it.id == detail.songId && it.scoreVersion == detail.scoreVersion }
            _uiState.update { it.copy(record = detail, song = null, detailLoaded = true, compareCandidates = detail.compareCandidates(recordRepository.records.value)) }
            if (retained == null) retrySong() else try {
                applySong(retained)
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(songLookup = RecordSongLookup.Failed) }
            } catch (error: IllegalStateException) {
                _uiState.update { it.copy(songLookup = RecordSongLookup.Failed) }
            }
        }
    }

    fun retrySong() {
        if (!accountScope.isActive) return
        // 목록 캐시나 이전 악보가 있어도 상세 실패는 상세부터 다시 요청한다.
        if (!_uiState.value.detailLoaded) {
            if (_uiState.value.songLookup == RecordSongLookup.Failed && detailJob?.isActive != true && currentId.isNotEmpty()) {
                _uiState.update { it.copy(songLookup = RecordSongLookup.Loading, notice = null) }
                loadDetail(currentId)
            }
            return
        }
        val record = _uiState.value.record ?: return
        if (songJob?.isActive == true || _uiState.value.song != null) return
        val request = ++songRequest
        _uiState.update { it.copy(songLookup = RecordSongLookup.Loading) }
        songJob = accountScope.launch {
            val song = try {
                (if (record.scoreVersion == null) songRepository.getSong(record.songId) else songRepository.getSong(record.songId, record.scoreVersion))?.also { song ->
                    check(song.id == record.songId && (record.scoreVersion == null || song.scoreVersion == record.scoreVersion))
                    _uiState.value.evaluation?.withScore(song)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    if (request == songRequest && it.record?.id == record.id) it.copy(songLookup = RecordSongLookup.Failed) else it
                }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            _uiState.update {
                if (request == songRequest && it.record?.id == record.id) {
                    it.copy(song = song, songLookup = if (song == null) RecordSongLookup.Missing else RecordSongLookup.Ready)
                } else it
            }
            if (request == songRequest && song != null) applySong(song)
        }
    }

    private fun applySong(song: Song) {
        val state = _uiState.value
        val record = state.record ?: return
        _uiState.update {
            val ready = it.copy(song = song, songLookup = RecordSongLookup.Ready, record = record.withScore(song))
            ready.copy(position = ready.indexAt(ready.playbackPositionMs / 1000.0))
        }
        loadInstrumentName(song)
    }

    private fun loadInstrumentName(song: Song) {
        val id = song.instrumentId ?: return
        instrumentJob?.cancel()
        instrumentJob = accountScope.launch {
            val name = try {
                loadInstruments().firstOrNull { it.instrumentId == id }?.name?.takeIf { it.isNotBlank() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            _uiState.update { if (it.song?.id == song.id) it.copy(instrumentName = name) else it }
        }
    }

    /** 재생·일시정지. 끝에서 재생하면 처음부터. 녹음을 다시 들을 수 없는 기록이면 아무것도 하지 않는다. */
    fun togglePlay() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        if (!state.canPlay) return
        if (state.playing) {
            pause()
            return
        }
        if (state.durationMillis > 0L && state.playbackPositionMs >= state.durationMillis) {
            _uiState.update { it.copy(position = 0, playbackPositionMs = 0L) }
        }
        // 재생을 시작하면 악보가 다시 재생 위치를 따라간다.
        _uiState.update { it.copy(follow = true) }
        playFrom(_uiState.value.playbackPositionMs)
    }

    /** 녹음 주소를 (필요하면 다시) 받아 [positionMs]부터 재생한다. */
    private fun playFrom(positionMs: Long) {
        if (!accountScope.isActive) return
        val record = _uiState.value.record ?: return
        playJob?.cancel()
        val cached = source?.takeIf { (s, expires) -> s.id.startsWith(record.id) && now() < expires - URL_MARGIN_MS }?.first
        // 녹음 주소를 새로 받는 처음 재생은 연습 화면처럼 ‘로딩 중’을 띄운다. 재생이 시작되거나 실패하면 내린다.
        _uiState.update { it.copy(playing = true, playbackLoading = cached == null) }
        playJob = accountScope.launch {
            val audio = cached ?: when (val playback = recordRepository.playback(record.id)) {
                is RecordPlayback.Ready -> AudioSource("${record.id}#${++sourceRequest}", playback.url)
                    .also {
                        source = it to playback.expiresAtMillis
                        _uiState.update { state -> state.copy(audioDurationMs = playback.durationMs?.takeIf { duration -> duration > 0L }) }
                    }
                RecordPlayback.Unavailable -> {
                    _uiState.update {
                        it.copy(playing = false, playbackLoading = false, notice = RecordNotice.PlaybackUnavailable, record = it.record?.copy(recordingAvailable = false))
                    }
                    return@launch
                }
                RecordPlayback.Busy -> {
                    _uiState.update { it.copy(playing = false, playbackLoading = false, notice = RecordNotice.PlaybackBusy) }
                    return@launch
                }
                RecordPlayback.Failed -> {
                    _uiState.update { it.copy(playing = false, playbackLoading = false, notice = RecordNotice.PlaybackFailed) }
                    return@launch
                }
            }
            currentCoroutineContext().ensureActive()
            player.load(audio, positionMs, 1f)
        }
    }

    private fun onPlayer(playback: AudioPlaybackState) {
        if (!accountScope.isActive) return
        val current = source?.first ?: return
        if (playback.sourceId != current.id) return
        if (playback.durationMs > 0L) _uiState.update { it.copy(audioDurationMs = playback.durationMs) }
        if (playback.status != AudioPlaybackStatus.Loading) _uiState.update { it.copy(playbackLoading = false) }
        when (playback.status) {
            AudioPlaybackStatus.Playing -> _uiState.update {
                it.copy(playing = true, playbackPositionMs = playback.positionMs.coerceAtLeast(0L), position = it.indexAt(playback.positionMs / 1000.0))
            }
            AudioPlaybackStatus.Completed -> _uiState.update {
                it.copy(playing = false, playbackPositionMs = it.durationMillis, position = (it.eventCount - 1).coerceAtLeast(0))
            }
            AudioPlaybackStatus.Paused, AudioPlaybackStatus.Interrupted -> _uiState.update {
                it.copy(playing = false, playbackPositionMs = playback.positionMs.coerceAtLeast(0L), position = it.indexAt(playback.positionMs / 1000.0))
            }
            AudioPlaybackStatus.Idle -> _uiState.update { it.copy(playing = false) }
            AudioPlaybackStatus.Failed, AudioPlaybackStatus.Unavailable -> _uiState.update {
                it.copy(playing = false, notice = if (it.playing) RecordNotice.PlaybackFailed else it.notice)
            }
            AudioPlaybackStatus.Loading -> Unit
        }
    }

    fun pause() {
        if (!accountScope.isActive) return
        playJob?.cancel()
        player.pause()
        _uiState.update { it.copy(playing = false, playbackLoading = false) }
    }

    private fun stopPlayback() {
        if (!accountScope.isActive) return
        playJob?.cancel()
        player.stop()
        _uiState.update { it.copy(playing = false, playbackLoading = false) }
    }

    /** 칸을 누르거나 재생 위치를 옮기면 그 음부터 듣는다. 재생 중이면 그 자리부터 이어 재생한다. */
    fun seek(index: Int) {
        if (!accountScope.isActive || !_uiState.value.canPlay) return
        val state = _uiState.value
        seekToMillis(state.startMillis(index.coerceIn(0, (state.eventCount - 1).coerceAtLeast(0))))
    }

    /** 슬라이더와 초 단위 이동은 음의 시작점으로 반올림하지 않는다. */
    fun seekToMillis(positionMs: Long) {
        if (!accountScope.isActive || !_uiState.value.canPlay) return
        _uiState.update {
            val bounded = if (it.durationMillis > 0L) positionMs.coerceIn(0L, it.durationMillis) else positionMs.coerceAtLeast(0L)
            it.copy(playbackPositionMs = bounded, position = it.indexAt(bounded / 1000.0))
        }
        val state = _uiState.value
        if (state.playing) {
            if (state.durationMillis > 0L && state.playbackPositionMs >= state.durationMillis) {
                // MediaPlayer는 파일 길이와 같은 위치의 load를 거부한다. 끝에서는 멈춘다.
                source = null
                pause()
            } else playFrom(state.playbackPositionMs)
        }
    }

    fun skipSeconds(delta: Int) = seekToMillis(_uiState.value.playbackPositionMs + delta * 1000L)

    /**
     * 정간을 누르면 그 음으로 재생 위치를 옮긴다. 재생 중이면 그 자리부터 이어 재생하고,
     * 멈춰 있으면 위치만 옮겨 두고 재생 버튼을 눌렀을 때 그 음부터 재생한다.
     */
    fun playAt(index: Int) {
        if (!accountScope.isActive || !_uiState.value.canPlay) return
        seek(index)
    }

    fun selectFilter(filter: ErrorFilter) = _uiState.update { it.copy(filter = filter) }

    fun askDelete() {
        if (!accountScope.isActive || _uiState.value.record == null) return
        _uiState.update { if (it.delete == RecordDeleteState.Idle) it.copy(delete = RecordDeleteState.Confirm) else it }
    }

    fun cancelDelete() = _uiState.update {
        if (it.delete == RecordDeleteState.Confirm) it.copy(delete = RecordDeleteState.Idle) else it
    }

    fun confirmDelete() {
        if (!accountScope.isActive || _uiState.value.delete != RecordDeleteState.Confirm) return
        val id = _uiState.value.record?.id ?: return
        val request = selection
        _uiState.update { it.copy(delete = RecordDeleteState.Deleting) }
        accountScope.launch {
            val outcome = try {
                recordRepository.delete(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RecordDeleteOutcome.Failed
            }
            currentCoroutineContext().ensureActive()
            if (currentId != id || request != selection) return@launch
            if (outcome == RecordDeleteOutcome.Deleted) {
                stopPlayback()
                source = null
            }
            _uiState.update {
                it.copy(delete = when (outcome) {
                    RecordDeleteOutcome.Deleted -> RecordDeleteState.Deleted
                    RecordDeleteOutcome.Busy -> RecordDeleteState.Busy
                    RecordDeleteOutcome.Failed -> RecordDeleteState.Failed
                })
            }
        }
    }

    fun dismissDeleteError() = _uiState.update {
        if (it.delete == RecordDeleteState.Busy || it.delete == RecordDeleteState.Failed) it.copy(delete = RecordDeleteState.Idle) else it
    }

    /** 사용자가 악보를 직접 넘기면 따라가기를 멈추고 ‘지금 위치로’를 보인다. */
    fun detachFollow() = _uiState.update { if (it.canPlay) it.copy(follow = false) else it }

    /** ‘지금 위치로’: 다시 재생 위치를 따라간다. */
    fun followNow() = _uiState.update { it.copy(follow = true) }

    /** ‘지난 연주와 비교’: 비교할 기록 고르기 시트를 연다. 재생은 멈춘다. */
    fun openComparePick() {
        val state = _uiState.value
        val record = state.record ?: return
        if (!accountScope.isActive || !state.canCompare || compareJob?.isActive == true) return
        pause()
        _uiState.update { it.copy(comparePickOpen = true, compareLoad = RecordCompareLoad.Loading) }
        compareJob = accountScope.launch {
            val candidates = try {
                recordRepository.recordsForSong(record.songId)?.let(record::compareCandidates)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            _uiState.update {
                if (candidates == null) it.copy(compareLoad = RecordCompareLoad.Failed)
                else it.copy(compareCandidates = candidates, compareLoad = RecordCompareLoad.Ready)
            }
        }
    }

    fun closeComparePick() {
        compareJob?.cancel()
        _uiState.update { it.copy(comparePickOpen = false) }
    }

    /** ‘메모’: 메모 창을 읽기로 연다. 메모가 없어도 읽기로 열며, 고칠 수 없는 기록의 빈 메모는 열지 않는다. */
    fun openMemo() {
        val state = _uiState.value
        val record = state.record ?: return
        if (!accountScope.isActive || state.memo != null) return
        if (record.memo.isBlank() && !state.canEditMemo) return
        _uiState.update { it.copy(memo = RecordMemoState()) }
    }

    /** 메모 창을 닫는다. 저장 중에는 닫지 않는다. */
    fun closeMemo() = _uiState.update { if (it.memo?.saving == true) it else it.copy(memo = null) }

    /** 연필 버튼: 메모를 고치기 시작한다. */
    fun editMemo() {
        if (!accountScope.isActive || !_uiState.value.canEditMemo) return
        _uiState.update { state -> state.memo?.let { state.copy(memo = it.copy(editing = true, failed = false)) } ?: state }
    }

    /** ‘취소’: 고치기를 그만두고 읽기로 돌아간다. 메모가 없던 기록도 같다. 저장 중에는 무시한다. */
    fun cancelMemoEdit() = _uiState.update { state ->
        val memo = state.memo ?: return@update state
        if (memo.saving) state else state.copy(memo = RecordMemoState())
    }

    /**
     * [memo]를 저장한다. 앞뒤 공백은 서버처럼 잘라 비교하고, 바뀌지 않았으면 요청 없이 고치기를 끝낸다.
     * 빈 값은 메모를 지운다. 성공하면 상세의 메모를 바꾸고(목록은 저장소가 알린다) 읽기로 돌아가며, 메모를 지웠으면 창을 닫는다.
     * 실패하면 적던 내용을 둔 채 실패를 보인다.
     */
    fun saveMemo(memo: String) {
        val state = _uiState.value
        val record = state.record ?: return
        val dialog = state.memo?.takeIf { it.editing } ?: return
        if (!accountScope.isActive || dialog.saving || !state.canEditMemo) return
        val text = memo.trim()
        if (recordMemoLength(text) > RECORD_MEMO_MAX_LENGTH) {
            _uiState.update { it.copy(memo = dialog.copy(failed = true)) }
            return
        }
        if (text == record.memo.trim()) {
            _uiState.update { it.copy(memo = if (text.isEmpty()) null else RecordMemoState()) }
            return
        }
        val request = selection
        _uiState.update { it.copy(memo = dialog.copy(saving = true, failed = false)) }
        memoJob = accountScope.launch {
            val saved = try {
                recordRepository.updateMemo(record.id, text)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            currentCoroutineContext().ensureActive()
            if (request != selection || currentId != record.id) return@launch
            // 서버가 돌려준 값으로 저장소 목록을 바꿨으면 그 값을 쓴다.
            val stored = recordRepository.records.value.firstOrNull { it.id == record.id }?.memo ?: text
            _uiState.update {
                if (!saved) it.copy(memo = it.memo?.copy(saving = false, failed = true))
                else it.copy(
                    memo = if (stored.isBlank()) null else RecordMemoState(),
                    record = it.record?.copy(memo = stored),
                )
            }
        }
    }

    fun consumeNotice() = _uiState.update { it.copy(notice = null) }

    override fun onCleared() {
        playJob?.cancel()
        songJob?.cancel()
        detailJob?.cancel()
        instrumentJob?.cancel()
        memoJob?.cancel()
        player.release()
    }

    private companion object {
        /** 녹음 주소 만료 전에 다시 받는 여유(ms). */
        const val URL_MARGIN_MS = 30_000L
    }
}
