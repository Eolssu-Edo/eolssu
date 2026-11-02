package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.evaluation.domain.EvaluationScoreCells
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
}
