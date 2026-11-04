package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongLevel

/** 불러올 악보 목록의 탭. */
enum class EditPickTab { Mine, Songs }

/** 목록에서 고른 악보. */
sealed interface EditPickTarget {
    data class Sheet(val sheetId: String) : EditPickTarget
    data class Song(val songId: String) : EditPickTarget
}

/** 제공 곡 한 줄에 필요한 값. */
data class EditPickSong(val id: String, val title: String, val level: SongLevel, val jangdanName: String)

/**
 * 불러올 악보 고르기(EditPick) 상태. 줄을 누르면 고르기만 하고(다시 누르면 풀림), 오른쪽 위 '선택'으로 편집(Edit-Copy)에 간다.
 * 제공 곡 탭에서만 곡 이름으로 찾는다.
 *
 * @property sheets 내 악보. 내 악보에는 정간보만 둔다(2차 결정).
 */
data class EditPickUiState(
    val tab: EditPickTab = EditPickTab.Mine,
    val query: String = "",
    val picked: EditPickTarget? = null,
    val sheets: List<MySheet> = emptyList(),
    val songs: List<EditPickSong> = emptyList(),
) {
    /** 검색어가 이름에 든 제공 곡. 검색어 앞뒤 공백은 무시한다. */
    val visibleSongs: List<EditPickSong>
        get() = query.trim().let { q -> if (q.isEmpty()) songs else songs.filter { it.title.contains(q, ignoreCase = true) } }

    /** 고른 악보의 이름. 고르지 않았으면 null. */
    val pickedTitle: String?
        get() = when (val target = picked) {
            is EditPickTarget.Sheet -> sheets.firstOrNull { it.id == target.sheetId }?.title
            is EditPickTarget.Song -> songs.firstOrNull { it.id == target.songId }?.title
            null -> null
        }

    val canSelect: Boolean get() = pickedTitle != null

    /** 같은 줄을 다시 누르면 고름을 푼다. */
    fun toggle(target: EditPickTarget): EditPickUiState = copy(picked = if (picked == target) null else target)
}
