package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 실제 기록 API가 연결되지 않았음을 명시한다. 저장 성공이나 계정 간 공유 기록을 만들지 않는다. */
class PracticeRecordUnavailable : IllegalStateException("Practice history API is not connected")

/** 기록 API 연결 전의 운영 경로. 예시 데이터나 메모리 저장으로 성공을 대신하지 않는다. */
class UnavailablePracticeRecordRepository : PracticeRecordRepository {
    override val available: Boolean = false
    override val records: StateFlow<List<PracticeRecord>> = MutableStateFlow(emptyList<PracticeRecord>()).asStateFlow()

    override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String): PracticeRecord =
        throw PracticeRecordUnavailable()

    override suspend fun updateMemo(recordId: String, memo: String): Unit = throw PracticeRecordUnavailable()
}
