package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.core.model.sortedByTitle
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.data.song.SheetConversion

/** SONG-03 탭. 홈 '연습하기'로 들어오면 제공 곡부터, '악보 추가·편집하기'로 들어오면 내 악보부터 보인다. */
enum class MySongsTab { Songs, Sheets }

/**
 * SONG-03 연습곡 고르기 상태. 제공 곡은 평가 가능 여부와 관계없이 모두 보이고, 누르면 평가 없는 연습 모드로 간다.
 *
 * @property query 제목 검색어. 두 탭이 같은 검색어를 쓴다.
 * @property mine 내 악보와 변환 상태. 악보 추가 화면과 같은 묶음([MySheetList])을 쓴다.
 * @property deleting 삭제를 묻는 창에 띄운 내 악보. 창이 닫혀 있으면 null.
 */
data class MySongsUiState(
    val tab: MySongsTab = MySongsTab.Songs,
    val loadState: SongLoadState = SongLoadState.Loading,
    val songs: List<SongSummary> = emptyList(),
    val mine: MySheetList = MySheetList(),
    val deleting: MySheet? = null,
    val instrumentId: String? = null,
    val query: String = "",
    val filter: SongLevelFilter = SongLevelFilter.All,
    val sort: SongSortOrder = SongSortOrder.Ascending,
    val sheetLoadState: SongLoadState = SongLoadState.Loading,
    val deleteFailed: Boolean = false,
) {
    /** 내 악보. 아직 받지 않았으면 null. */
    val sheets: List<MySheet>? get() = mine.sheets
    val conversions: Map<String, SheetConversion> get() = mine.conversions
    val retrying: Set<String> get() = mine.retrying
    val conversionNotice: ConversionNotice? get() = mine.notice

    val sheetCount: Int get() = sheets?.size ?: 0

    /** [MySheetList.conversionOf]. */
    fun conversionOf(sheet: MySheet): SheetConversion? = mine.conversionOf(sheet)

    val convertingIds: List<String> get() = mine.convertingIds

    val failedConversionIdsWithoutDetails: List<String> get() = mine.failedConversionIdsWithoutDetails

    /** 선택한 단계에서 제목에 [query]가 든 제공 곡을 [sort] 순서로. 평가 가능 여부로 제외하지 않는다. */
    val visibleSongs: List<SongSummary>
        get() {
            val q = query.trim()
            return songs.filter { (filter.level == null || it.level == filter.level) && (q.isEmpty() || it.title.contains(q)) }
                .sortedByTitle(sort) { it.title }
        }

    /** 제목에 [query]가 든 내 악보. 순서는 받은 목록 그대로다. */
    val visibleSheets: List<MySheet>
        get() {
            val q = query.trim()
            val all = sheets.orEmpty()
            return if (q.isEmpty()) all else all.filter { it.title.contains(q) }
        }

    /** 내 악보 목록을 받는다([MySheetList.receive]). 삭제를 묻던 악보가 목록에서 빠졌으면 창도 닫는다. */
    fun receiveSheets(received: List<MySheet>): MySongsUiState = copy(
        mine = mine.receive(received),
        deleting = deleting?.takeIf { asked -> received.any { it.id == asked.id } },
    )
}
