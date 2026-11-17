package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.feature.song.MySheetConversionWatcher
import com.ssafy.eolssu.feature.song.SongLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * EDIT-01 악보 추가. 고른 가져오기 종류를 기기 선택 창·인식 화면으로 넘길 때까지 들고 있고,
 * 아래 내 악보 목록과 변환 진행 상황을 내 악보 탭과 같은 방식([MySheetConversionWatcher])으로 보인다.
 * [mySheetRepository]가 없으면 목록 없이 가져오기만 둔다.
 */
class EditNewViewModel(
    private val mySheetRepository: MySheetRepository? = null,
    conversionRepository: SheetConversionRepository? = null,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditNewUiState())
    val uiState: StateFlow<EditNewUiState> = _uiState.asStateFlow()

    private var sheetJob: Job? = null

    private val accountScope: CoroutineScope = member.screenScope(viewModelScope) {
        _uiState.update { EditNewUiState(kind = it.kind) }
        watcher?.forgetFailedDetails()
    }

    private val watcher: MySheetConversionWatcher? = mySheetRepository?.let { repository ->
        MySheetConversionWatcher(
            accountScope, repository, conversionRepository,
            read = { _uiState.value.mine },
            write = { change -> _uiState.update { it.copy(mine = change(it.mine)) } },
        )
    }

    init {
        val repository = mySheetRepository
        if (repository != null) accountScope.launch {
            repository.sheets.collect { sheets ->
                _uiState.update { state ->
                    state.copy(
                        mine = state.mine.receive(sheets),
                        deleting = state.deleting?.takeIf { asked -> sheets.any { it.id == asked.id } },
                    )
                }
                watcher?.onSheetsReceived()
            }
        }
    }

    fun selectKind(kind: ImportKind) = _uiState.update { it.copy(kind = kind) }

    fun openDevicePick() = _uiState.update { it.copy(devicePickOpen = true) }

    fun closeDevicePick() = _uiState.update { it.copy(devicePickOpen = false) }

    /** 내 악보 목록을 새로 받는다. 화면이 다시 보일 때와 '다시 시도'에서 부른다. */
    fun refreshSheets() {
        val repository = mySheetRepository ?: return
        if (!accountScope.isActive || sheetJob?.isActive == true) return
        watcher?.forgetFailedDetails()
        sheetJob = accountScope.launch {
            _uiState.update { it.copy(sheetLoad = SongLoadState.Loading) }
            val ok = repository.refresh()
            _uiState.update { it.copy(sheetLoad = if (ok) SongLoadState.Ready else SongLoadState.Failed) }
            watcher?.start()
        }
    }

    /** 화면이 보이는 동안 변환 중인 악보의 상태를 묻는다. */
    fun watchConversions() { watcher?.watch() }

    fun stopWatchingConversions() { watcher?.stop() }

    /** 실패한 변환의 '다시 시도'(SHT-14). */
    fun retryConversion(sheet: MySheet) { watcher?.retry(sheet) }

    fun consumeConversionNotice() = _uiState.update { it.copy(mine = it.mine.copy(notice = null)) }

    /** 휴지통: 삭제를 묻는 창을 연다. */
    fun askDelete(sheet: MySheet) = _uiState.update { it.copy(deleting = sheet) }

    fun cancelDelete() = _uiState.update { it.copy(deleting = null) }

    /** 창의 '삭제': 묻던 악보를 지운다. 목록은 [MySheetRepository.sheets]로 다시 받는다. */
    fun confirmDelete() {
        val repository = mySheetRepository ?: return
        val sheet = _uiState.value.deleting ?: return
        _uiState.update { it.copy(deleting = null, deleteFailed = false) }
        accountScope.launch {
            try { repository.delete(sheet.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(deleteFailed = true) } }
        }
    }

    fun consumeDeleteFailure() = _uiState.update { it.copy(deleteFailed = false) }
}
