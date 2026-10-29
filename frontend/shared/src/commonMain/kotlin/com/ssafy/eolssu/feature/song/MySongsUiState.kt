package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.Song

/** SONG-03 탭. 홈 '연습곡 선택하기'로 들어오면 제공 곡부터 보인다. */
enum class MySongsTab { Songs, Sheets }

/**
 * SONG-03 연습곡 고르기 상태. 제공 곡은 평가 가능 여부와 관계없이 모두 보이고, 누르면 평가 없는 연습 모드로 간다.
 *
 * @property sheets 내 악보. 아직 받지 않았으면 null.
 * @property savedSheet 이 화면을 떠나 있는 동안 새로 저장된 악보. 저장 안내를 띄운 뒤 비운다.
 * @property addSheetOnReturn 악보 추가 시트에서 PDF 올리기로 갔다. 저장하지 않고 돌아오면 시트를 다시 연다.
 *   시트는 다른 화면 위에 겹쳐 그려지므로 떠날 때 닫아 두고 돌아올 때 연다.
 */
data class MySongsUiState(
    val tab: MySongsTab = MySongsTab.Songs,
    val songs: List<Song> = emptyList(),
    val sheets: List<MySheet>? = null,
    val addSheetOpen: Boolean = false,
    val savedSheet: MySheet? = null,
    val addSheetOnReturn: Boolean = false,
) {
    val sheetCount: Int get() = sheets?.size ?: 0

    /** 내 악보 목록을 받는다. 처음 받은 뒤에 새 악보가 생기면 저장 안내를 띄우고 내 악보 탭을 보인다. */
    fun receiveSheets(received: List<MySheet>): MySongsUiState {
        val before = sheets ?: return copy(sheets = received)
        val known = before.mapTo(HashSet()) { it.id }
        val added = received.firstOrNull { it.id !in known } ?: return copy(sheets = received)
        return copy(sheets = received, savedSheet = added, tab = MySongsTab.Sheets, addSheetOpen = false, addSheetOnReturn = false)
    }

    /** 악보 추가 시트에서 PDF 올리기로 간다. */
    fun leaveForPdfUpload(): MySongsUiState = copy(addSheetOpen = false, addSheetOnReturn = true)

    /** 이 화면으로 돌아왔다. PDF 올리기에서 저장하지 않고 돌아왔으면 악보 추가 시트를 다시 연다. */
    fun returnToScreen(): MySongsUiState = if (addSheetOnReturn) copy(addSheetOpen = true, addSheetOnReturn = false) else this
}
