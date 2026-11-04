package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongSummary

/** SONG-03 탭. 홈 '연습곡 선택하기'로 들어오면 제공 곡부터, '악보 추가·편집하기'로 들어오면 내 악보부터 보인다. */
enum class MySongsTab { Songs, Sheets }

/**
 * SONG-03 연습곡 고르기 상태. 제공 곡은 평가 가능 여부와 관계없이 모두 보이고, 누르면 평가 없는 연습 모드로 간다.
 *
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
    val filter: SongLevelFilter = SongLevelFilter.All,
) {
    val sheetCount: Int get() = sheets?.size ?: 0

    /** 선택한 단계의 제공 곡. 평가 가능 여부로 제외하지 않고 서버 순서를 유지한다. */
    val visibleSongs: List<SongSummary>
        get() = songs.filter { filter.level == null || it.level == filter.level }

    /** 내 악보 목록을 받는다. 삭제를 묻던 악보가 목록에서 빠졌으면 창도 닫는다. */
    fun receiveSheets(received: List<MySheet>): MySongsUiState =
        copy(sheets = received, deleting = deleting?.takeIf { asked -> received.any { it.id == asked.id } })
}
