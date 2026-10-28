package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.analysis.domain.RecordingReadUrls
import com.ssafy.eolssu.backend.feature.practice.application.UploadRecording
import com.ssafy.eolssu.backend.feature.practice.domain.RECORDING_READ_URL_TTL
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFileValidator
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import com.ssafy.eolssu.backend.feature.practice.infrastructure.media.FfprobeAudioProbe
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.MyBatisRecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.RecordingStorageProperties
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.S3RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.UnavailableRecordingStorage
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// 녹음 업로드(PRA-02)는 세션을 바꾸는 쓰기 작업이라 db 프로필에서만 만든다.
@Configuration(proxyBeanMethods = false)
@Profile("db")
class RecordingUploadConfig {
    @Bean
    fun recordingUploadRepository(mapper: PracticeSessionMapper): RecordingUploadRepository = MyBatisRecordingUploadRepository(mapper)

    // 연결값(RECORDING_STORAGE_*)이 하나라도 비면 업로드를 503으로 답한다. 기동은 막지 않는다.
    @Bean
    fun recordingStorage(properties: RecordingStorageProperties): RecordingStorage =
        if (properties.isConfigured) {
            S3RecordingStorage.create(properties)
        } else {
            log.warn("Recording storage is not configured; uploads answer 503")
            UnavailableRecordingStorage()
        }

    @Bean
    fun recordingFileValidator() = RecordingFileValidator(FfprobeAudioProbe())

    // 분석 시작(PRA-03)은 RecordingUploaded 빈을 만들면 연결된다. 그 전에는 업로드된 세션이 상태 조회의 제한 시간 판정으로 TIMEOUT이 된다.
    @Bean
    fun uploadRecording(
        repository: RecordingUploadRepository,
        validator: RecordingFileValidator,
        storage: RecordingStorage,
        onUploaded: ObjectProvider<RecordingUploaded>,
    ) = UploadRecording(repository, validator, storage, Clock.systemUTC(), onUploaded.getIfAvailable { RecordingUploaded {} })

    /** AI가 원본을 읽는 15분짜리 서명 URL(PRA-03 분석 요청). */
    @Bean
    fun recordingReadUrls(storage: RecordingStorage) = RecordingReadUrls { storage.readUrl(it.recordingUrl, RECORDING_READ_URL_TTL) }

    private companion object {
        val log = LoggerFactory.getLogger(RecordingUploadConfig::class.java)
    }
}
