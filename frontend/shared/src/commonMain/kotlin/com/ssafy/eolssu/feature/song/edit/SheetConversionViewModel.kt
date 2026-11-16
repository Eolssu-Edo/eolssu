package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionLookup
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionStage
import com.ssafy.eolssu.data.song.SheetConversionStatus
import com.ssafy.eolssu.feature.song.CONVERSION_POLL_DEFAULT_MS
import com.ssafy.eolssu.feature.song.ConversionFailureGuide
import com.ssafy.eolssu.feature.song.conversionFailureGuide
import com.ssafy.eolssu.feature.song.conversionProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * 악보 변환 대기 화면 상태(S15P31E102-998).
 *
 * @property conversion 마지막으로 받은 변환 상태(SHT-10). 아직 받지 못했으면 null.
 * @property title 내 악보 목록에서 찾은 제목. 모르면 null.
 * @property readingElapsedMs 이 화면에서 '읽는 중'을 처음 본 뒤 지난 시간. 진행 막대 어림에 쓴다.
 * @property unreachable 상태 조회가 여러 번 이어서 실패했다. 계속 다시 묻는다.
 * @property missing 악보가 없다(404). 지워졌거나 다른 계정이다.
 * @property openEditor 완료돼 편집 화면으로 갈 차례. 화면이 이동한 뒤 [SheetConversionViewModel.editorOpened]로 지운다.
 */
data class SheetConversionUiState(
    val conversion: SheetConversion? = null,
    val title: String? = null,
    val readingElapsedMs: Long = 0,
    val unreachable: Boolean = false,
    val missing: Boolean = false,
    val openEditor: Boolean = false,
) {
    val done: Boolean get() = conversion?.status == SheetConversionStatus.Done

    /** 실패했으면 원인별 안내, 아니면 null. */
    val failure: ConversionFailureGuide?
        get() = conversion?.takeIf { it.status == SheetConversionStatus.Failed }?.let { conversionFailureGuide(it.failure, it.notation) }

    /** 진행 막대 값(0~1). 완료는 1, 실패·모름은 null. */
    val progress: Float?
        get() = when {
            done -> 1f
            else -> conversion?.let { conversionProgress(it, readingElapsedMs) }
        }
}

/**
 * 접수한 변환 하나의 상태를 화면이 보이는 동안 `pollAfterMs`마다 묻는다([watch]·[stop]).
 * 완료되면 앱 전체 완료 알림을 거두고([SheetConversionRepository.acknowledgeCompletion]) 편집 화면으로 보낸다.
 * 실패·404에서 묻기를 멈춘다. 응답이 없으면 계속 다시 묻고 [UNREACHABLE_AFTER]번 이어지면 안내한다.
 */
class SheetConversionViewModel(
    private val sheetId: String,
    private val repository: SheetConversionRepository,
    mySheetRepository: MySheetRepository? = null,
    member: SignedInMember? = null,
    private val clockMs: () -> Long = monotonicMs(),
) : ViewModel() {
    private val state = MutableStateFlow(SheetConversionUiState())
    val uiState: StateFlow<SheetConversionUiState> = state.asStateFlow()
    private val scope = member.screenScope(viewModelScope) { state.value = SheetConversionUiState(missing = true) }
    private var pollJob: Job? = null
    private var watching = false
    private var readingSince: Long? = null
    private var misses = 0

    init {
        if (mySheetRepository != null) scope.launch {
            mySheetRepository.sheets.collect { sheets ->
                sheets.firstOrNull { it.id == sheetId }?.let { sheet -> state.update { it.copy(title = sheet.title) } }
            }
        }
    }

    /** 화면이 보이면 묻기 시작한다. */
    fun watch() {
        watching = true
        start()
    }

    /** 화면이 가려지면 더 묻지 않는다. 변환은 서버에서 계속된다. */
    fun stop() {
        watching = false
        pollJob?.cancel()
        pollJob = null
    }

    /** 화면이 편집으로 이동했다. */
    fun editorOpened() = state.update { it.copy(openEditor = false) }

    private fun finished(current: SheetConversionUiState) = current.missing || current.failure != null || current.done

    private fun start() {
        if (!watching || pollJob?.isActive == true || !scope.isActive || finished(state.value)) return
        pollJob = scope.launch {
            while (isActive) {
                val lookup = try { repository.status(sheetId) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { SheetConversionLookup.Unavailable }
                if (!isActive) return@launch
                val wait = receive(lookup)
                if (finished(state.value)) break
                delay(wait.coerceIn(POLL_MIN_MS, POLL_MAX_MS))
            }
        }
    }

    /** 응답 하나를 반영하고 다음 조회까지 기다릴 시간을 돌려준다. */
    private fun receive(lookup: SheetConversionLookup): Long = when (lookup) {
        is SheetConversionLookup.Found -> {
            misses = 0
            val conversion = lookup.conversion
            val reading = conversion.status == SheetConversionStatus.Running && conversion.stage != SheetConversionStage.BuildingScore
            val now = clockMs()
            if (reading && readingSince == null) readingSince = now
            val elapsed = readingSince?.takeIf { reading }?.let { now - it } ?: 0
            if (conversion.status == SheetConversionStatus.Done) repository.acknowledgeCompletion(sheetId)
            state.update {
                it.copy(conversion = conversion, readingElapsedMs = elapsed, unreachable = false,
                    openEditor = conversion.status == SheetConversionStatus.Done)
            }
            conversion.pollAfterMs ?: CONVERSION_POLL_DEFAULT_MS
        }
        SheetConversionLookup.NotFound -> {
            state.update { it.copy(missing = true) }
            CONVERSION_POLL_DEFAULT_MS
        }
        SheetConversionLookup.Unavailable -> {
            misses++
            state.update { it.copy(unreachable = misses >= UNREACHABLE_AFTER) }
            CONVERSION_POLL_DEFAULT_MS
        }
    }

    companion object {
        /** 조회 실패가 이만큼 이어지면 연결 안내를 띄운다. */
        const val UNREACHABLE_AFTER = 3
        private const val POLL_MIN_MS = 1_000L
        private const val POLL_MAX_MS = 30_000L

        private fun monotonicMs(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}
