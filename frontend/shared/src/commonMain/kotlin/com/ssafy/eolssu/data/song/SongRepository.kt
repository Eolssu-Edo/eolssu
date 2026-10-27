package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Song

/**
 * 곡 목록·상세. 와이어프레임 API 정리의 `GET /songs`, `GET /songs/{songId}`에 대응한다.
 * 지금은 [SampleSongRepository]가 예시 곡을 돌려주고, 곡 API가 생기면 같은 인터페이스의 API 구현으로 바꾼다.
 */
interface SongRepository {
    /** 모든 곡. 평가 가능 여부([Song.evaluable])는 화면이 거른다. */
    suspend fun getSongs(): List<Song>

    suspend fun getSong(songId: String): Song?
}

/** 와이어프레임 예시 곡을 그대로 돌려준다. */
class SampleSongRepository : SongRepository {
    override suspend fun getSongs(): List<Song> = SampleSongs

    override suspend fun getSong(songId: String): Song? = SampleSongs.firstOrNull { it.id == songId }
}
