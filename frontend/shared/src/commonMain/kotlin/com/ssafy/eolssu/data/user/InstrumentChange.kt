package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument

/**
 * 연습 악기를 바꾼다. 활성 악기 목록에서 서버 ID를 찾아 회원 정보를 고치고, 서버가 반영한 뒤에만 [instruments]의 선택을 바꾼다.
 * 세션이 끝났으면([MemberResult.Unauthenticated]) 로그아웃하고 악기를 미확인으로 돌린다.
 * 마이페이지(MY-01)와 평가할 곡 고르기(SONG-01) · 연습곡 고르기(SONG-03)의 악기 드롭다운이 함께 쓴다.
 */
suspend fun UserRepository.changePracticeInstrument(instruments: InstrumentRepository, instrument: Instrument): MemberResult {
    val id = instruments.serverIdFor(instrument) ?: return MemberResult.Unavailable
    val result = changeInstrument(id)
    if (result == MemberResult.Unauthenticated) {
        logOut()
        instruments.clear()
        return result
    }
    if (result != MemberResult.Done) return result
    if (profile.value.instrumentId != id) return MemberResult.Unavailable
    instruments.select(instrument)
    return MemberResult.Done
}
