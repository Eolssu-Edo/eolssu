package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.sheet.application.CreateUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.EditUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.GetUserSheets
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.CommonSheetScores
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.MyBatisUserSheetRepository
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.UserSheetMapper
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.MyBatisSongScoreRepository
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongScoreMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

// 내 악보(SHT-01·02·03·04·08·11). 계약: docs/specs/user-sheets.md
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class UserSheetConfig {
    @Bean
    fun userSheetRepository(mapper: UserSheetMapper): UserSheetRepository = MyBatisUserSheetRepository(mapper)

    @Bean
    fun sheetScores(songs: SongScoreMapper, json: JsonMapper): SheetScores =
        CommonSheetScores(json, CommonScoreTimelineDecoder(json), MyBatisSongScoreRepository(songs)::pitches)

    @Bean
    fun createUserSheet(repository: UserSheetRepository, scores: SheetScores, clock: Clock) = CreateUserSheet(repository, scores, clock)

    @Bean
    fun getUserSheets(repository: UserSheetRepository, scores: SheetScores) = GetUserSheets(repository, scores)

    @Bean
    fun editUserSheet(repository: UserSheetRepository, scores: SheetScores, clock: Clock) = EditUserSheet(repository, scores, clock)
}
