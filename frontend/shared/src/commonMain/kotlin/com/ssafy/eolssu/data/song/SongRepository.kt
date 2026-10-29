package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Song

/**
 * 곡 목록·상세(`GET /api/songs`, `GET /api/songs/{songId}`). 앱은 [HttpSongRepository]를 쓰고,
 * [SampleSongRepository]는 예시 곡을 돌려준다(테스트와 예시 기록이 쓴다).
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
