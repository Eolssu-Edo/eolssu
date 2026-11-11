package com.ssafy.eolssu.backend.feature.user.domain

import java.time.Instant

/**
 * 내 정보(USR-01). 마이페이지에 보일 본인 이메일은 담고 비밀번호 해시는 담지 않는다.
 * [profileImage]는 앱에 담긴 절쑤 그림 12종 중 하나의 키다([ProfileImages]).
 */
class MyInfo(
    val userId: Long,
    val email: String?,
    val nickname: String,
    val instrumentId: Long,
    val notationMode: String,
    val hasPassword: Boolean,
    val profileImage: String = ProfileImages.DEFAULT,
)

/** 마이페이지 프로필 그림 키(S15P31E102-738). DB CHECK(V38)와 같은 목록이다. */
object ProfileImages {
    const val DEFAULT = "HELLO"
    const val PATTERN = "HELLO|DANSO|STUDY|LOOK|GREAT|JANGDAN|AGAIN|SURPRISE|PROUD|OKAY|REST|DONE"
}

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
    /** ACTIVE 회원의 프로필 그림을 바꾼다. 바꿨으면 true. */
    fun updateProfileImage(userId: Long, profileImage: String): Boolean
}
