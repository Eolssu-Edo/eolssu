package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 기초부터 배우기에서 악기별로 완료 표시한 단계 번호를 기기에 남긴다. 앱을 다시 켜도 이어진다.
 * 서버에 학습 진도를 올리는 API가 생기면 이 저장소를 그 구현으로 바꾼다.
 */
class LearnProgressRepository(private val store: KeyValueStore) {
    private val _saves = MutableStateFlow(0)

    /** 완료 표시를 저장할 때마다 늘어난다. 다른 화면(연습 기록지)이 완료 수를 다시 셀 때 쓴다. */
    val saves: StateFlow<Int> = _saves.asStateFlow()

    fun doneStages(instrumentId: String): Set<Int> =
        store.getString(key(instrumentId))
            ?.split(SEPARATOR)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            .orEmpty()

    fun saveDoneStages(instrumentId: String, stages: Set<Int>) {
        store.putString(key(instrumentId), stages.sorted().joinToString(SEPARATOR))
        _saves.update { it + 1 }
    }

    /**
     * 연습 악기로 완료 표시한 단계 수(연습 기록지 '완료한 단계'). 학습 화면은 대금을 정악·산조로 나누므로,
     * 정악·산조의 완료 단계는 별도로 센다.
     */
    fun doneStageCount(instrument: Instrument): Int =
        instrument.learnIds.flatMap(::doneStages).filter { it in 1..STAGE_COUNT }.toSet().size

    private fun key(instrumentId: String) = "learn_done_$instrumentId"

    companion object {
        /** 기초부터 배우기 단계 수. 학습 화면의 단계 목록(`LearnStages`)과 같다. */
        const val STAGE_COUNT = 9

        private const val SEPARATOR = ","
    }
}

/** 학습 화면 악기 식별자(`GuideInstrument.id`). */
private val Instrument.learnIds: List<String>
    get() = when (this) {
        Instrument.DaegeumJeongak -> listOf("daegeum-jeongak")
        Instrument.DaegeumSanjo -> listOf("daegeum-sanjo")
        Instrument.Sogeum -> listOf("sogeum")
        Instrument.Danso -> listOf("danso")
    }
