package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel

/** 곡 목록의 난이도 필터. [level]이 null이면 전체. */
enum class SongLevelFilter(val level: SongLevel?) {
    All(null),
    Intro(SongLevel.Intro),
    Elementary(SongLevel.Elementary),
    Intermediate(SongLevel.Intermediate),
}

/**
 * SONG-01 상태. AI 연주 평가 흐름이라 평가 가능한 곡만 보인다.
 * 곡 상세에서 돌아와도 검색어·필터가 남도록 ViewModel이 들고 있다.
 */
data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val filter: SongLevelFilter = SongLevelFilter.All,
) {
    val visibleSongs: List<Song>
        get() {
            val q = query.trim()
            return songs.filter { song ->
                song.evaluable &&
                    (filter.level == null || song.level == filter.level) &&
                    (q.isEmpty() || song.title.contains(q))
            }
        }
}
