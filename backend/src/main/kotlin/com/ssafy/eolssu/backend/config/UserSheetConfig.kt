package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import com.ssafy.eolssu.backend.feature.sheet.api.SheetBodyLimitFilter
import com.ssafy.eolssu.backend.feature.sheet.application.EditUserSheet
import com.ssafy.eolssu.backend.feature.sheet.application.GetUserSheets
import com.ssafy.eolssu.backend.feature.sheet.application.SheetImportFiles
import com.ssafy.eolssu.backend.feature.sheet.application.SheetImports
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportRepository
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.CommonSheetScores
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.LocalSheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.PdfBoxPageCounter
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.S3SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.SheetStorageProperties
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.UnavailableSheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.MyBatisSheetImportRepository
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.SheetImportMapper
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.MyBatisUserSheetRepository
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.UserSheetMapper
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.MyBatisSongScoreRepository
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongScoreMapper
import java.nio.file.Path
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

// 내 악보(SHT-01·02·04·07·08·10·11). 계약: docs/specs/user-sheets.md
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class UserSheetConfig {
    @Bean
    fun userSheetRepository(mapper: UserSheetMapper): UserSheetRepository = MyBatisUserSheetRepository(mapper)

    @Bean
    fun sheetScores(songs: SongScoreMapper, json: JsonMapper): SheetScores =
        CommonSheetScores(json, CommonScoreTimelineDecoder(json), MyBatisSongScoreRepository(songs)::pitches)

    @Bean
    fun getUserSheets(repository: UserSheetRepository, scores: SheetScores) = GetUserSheets(repository, scores)

    @Bean
    fun editUserSheet(repository: UserSheetRepository, scores: SheetScores, clock: Clock) = EditUserSheet(repository, scores, clock)

    // 연결값(SHEET_STORAGE_*)이 하나라도 비면 불러오기를 503으로 답한다. 기동은 막지 않는다(녹음 저장소와 같다).
    @Bean
    fun sheetSourceStorage(properties: SheetStorageProperties): SheetSourceStorage = when {
        properties.type == "local" -> {
            require(properties.localDirectory.isNotBlank()) { "SHEET_STORAGE_LOCAL_DIRECTORY is required for local sheet storage" }
            LocalSheetSourceStorage(Path.of(properties.localDirectory))
        }
        properties.type == "s3" && properties.isConfigured -> S3SheetSourceStorage.create(properties)
        else -> {
            require(properties.type == "s3") { "Unknown sheet storage type" }
            log.warn("Sheet storage is not configured; sheet imports answer 503")
            UnavailableSheetSourceStorage()
        }
    }

    @Bean
    fun sheetImportRepository(mapper: SheetImportMapper, transactionManager: PlatformTransactionManager): SheetImportRepository =
        MyBatisSheetImportRepository(mapper, TransactionTemplate(transactionManager))

    @Bean
    fun sheetImports(repository: SheetImportRepository, storage: SheetSourceStorage, clock: Clock) =
        SheetImports(SheetImportFiles(PdfBoxPageCounter()), repository, storage, clock)

    // 기본 순서(가장 늦음)라 Spring Security 뒤에서 돈다. 로그인하지 않은 요청은 본문을 읽기 전에 401이다.
    @Bean
    fun sheetBodyLimitFilter(errorWriter: ErrorResponseWriter) =
        FilterRegistrationBean(SheetBodyLimitFilter(EditUserSheet.MAX_SCORE_BYTES, errorWriter)).apply { addUrlPatterns("/api/users/me/sheets/*") }

    private companion object {
        val log = LoggerFactory.getLogger(UserSheetConfig::class.java)
    }
}
