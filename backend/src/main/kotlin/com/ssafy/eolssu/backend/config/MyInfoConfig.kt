package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.UserSocialAccountMapper
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.user.application.MyInfoService
import com.ssafy.eolssu.backend.feature.user.domain.MyInfoRepository
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.MyBatisMyInfoRepository
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db")
class MyInfoConfig {
    @Bean
    fun myInfoRepository(users: UserMapper, socialAccounts: UserSocialAccountMapper, instruments: InstrumentMapper): MyInfoRepository =
        MyBatisMyInfoRepository(users, socialAccounts, instruments)

    @Bean
    fun myInfoService(repository: MyInfoRepository) = MyInfoService(repository)
}
