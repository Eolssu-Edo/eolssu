package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.core.model.sortedByTitle
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.instrumentOfServerCode
import com.ssafy.eolssu.data.song.SheetConversion

/** 내 악보 탭에서 같은 악기로 묶은 악보. [instrument]가 null이면 앱이 모르는 악기이거나 악기 정보가 없는 악보다. */
data class SheetGroup(val instrument: Instrument?, val sheets: List<MySheet>)

/** SONG-03 탭. 홈 '연습하기'로 들어오면 제공 곡부터, '악보 추가·편집하기'로 들어오면 내 악보부터 보인다. */
enum class MySongsTab { Songs, Sheets }

/**
 * SONG-03 연습곡 고르기 상태. 제공 곡은 평가 가능 여부와 관계없이 모두 보이고, 누르면 평가 없는 연습 모드로 간다.
 *
 * @property query 제목 검색어. 두 탭이 같은 검색어를 쓴다.
 * @property mine 내 악보와 변환 상태. 악보 추가 화면과 같은 묶음([MySheetList])을 쓴다.
 * @property deleting 삭제를 묻는 창에 띄운 내 악보. 창이 닫혀 있으면 null.
 * @property practiceInstrument 상단 바 드롭다운의 연습 악기. 내 악보는 이 악기의 악보를 먼저 보인다. 아직 모르면 null.
 * @property otherSheetsOpen 사용자가 '다른 악기 악보 보기'를 펼쳤는지. 연습 악기가 바뀌면 다시 접는다.
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
    val practiceInstrument: Instrument? = null,
    val otherSheetsOpen: Boolean = false,
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

    /** 제목 검색에 맞는 내 악보. 악기와 관계없이 서버 목록 순서를 따른다. */
    val visibleSheets: List<MySheet>
        get() {
            val q = query.trim()
            return sheets.orEmpty().filter { it.title.contains(q) }
        }

    /** [visibleSheets] 중 [practiceInstrument]의 악보. 연습 악기를 아직 모르면 나누지 않고 모두 여기에 둔다. */
    val ownSheets: List<MySheet>
        get() = practiceInstrument?.let { mine -> visibleSheets.filter { it.instrumentCode == mine.serverCode } } ?: visibleSheets

    /** 검색과 관계없이 [practiceInstrument]의 악보가 하나라도 있는지. 없을 때와 검색에 걸러졌을 때의 안내 문구를 가른다. */
    val hasOwnSheets: Boolean
        get() = practiceInstrument?.let { mine -> sheets.orEmpty().any { it.instrumentCode == mine.serverCode } } ?: (sheetCount > 0)

    /**
     * [visibleSheets] 중 [practiceInstrument]가 아닌 악보를 악기별로 묶는다. 묶음은 [Instrument] 선언 순서를 따르고,
     * 앱이 모르는 악기나 악기 정보가 없는 악보는 맨 뒤 한 묶음([SheetGroup.instrument] = null)에 모은다.
     */
    val otherSheetGroups: List<SheetGroup>
        get() {
            val mine = practiceInstrument ?: return emptyList()
            return visibleSheets.filter { it.instrumentCode != mine.serverCode }
                .groupBy { instrumentOfServerCode(it.instrumentCode) }
                .entries.sortedBy { (instrument, _) -> instrument?.ordinal ?: Instrument.entries.size }
                .map { (instrument, grouped) -> SheetGroup(instrument, grouped) }
        }

    val otherSheetCount: Int get() = otherSheetGroups.sumOf { it.sheets.size }

    /** 다른 악기 묶음을 보이는지. 연습 악기 악보가 (검색 결과에) 없으면 펼치지 않아도 보인다. */
    val otherSheetsShown: Boolean get() = otherSheetCount > 0 && (otherSheetsOpen || ownSheets.isEmpty())

    /** 내 악보 목록을 받는다([MySheetList.receive]). 삭제를 묻던 악보가 목록에서 빠졌으면 창도 닫는다. */
    fun receiveSheets(received: List<MySheet>): MySongsUiState = copy(
        mine = mine.receive(received),
        deleting = deleting?.takeIf { asked -> received.any { it.id == asked.id } },
    )
}
