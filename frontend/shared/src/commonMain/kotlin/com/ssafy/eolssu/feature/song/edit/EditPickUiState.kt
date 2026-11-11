package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.data.song.PublishedSheetCopyProblem
import com.ssafy.eolssu.feature.song.SongSortOrder
import com.ssafy.eolssu.feature.song.sortedByTitle

/** 불러올 악보 목록의 탭. 화면에 이 순서(제공 곡 → 내 악보)로 놓는다. */
enum class EditPickTab { Songs, Mine }

/** 목록에서 고른 악보. */
sealed interface EditPickTarget {
    data class Sheet(val sheetId: String) : EditPickTarget
    data class Song(val songId: String) : EditPickTarget
}

/** 제공 곡 한 줄에 필요한 값. */
data class EditPickSong(val id: String, val title: String, val level: SongLevel, val jangdanName: String)

/** 고른 악기의 악보 목록을 받는 상태. 악기를 고르기 전에는 목록을 보이지 않는다. */
enum class EditPickLoad { NeedsInstrument, Loading, Ready, Failed }

/**
 * 불러올 악보 고르기(EditPick) 상태. 먼저 악기를 고르면 그 악기의 내 악보·제공 곡만 보이고,
 * 줄을 누르면 고르기만 하며(다시 누르면 풀림) 오른쪽 위 '선택'으로 편집에 간다. 제공 곡 탭에서만 곡 이름으로 찾는다.
 *
 * @property instrument 고른 악기. 연습 악기 설정과 따로 두며 바꿔도 회원 설정을 바꾸지 않는다.
 * @property sheets 고른 악기로 만든 내 악보. 내 악보에는 정간보만 둔다(2차 결정).
 * @property uncheckedSheets 악보를 읽지 못해 악기를 확인하지 못한 내 악보 수. 다른 악기 목록에 섞지 않는다.
 */
data class EditPickUiState(
    val instrument: Instrument? = null,
    val load: EditPickLoad = EditPickLoad.NeedsInstrument,
    val tab: EditPickTab = EditPickTab.Songs,
    val query: String = "",
    val sort: SongSortOrder = SongSortOrder.Ascending,
    val picked: EditPickTarget? = null,
    val sheets: List<MySheet> = emptyList(),
    val songs: List<EditPickSong> = emptyList(),
    val uncheckedSheets: Int = 0,
    val copying: Boolean = false,
    val copyProblem: PublishedSheetCopyProblem? = null,
) {
    /** 검색어가 이름에 든 제공 곡을 [sort] 순서로. 검색어 앞뒤 공백은 무시한다. */
    val visibleSongs: List<EditPickSong>
        get() = query.trim()
            .let { q -> if (q.isEmpty()) songs else songs.filter { it.title.contains(q, ignoreCase = true) } }
            .sortedByTitle(sort) { it.title }

    /** 고른 악보의 이름. 고르지 않았으면 null. */
    val pickedTitle: String?
        get() = when (val target = picked) {
            is EditPickTarget.Sheet -> sheets.firstOrNull { it.id == target.sheetId }?.title
            is EditPickTarget.Song -> songs.firstOrNull { it.id == target.songId }?.title
            null -> null
        }

    val canSelect: Boolean get() = load == EditPickLoad.Ready && !copying && pickedTitle != null

    /** 같은 줄을 다시 누르면 고름을 푼다. */
    fun toggle(target: EditPickTarget): EditPickUiState = copy(picked = if (picked == target) null else target, copyProblem = null)

    /** 악기를 바꾸면 이전 악기의 목록과 고른 악보를 비우고 새로 받는다. 편집 전이라 버릴 작업은 없다. */
    fun chooseInstrument(next: Instrument): EditPickUiState =
        copy(instrument = next, load = EditPickLoad.Loading, picked = null, sheets = emptyList(), songs = emptyList(), uncheckedSheets = 0, copyProblem = null)
}
