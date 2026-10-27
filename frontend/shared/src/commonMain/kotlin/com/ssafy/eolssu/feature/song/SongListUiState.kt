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
 *
 * @property selectedSongId 태블릿 가로 두 칸 배치의 오른쪽에 정보를 보일 곡. 곡을 받으면 보이는 첫 곡을 고른다.
 */
data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val filter: SongLevelFilter = SongLevelFilter.All,
    val selectedSongId: String? = null,
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

    /** 고른 곡. 검색·필터로 목록에서 빠지면 null이라 '곡을 골라주세요' 안내를 보인다. */
    val selectedSong: Song?
        get() = selectedSongId?.let { id -> visibleSongs.firstOrNull { it.id == id } }

    /** 곡 목록을 받는다. 아직 고른 곡이 없으면 보이는 첫 곡을 고른다. */
    fun receiveSongs(received: List<Song>): SongListUiState {
        val next = copy(songs = received)
        return if (selectedSongId != null) next else next.copy(selectedSongId = next.visibleSongs.firstOrNull()?.id)
    }

    fun selectSong(songId: String): SongListUiState = copy(selectedSongId = songId)
}
