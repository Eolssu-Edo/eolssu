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
 * @property songPicked [selectedSongId]를 두 칸 배치에서 사용자가 직접 골랐는지. 곡을 받을 때 자동으로 고른 첫 곡은 false다.
 *   직접 고른 채 세로로 바뀌면 그 곡의 상세를 연다.
 */
data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val loadFailed: Boolean = false,
    val query: String = "",
    val filter: SongLevelFilter = SongLevelFilter.All,
    val selectedSongId: String? = null,
    val songPicked: Boolean = false,
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

    /** 세로로 바뀌면 상세를 열 곡. 직접 고른 곡이 목록에 보일 때만 있다. */
    val pickedSong: Song?
        get() = selectedSong?.takeIf { songPicked }

    /** 곡 목록을 받는다. 아직 고른 곡이 없으면 보이는 첫 곡을 고른다(직접 고른 것으로 보지 않는다). */
    fun receiveSongs(received: List<Song>): SongListUiState {
        val next = copy(songs = received)
        return if (selectedSongId != null) next else next.copy(selectedSongId = next.visibleSongs.firstOrNull()?.id)
    }

    /** 세로 목록에서 곡 상세로 가는 곡. 가로로 바뀌면 오른쪽에 보이지만 직접 고른 것으로 보지 않는다. */
    fun selectSong(songId: String): SongListUiState = copy(selectedSongId = songId, songPicked = false)

    /** 두 칸 배치에서 사용자가 직접 고른 곡. 곡 상세에서 가로로 바뀌어 돌아온 곡도 여기에 든다. */
    fun pickSong(songId: String): SongListUiState = copy(selectedSongId = songId, songPicked = true)

    /** 직접 고른 곡의 상세를 세로 화면으로 열었다. 상세에서 목록으로 돌아오면 다시 열지 않는다. */
    fun openPickedSong(): SongListUiState = copy(songPicked = false)
}
