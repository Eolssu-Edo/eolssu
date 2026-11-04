package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.recommendedSection

/** 구간 반복 횟수 고르기(시안 3회 · 5회 · 계속). [count]가 null이면 멈출 때까지 계속한다. */
enum class SectionRepeat(val count: Int?) { Three(3), Five(5), Endless(null) }

/**
 * PRACTICE-02 취약 구간 선택 상태.
 *
 * @property section 고른 구간. 처음에는 추천 구간이고, 틀린 정간이 없으면 null.
 * @property picking 처음 정간을 누른 뒤 끝 정간을 기다리는 중.
 * @property loop ‘구간 반복’. 끄면 한 번만 연주하고 반복 횟수는 고를 수 없다.
 */
data class SectionSelectUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val result: EvaluationResult? = null,
    val section: ScoreSection? = null,
    val picking: Boolean = false,
    val loop: Boolean = true,
    val repeat: SectionRepeat = SectionRepeat.Five,
    /** 연습 속도. 연습 화면 속도 설정과 같은 5단계([TempoStep])이고 처음에는 약간 느리게(80%)다. */
    val speed: TempoStep = TempoStep.Slow,
) {
    /** 추천 취약 구간(틀린 정간이 가장 많이 몰린 두 각). */
    val recommended: ScoreSection? get() = song?.let { s -> result?.recommendedSection(s.score) }

    /** 구간 반복 화면에 넘길 반복 횟수. 반복을 끄면 1, ‘계속’이면 null. */
    val repeats: Int? get() = if (loop) repeat.count else 1

    val bpm: Int? get() = song?.let { speed.bpm(it.baseBpm) }

    /** 구간을 골라 연습을 시작할 수 있는지. 끝 정간을 기다리는 중에는 한 정간 구간으로 시작하지 않는다. */
    val canStart: Boolean get() = section != null && !picking
}

/**
 * 정간 [position]을 눌렀을 때. 처음 누르면 그 정간 하나를 구간으로 잡고 끝 정간을 기다리고,
 * 두 번째로 누르면 첫 정간과 이어 구간을 마무리한다(앞뒤 순서와 관계없이).
 */
fun SectionSelectUiState.tap(position: ScorePosition): SectionSelectUiState {
    val start = section?.start
    return if (!picking || start == null) {
        copy(section = ScoreSection(position, position), picking = true)
    } else {
        copy(section = ScoreSection.between(start, position), picking = false)
    }
}

/** 추천 구간으로 되돌린다. */
fun SectionSelectUiState.pickRecommended(): SectionSelectUiState = copy(section = recommended ?: section, picking = false)
