package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.toSummary

/**
 * 공개 곡 목록·상세. `GET /api/songs`, `GET /api/songs/{songId}`에 대응한다.
 * 앱 DI는 [ApiSongRepository]를 쓰고 [SampleSongRepository]는 격리된 테스트에서만 사용한다.
 */
interface SongRepository {
    /** 모든 곡. 평가 가능 여부([Song.evaluable])는 화면이 거른다. */
    suspend fun getSongs(): List<SongSummary>

    suspend fun getSong(songId: String): Song?
}

/** 와이어프레임 예시 곡을 그대로 돌려준다. */
class SampleSongRepository : SongRepository {
    override suspend fun getSongs(): List<SongSummary> = SampleSongs.map { it.toSummary() }

    override suspend fun getSong(songId: String): Song? = SampleSongs.firstOrNull { it.id == songId }
}
