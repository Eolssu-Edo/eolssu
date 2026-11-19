package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.CatalogUnreachable

/** [Offline]은 서버에 닿지 못한 실패(연결 실패·시간 초과), [Failed]는 그 밖의 실패(서버 오류·잘못된 응답)다. */
enum class SongLoadState { Loading, Ready, Failed, Offline, NotFound }

/** 곡 조회 실패를 화면 상태로 바꾼다. 연결 실패만 [SongLoadState.Offline]으로 나눈다. */
internal fun loadFailure(error: Exception): SongLoadState =
    if (error is CatalogUnreachable) SongLoadState.Offline else SongLoadState.Failed
