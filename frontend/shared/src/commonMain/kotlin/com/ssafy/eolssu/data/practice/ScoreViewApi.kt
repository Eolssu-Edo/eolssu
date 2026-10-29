package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment

/**
 * 체류 기록 API(GRO-04, `POST /api/users/me/score-views`). 구간 키는 `Idempotency-Key` 헤더로 보낸다.
 * 구현([HttpScoreViewApi])이 없으면 [PracticeTimeQueue]는 기록을 기기에만 쌓아 둔다.
 */
interface ScoreViewApi {
    /**
     * 구간 하나를 보내고 응답 코드를 돌려준다. 응답을 받지 못하면(네트워크 오류·시간 초과) null.
     * 401이면 토큰 갱신을 이미 해 본 결과다(갱신은 HTTP 클라이언트가 맡는다).
     */
    suspend fun post(segment: PracticeTimeSegment): Int?
}
