package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.toSummary

/**
 * 공개 곡 목록·상세. `GET /api/songs`, `GET /api/songs/{songId}`에 대응한다.
 * 앱 DI는 [ApiSongRepository]를 쓰고 [SampleSongRepository]는 격리된 테스트에서만 사용한다.
 */
interface SongRepository {
    /** 활성 악기의 서버 ID·코드·이름. */
    suspend fun getInstruments(): List<CatalogInstrument>

    /** 선택한 악기의 곡(생략 시 전체). 평가 가능 여부([Song.evaluable])는 화면이 거른다. */
    suspend fun getSongs(instrumentId: String? = null): List<SongSummary>

    suspend fun getSong(songId: String): Song?

    /** 서버에서 현재 게시 판본을 확인한다. */
    suspend fun getPublishedScoreVersion(songId: String): Int? = getSong(songId)?.scoreVersion

    /** 같은 로그인에서 확인한 곡. 판본을 생략하면 직전에 확인한 게시 판본만 반환한다. */
    suspend fun retainedSong(songId: String, scoreVersion: Int? = null): Song? = null

    /** 기록을 만든 정확한 판본. 과거 판본을 현재 악보로 대체하지 않는다. */
    suspend fun getSong(songId: String, scoreVersion: Int): Song? {
        require(scoreVersion > 0)
        return getSong(songId)?.takeIf { it.scoreVersion == scoreVersion }
    }

    /** 편집기로 복사할 제공 곡과 공통 악보 원본(CAT-04). 원본을 주지 않는 저장소는 null이다. */
    suspend fun getEditableSong(songId: String): EditableSong? = null
}

/** 제공 곡 화면 모델과 그 곡의 공통 악보 원본. 편집기는 원본의 각·정간 배치와 시김새를 그대로 이어 받는다. */
data class EditableSong(val song: Song, val score: CommonScoreData)

/** 와이어프레임 예시 곡을 그대로 돌려준다. */
class SampleSongRepository : SongRepository {
    override suspend fun getInstruments() = emptyList<CatalogInstrument>()
    override suspend fun getSongs(instrumentId: String?): List<SongSummary> = SampleSongs.filter { instrumentId == null || it.instrumentId == instrumentId }.map { it.toSummary() }

    override suspend fun getSong(songId: String): Song? = SampleSongs.firstOrNull { it.id == songId }
}
