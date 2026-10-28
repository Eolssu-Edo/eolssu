package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.storage.KeyValueStore

/**
 * 기초부터 배우기에서 악기별로 완료 표시한 단계 번호를 기기에 남긴다. 앱을 다시 켜도 이어진다.
 * 서버에 학습 진도를 올리는 API가 생기면 이 저장소를 그 구현으로 바꾼다.
 */
class LearnProgressRepository(private val store: KeyValueStore) {
    fun doneStages(instrumentId: String): Set<Int> =
        store.getString(key(instrumentId))
            ?.split(SEPARATOR)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            .orEmpty()

    fun saveDoneStages(instrumentId: String, stages: Set<Int>) =
        store.putString(key(instrumentId), stages.sorted().joinToString(SEPARATOR))

    private fun key(instrumentId: String) = "learn_done_$instrumentId"

    private companion object {
        const val SEPARATOR = ","
    }
}
