package com.ssafy.eolssu.backend.feature.user.application

import com.ssafy.eolssu.backend.feature.user.domain.LinkedProvider
import com.ssafy.eolssu.backend.feature.user.domain.MyInfo
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository

class MyInfoService(private val repository: MyInfoRepository) {
    fun find(userId: Long): MyInfoWithProviders? =
        repository.findActive(userId)?.let { MyInfoWithProviders(it, repository.linkedProviders(userId)) }
}

class MyInfoWithProviders(val info: MyInfo, val linkedProviders: List<LinkedProvider>)
