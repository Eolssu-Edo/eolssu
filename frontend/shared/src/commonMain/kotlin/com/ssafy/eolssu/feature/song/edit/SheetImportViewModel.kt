package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.InputLimit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.model.instrumentDisplayRank
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.core.storage.*
import com.ssafy.eolssu.data.song.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SheetImportViewModel(
    kind: ImportKind, private val hwang: HwangPitch,
    private val repository: SheetImportRepository,
    private val loadInstruments: suspend () -> List<CatalogInstrument>,
    private val loadJangdans: suspend () -> List<CatalogJangdanSummary>,
    member: SignedInMember? = null,
    private val authoringCatalog: SheetAuthoringCatalog? = null,
) : ViewModel() {
    private val state = MutableStateFlow(SheetImportUiState(kind = kind))
    val uiState = state.asStateFlow()
    private var key: String? = null
    private val scope = member.screenScope(viewModelScope) {
        key = null
        state.value = SheetImportUiState(kind = state.value.kind, loading = false, expired = true)
    }

    init { reload() }

    fun reload() {
        if (state.value.expired || state.value.busy) return
        state.value = state.value.copy(loading = true, catalogFailed = false, error = null)
        scope.launch {
            try {
                val instruments = (authoringCatalog?.instruments() ?: loadInstruments()).sortedBy { instrumentDisplayRank(it.code) }
                // 올리기 화면에서 정간보·오선보를 바꿀 수 있으므로 장단 목록은 종류와 상관없이 받아 둔다.
                // 오선보로 시작했으면 장단을 못 받아도 올리기를 막지 않고, 정간보로 바꿀 때 다시 받는다([kind]).
                val jeongganbo = state.value.kind == ImportKind.Jeongganbo
                val jangdans = try { jeongganboUploadJangdans(authoringCatalog?.references?.jangdans() ?: loadJangdans()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { if (jeongganbo) throw failure else emptyList() }
                check(instruments.isNotEmpty() && (!jeongganbo || jangdans.isNotEmpty()))
                ensureAccountSession()
                state.value = state.value.copy(instruments = instruments, jangdans = jangdans, loading = false)
                authoringCatalog?.warmUp()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(loading = false, catalogFailed = true) }
        }
    }

    private fun editable() = !state.value.busy && !state.value.expired && state.value.acceptedId == null
    private fun edit(value: SheetImportUiState) { key = null; state.value = value.copy(error = null) }
    fun title(value: String) { if (editable() && value != state.value.title) edit(state.value.copy(title = value)) }
    /** 올리기 화면 위 '악보 종류'. 고른 장단은 오선보로 바꿔도 남겨 두고, 접수할 때만 정간보에 싣는다. */
    fun kind(value: ImportKind) {
        if (!editable() || value == state.value.kind) return
        edit(state.value.copy(kind = value))
        if (value == ImportKind.Jeongganbo && state.value.jangdans.isEmpty() && !state.value.loading) reload()
    }
    fun instrument(id: String) {
        if (editable() && id != state.value.instrumentId && state.value.instruments.any { it.instrumentId == id }) edit(state.value.copy(instrumentId = id))
    }
    fun jangdan(id: String) {
        val current = state.value
        if (editable() && (id != current.jangdanId || current.jangdanUnsure) && current.jangdans.any { it.jangdanId == id })
            edit(current.copy(jangdanId = id, jangdanUnsure = false))
    }
    /** 장단을 모를 때 고르는 '잘 모르겠어요'. 추천 장단 굿거리로 올린다(S15P31E102-1054). */
    fun jangdanUnsure() {
        val current = state.value
        if (editable() && !current.jangdanUnsure && current.canChooseJangdanUnsure)
            edit(current.copy(jangdanId = RECOMMENDED_JANGDAN_ID, jangdanUnsure = true))
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
                // 형식을 모르는 파일이 섞였으면 형식 안내 창, 형식은 맞지만 개수·조합이 틀리면 화면 안 문구로 알린다.
                if (result.files.any { it.contentType == null }) state.value = state.value.copy(error = "UNSUPPORTED_FILE_TYPE")
                else if (!validSheetFiles(result.files)) state.value = state.value.copy(error = "INVALID_FILE_SET")
                else edit(state.value.copy(files = result.files,
                    title = state.value.title.ifBlank { fileTitle(result.files.first().name) }))
            }
        }
    }

    /** 안내 창으로 보인 오류를 닫는다. 고른 파일·입력은 그대로 둔다. */
    fun dismissError() { if (state.value.error != null) state.value = state.value.copy(error = null) }

    @OptIn(ExperimentalUuidApi::class)
    fun submit() {
        val current = state.value
        if (!editable() || current.picking || current.loading || current.catalogFailed) return
        val jeongganbo = current.kind == ImportKind.Jeongganbo
        val input = SheetImportInput(current.title, if (jeongganbo) "JEONGGANBO" else "STAFF",
            current.instrumentId.orEmpty(), current.jangdanId.takeIf { jeongganbo },
            if (jeongganbo) null else hwang.name, current.files)
        if (!input.valid()) { state.value = current.copy(error = "INPUT_REQUIRED", validationRequested = true); return }
        val submissionKey = key ?: Uuid.random().toString().also { key = it }
        state.value = current.copy(busy = true, error = null)
        scope.launch {
            val result = upload(input, submissionKey)
            // 로그인 변경에 늦게 온 대역/플랫폼 결과도 새 화면에 노출하지 않는다.
            if (state.value.expired) return@launch
            state.value = when (result) {
                is SheetImportResult.Accepted -> state.value.copy(busy = false, acceptedId = result.sheetId, files = emptyList())
                is SheetImportResult.Failed -> state.value.copy(busy = false, error = result.code ?: "UPLOAD_FAILED")
            }
        }
    }

    private suspend fun upload(input: SheetImportInput, submissionKey: String): SheetImportResult =
        try { repository.upload(input, submissionKey) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SheetImportResult.Failed() }
}

/** 파일 이름으로 채울 제목: 확장자를 빼고 앞뒤 공백을 자른 뒤 [InputLimit.SHEET_TITLE]자까지(이모지를 반으로 자르지 않는다, S15P31E102-996). */
internal fun fileTitle(fileName: String): String {
    val name = fileName.substringBeforeLast('.').trim()
    return name.substring(0, InputLimit.endOfFirst(name, InputLimit.SHEET_TITLE)).trim()
}
