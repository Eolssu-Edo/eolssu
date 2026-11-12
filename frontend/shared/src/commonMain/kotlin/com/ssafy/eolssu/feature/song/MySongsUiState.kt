package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.SongSortOrder
import com.ssafy.eolssu.core.model.sortedByTitle
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionStatus

/** SONG-03 탭. 홈 '연습하기'로 들어오면 제공 곡부터, '악보 추가·편집하기'로 들어오면 내 악보부터 보인다. */
enum class MySongsTab { Songs, Sheets }

/**
 * SONG-03 연습곡 고르기 상태. 제공 곡은 평가 가능 여부와 관계없이 모두 보이고, 누르면 평가 없는 연습 모드로 간다.
 *
 * @property query 제목 검색어. 두 탭이 같은 검색어를 쓴다.
 * @property sheets 내 악보. 아직 받지 않았으면 null.
 * @property deleting 삭제를 묻는 창에 띄운 내 악보. 창이 닫혀 있으면 null.
 */
data class MySongsUiState(
    val tab: MySongsTab = MySongsTab.Songs,
    val loadState: SongLoadState = SongLoadState.Loading,
    val songs: List<SongSummary> = emptyList(),
    val sheets: List<MySheet>? = null,
    val deleting: MySheet? = null,
    val instrumentId: String? = null,
    val query: String = "",
    val filter: SongLevelFilter = SongLevelFilter.All,
    val sort: SongSortOrder = SongSortOrder.Ascending,
    val sheetLoadState: SongLoadState = SongLoadState.Loading,
    val deleteFailed: Boolean = false,
    val conversions: Map<String, SheetConversion> = emptyMap(),
    val retrying: Set<String> = emptySet(),
    val conversionNotice: ConversionNotice? = null,
) {
    val sheetCount: Int get() = sheets?.size ?: 0

    /**
     * 그 악보의 변환 상태. 변환 상태 조회(SHT-10)로 받은 값이 있으면 그것을, 없으면 목록(SHT-01)의 `conversionStatus`만 쓴다.
     * 불러오기로 만들지 않은 악보는 null이다.
     */
    fun conversionOf(sheet: MySheet): SheetConversion? =
        conversions[sheet.id] ?: SheetConversionStatus.fromCode(sheet.conversionStatus)?.let { SheetConversion(sheet.id, it) }

    /** 아직 변환 중이라 상태를 다시 물어야 하는 악보. */
    val convertingIds: List<String>
        get() = sheets.orEmpty().filter { conversionOf(it)?.status?.inProgress == true }.map { it.id }

    /** 목록에서 이미 실패한 악보도 원인별 안내를 위해 상세를 한 번 받는다. */
    val failedConversionIdsWithoutDetails: List<String>
        get() = sheets.orEmpty().filter {
            it.id !in conversions && SheetConversionStatus.fromCode(it.conversionStatus) == SheetConversionStatus.Failed
        }.map { it.id }

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

    /**
     * 내 악보 목록을 받는다. 삭제를 묻던 악보가 목록에서 빠졌으면 창도 닫는다.
     * 목록에서 빠진 악보의 변환 상태는 버리고, 목록이 이미 끝났다(DONE·FAILED)고 하는데 받아 둔 상태가 변환 중이면 목록을 믿는다.
     */
    fun receiveSheets(received: List<MySheet>): MySongsUiState {
        val byId = received.associateBy { it.id }
        val kept = conversions.filter { (id, conversion) ->
            val listed = byId[id] ?: return@filter false
            val listedStatus = SheetConversionStatus.fromCode(listed.conversionStatus)
            !(conversion.status.inProgress && listedStatus != null && !listedStatus.inProgress)
        }
        return copy(
            sheets = received,
            deleting = deleting?.takeIf { asked -> received.any { it.id == asked.id } },
            conversions = kept,
            retrying = retrying.filterTo(mutableSetOf()) { it in byId },
        )
    }
}

/** 변환 다시 시도 뒤 한 번 보일 안내. */
enum class ConversionNotice {
    /** 서버가 다시 시도할 수 없다고 했다(원본 미보관). 새 파일로 다시 불러와야 한다. */
    Reimport,

    /** 응답이 없거나 서버 오류라 다시 시도하지 못했다. */
    RetryFailed,
}
