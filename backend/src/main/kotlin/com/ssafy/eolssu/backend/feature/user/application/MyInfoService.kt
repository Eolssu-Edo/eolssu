package com.ssafy.eolssu.backend.feature.user.application

import com.ssafy.eolssu.backend.feature.user.domain.LinkedProvider
import com.ssafy.eolssu.backend.feature.user.domain.MyInfo
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository

class MyInfoService(private val repository: MyInfoRepository) {
    fun find(userId: Long): MyInfoWithProviders? =
        repository.findActive(userId)?.let { MyInfoWithProviders(it, repository.linkedProviders(userId)) }

    /** 바꾼 뒤의 내 정보. ACTIVE 회원이 아니면 null. */
    fun changeNickname(userId: Long, nickname: String): MyInfo? =
        if (repository.updateNickname(userId, nickname)) repository.findActive(userId) else null

    fun changePreferences(userId: Long, instrumentId: Long?, notationMode: String?): PreferencesResult {
        if (instrumentId != null && !repository.activeInstrumentExists(instrumentId)) return PreferencesResult.InactiveInstrument
        if (!repository.updatePreferences(userId, instrumentId, notationMode)) return PreferencesResult.NotFound
        return repository.findActive(userId)?.let { PreferencesResult.Changed(it) } ?: PreferencesResult.NotFound
    }
}

sealed interface PreferencesResult {
    class Changed(val info: MyInfo) : PreferencesResult
    data object InactiveInstrument : PreferencesResult
    data object NotFound : PreferencesResult
}

class MyInfoWithProviders(val info: MyInfo, val linkedProviders: List<LinkedProvider>)
