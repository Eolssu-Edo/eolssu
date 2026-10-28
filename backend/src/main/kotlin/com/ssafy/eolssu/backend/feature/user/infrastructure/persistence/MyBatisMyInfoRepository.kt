package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.UserSocialAccountMapper
import com.ssafy.eolssu.backend.feature.user.domain.LinkedProvider
import com.ssafy.eolssu.backend.feature.user.domain.MyInfo
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository
import java.time.ZoneOffset

class MyBatisMyInfoRepository(private val users: UserMapper, private val socialAccounts: UserSocialAccountMapper) : MyInfoRepository {
    override fun findActive(userId: Long) = users.findById(userId)
        ?.takeIf { it.status == "ACTIVE" }
        ?.let { MyInfo(it.id, checkNotNull(it.nickname), it.instrumentId, it.notationMode) }

    override fun linkedProviders(userId: Long) =
        socialAccounts.findByUserId(userId).map { LinkedProvider(it.provider, it.createdAt.toInstant(ZoneOffset.UTC)) }
}
