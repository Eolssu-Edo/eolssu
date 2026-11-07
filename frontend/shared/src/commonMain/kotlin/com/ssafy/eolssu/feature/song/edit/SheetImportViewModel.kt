package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.core.storage.*
import com.ssafy.eolssu.data.song.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SheetImportViewModel(
    private val kind: ImportKind, private val hwang: HwangPitch,
    private val repository: SheetImportRepository,
    private val loadInstruments: suspend () -> List<CatalogInstrument>,
    private val loadJangdans: suspend () -> List<CatalogJangdanSummary>,
    member: SignedInMember? = null,
) : ViewModel() {
    private val state = MutableStateFlow(SheetImportUiState())
    val uiState = state.asStateFlow()
    private var key: String? = null
    private val scope = member.screenScope(viewModelScope) {
        key = null
        state.value = SheetImportUiState(loading = false, expired = true)
    }

    init { reload() }

    fun reload() {
        if (state.value.expired || state.value.busy) return
        state.value = state.value.copy(loading = true, catalogFailed = false, error = null)
        scope.launch {
            try {
                val instruments = loadInstruments()
                val jangdans = if (kind == ImportKind.Jeongganbo) loadJangdans() else emptyList()
                check(instruments.isNotEmpty() && (kind != ImportKind.Jeongganbo || jangdans.isNotEmpty()))
                state.value = state.value.copy(instruments = instruments, jangdans = jangdans, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(loading = false, catalogFailed = true) }
        }
    }

    private fun editable() = !state.value.busy && !state.value.expired && state.value.acceptedId == null
    private fun edit(value: SheetImportUiState) { key = null; state.value = value.copy(error = null) }
    fun title(value: String) { if (editable() && value != state.value.title) edit(state.value.copy(title = value)) }
    fun instrument(id: String) {
        if (editable() && id != state.value.instrumentId && state.value.instruments.any { it.instrumentId == id }) edit(state.value.copy(instrumentId = id))
    }
    fun jangdan(id: String) {
        if (editable() && id != state.value.jangdanId && state.value.jangdans.any { it.jangdanId == id }) edit(state.value.copy(jangdanId = id))
    }
    fun beginPicking(): Boolean {
        if (!editable() || state.value.picking) return false
        state.value = state.value.copy(picking = true, error = null)
        return true
    }
    fun selectFiles(result: SheetFileSelection) {
        if (!editable()) return
        state.value = state.value.copy(picking = false)
        when (result) {
            SheetFileSelection.Cancelled -> Unit
            SheetFileSelection.Failed -> state.value = state.value.copy(error = "PICK_FAILED")
            SheetFileSelection.TooLarge -> state.value = state.value.copy(error = "FILE_TOO_LARGE")
            is SheetFileSelection.Selected -> {
                if (!validSheetFiles(result.files)) state.value = state.value.copy(error = "UNSUPPORTED_FILE_TYPE")
                else edit(state.value.copy(files = result.files,
                    title = state.value.title.ifBlank { result.files.first().name.substringBeforeLast('.').take(50) }))
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun submit() {
        val current = state.value
        if (!editable() || current.picking || current.loading || current.catalogFailed) return
        val input = SheetImportInput(current.title, if (kind == ImportKind.Jeongganbo) "JEONGGANBO" else "STAFF",
            current.instrumentId.orEmpty(), current.jangdanId,
            if (kind == ImportKind.Staff) hwang.name else null, current.files)
        if (!input.valid()) { state.value = current.copy(error = "INPUT_REQUIRED"); return }
        val submissionKey = key ?: Uuid.random().toString().also { key = it }
        state.value = current.copy(busy = true, error = null)
        scope.launch {
            val result = try { repository.upload(input, submissionKey) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { SheetImportResult.Failed() }
            // 로그인 변경에 늦게 온 대역/플랫폼 결과도 새 화면에 노출하지 않는다.
            if (state.value.expired) return@launch
            state.value = when (result) {
                is SheetImportResult.Accepted -> state.value.copy(busy = false, acceptedId = result.sheetId, files = emptyList())
                is SheetImportResult.Failed -> state.value.copy(busy = false, error = result.code ?: "UPLOAD_FAILED")
            }
        }
    }
}
