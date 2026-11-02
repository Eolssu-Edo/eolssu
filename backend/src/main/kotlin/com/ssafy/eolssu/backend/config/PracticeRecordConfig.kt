package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.evaluation.domain.EvaluationScoreCells
import com.ssafy.eolssu.backend.feature.practice.domain.RECORDING_READ_URL_TTL
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.RecordingStorageProperties
import com.ssafy.eolssu.backend.feature.record.application.DeletePracticeRecord
import com.ssafy.eolssu.backend.feature.record.application.GetPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.GetRecordingPlayback
import com.ssafy.eolssu.backend.feature.record.application.SavePracticeRecord
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import com.ssafy.eolssu.backend.feature.record.infrastructure.persistence.MyBatisPracticeRecords
import com.ssafy.eolssu.backend.feature.record.infrastructure.persistence.PracticeRecordMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// 연습 기록(REC-01~04). 다른 기능의 구현은 여기서만 함수로 연결한다.
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class PracticeRecordConfig {
    @Bean
    fun practiceRecords(mapper: PracticeRecordMapper): PracticeRecords = MyBatisPracticeRecords(mapper)

    // 각 번호는 EVA-03·04와 같은 악보 칸 읽기를 쓴다. 같은 해석을 복제하지 않는다.
    @Bean
    fun recordScoreColumns(cells: EvaluationScoreCells) = ScoreColumns { songId, scoreVersion ->
        cells.noteCells(songId, scoreVersion)?.map { it.column }
    }

    @Bean
    fun savePracticeRecord(records: PracticeRecords, columns: ScoreColumns) = SavePracticeRecord(records, columns, Clock.systemUTC())

    @Bean
    fun getPracticeRecords(records: PracticeRecords, columns: ScoreColumns) = GetPracticeRecords(records, columns)

    // 다시 듣기 주소는 녹음 저장소(PRA-02)의 서명 URL이고, 유효 시간은 분석 요청과 같은 15분이다.
    // 로컬 저장소의 읽기 주소는 AI가 쓰는 Compose 내부 주소라 앱에 내주지 않는다(infra/storage/README.md). 그때 다시 듣기는 503이다.
    @Bean
    fun getRecordingPlayback(records: PracticeRecords, storage: RecordingStorage, storageProperties: RecordingStorageProperties) =
        GetRecordingPlayback(
            records,
            if (storageProperties.type == "local") null else storage::readUrl,
            RECORDING_READ_URL_TTL,
            Clock.systemUTC(),
        )

    // 기록 삭제는 탈퇴 정리처럼 녹음 저장소의 삭제를 쓴다. 없는 키도 성공이다.
    @Bean
    fun deletePracticeRecord(records: PracticeRecords, storage: RecordingStorage) =
        DeletePracticeRecord(records, storage::delete, Clock.systemUTC())
}
