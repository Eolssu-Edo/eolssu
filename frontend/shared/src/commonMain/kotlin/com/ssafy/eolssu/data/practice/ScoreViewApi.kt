package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment

/**
 * 체류 기록 API(GRO-04, `POST /api/users/me/score-views`). 구간 키는 `Idempotency-Key` 헤더로 보낸다.
 * HTTP 라이브러리가 정해지기 전이라 구현이 없다. 구현이 없으면 [PracticeTimeQueue]는 기록을 기기에만 쌓아 둔다.
 */
interface ScoreViewApi {
    /** 구간 하나를 보내고 응답 코드를 돌려준다. 응답을 받지 못하면(네트워크 오류·시간 초과) null. */
    suspend fun post(segment: PracticeTimeSegment): Int?

    /** 로그인 토큰을 갱신한다. 갱신했으면 true. */
    suspend fun refreshToken(): Boolean
}
