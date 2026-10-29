package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.UserSocialAccountMapper
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.user.domain.LinkedProvider
import com.ssafy.eolssu.backend.feature.user.domain.MyInfo
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository
import java.time.ZoneOffset

class MyBatisMyInfoRepository(
    private val users: UserMapper,
    private val socialAccounts: UserSocialAccountMapper,
    private val instruments: InstrumentMapper,
) : MyInfoRepository {
    override fun findActive(userId: Long) = users.findById(userId)
        ?.takeIf { it.status == "ACTIVE" }
        ?.let { MyInfo(it.id, checkNotNull(it.nickname), it.instrumentId, it.notationMode) }

    override fun linkedProviders(userId: Long) =
        socialAccounts.findByUserId(userId).map { LinkedProvider(it.provider, it.createdAt.toInstant(ZoneOffset.UTC)) }

    override fun updateNickname(userId: Long, nickname: String) = users.updateNickname(userId, nickname) == 1
    override fun activeInstrumentExists(instrumentId: Long) = instruments.activeExists(instrumentId)
    override fun updatePreferences(userId: Long, instrumentId: Long?, notationMode: String?) =
        users.updatePreferences(userId, instrumentId, notationMode) == 1
}
