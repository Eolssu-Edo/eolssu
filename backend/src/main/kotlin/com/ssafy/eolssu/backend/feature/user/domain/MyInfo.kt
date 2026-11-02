package com.ssafy.eolssu.backend.feature.user.domain

import java.time.Instant

/** 내 정보(USR-01). 마이페이지에 보일 본인 이메일은 담고 비밀번호 해시는 담지 않는다. */
class MyInfo(val userId: Long, val email: String?, val nickname: String, val instrumentId: Long, val notationMode: String)

/** 연결된 소셜 로그인. 제공자 사용자 ID·이메일은 내보내지 않는다. */
class LinkedProvider(val provider: String, val linkedAt: Instant)

interface MyInfoRepository {
    /** ACTIVE 회원만 찾는다. */
    fun findActive(userId: Long): MyInfo?
    fun linkedProviders(userId: Long): List<LinkedProvider>
    /** ACTIVE 회원의 닉네임을 바꾼다. 바꿨으면 true. */
    fun updateNickname(userId: Long, nickname: String): Boolean
    fun activeInstrumentExists(instrumentId: Long): Boolean
    /** ACTIVE 회원의 악기·표기 중 null이 아닌 것만 바꾼다. 바꿨으면 true. */
    fun updatePreferences(userId: Long, instrumentId: Long?, notationMode: String?): Boolean
}
