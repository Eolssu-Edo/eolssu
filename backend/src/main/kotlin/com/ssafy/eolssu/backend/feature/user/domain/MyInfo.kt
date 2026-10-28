package com.ssafy.eolssu.backend.feature.user.domain

import java.time.Instant

/** 내 정보(USR-01). 이메일·비밀번호 해시는 담지 않는다. */
class MyInfo(val userId: Long, val nickname: String, val instrumentId: Long, val notationMode: String)

/** 연결된 소셜 로그인. 제공자 사용자 ID·이메일은 내보내지 않는다. */
class LinkedProvider(val provider: String, val linkedAt: Instant)

interface MyInfoRepository {
    /** ACTIVE 회원만 찾는다. */
    fun findActive(userId: Long): MyInfo?
    fun linkedProviders(userId: Long): List<LinkedProvider>
}
