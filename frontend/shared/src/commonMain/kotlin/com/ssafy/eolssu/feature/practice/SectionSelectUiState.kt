package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.data.practice.WeakSectionPick
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.ui.score.ErrorFilter

/** 구간 고르기 안내 문구 단계: 시작 정간 고르기 → 끝 정간 고르기 → 고른 구간 연습. */
enum class SectionSelectStep { Start, End, Ready }

/**
 * 구간 고르기 상태. 고른 구간으로 구간 연습(연습 화면)과 구간 재평가를 시작한다. 속도는 각 화면의 설정에서 정한다(S15P31E102-794).
 *
 * @property section 고른 구간. 처음에는 추천 구간이고, 틀린 정간이 없으면 null.
 * @property picking 시작 정간을 누른 뒤 끝 정간을 기다리는 중.
 * @property picked 사용자가 시작·끝 정간을 눌러 구간을 직접 마무리했는지. 추천 구간 그대로면 false다.
 * @property filter 악보에 보일 틀린 종류(연주 결과와 같은 5칸 필터). 구간 고르기에는 영향이 없다.
 */
data class SectionSelectUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val result: EvaluationResult? = null,
    val section: ScoreSection? = null,
    val picking: Boolean = false,
    val picked: Boolean = false,
    val filter: ErrorFilter = ErrorFilter.All,
    /** 서버가 추천한 취약 구간(EVA-03, 각 전체). 추천할 구간이 없거나 받지 못하면 null이고 정간을 눌러 직접 고른다. */
    val recommended: ScoreSection? = null,
    /** null은 현재 판본 조회 실패, false는 이전 판본이다. */
    val currentScore: Boolean? = true,
) {

    /** 구간을 골라 연습을 시작할 수 있는지. 끝 정간을 기다리는 중에는 한 정간 구간으로 시작하지 않는다. */
    val canStart: Boolean get() = section != null && !picking && currentScore == true

    /** 안내 문구 단계. 추천 구간이 잡혀 있어도 직접 고르기 전에는 시작 정간부터 안내한다. */
    val step: SectionSelectStep
        get() = when {
            picking -> SectionSelectStep.End
            picked && section != null -> SectionSelectStep.Ready
            else -> SectionSelectStep.Start
        }
}

/**
 * 정간 [position]을 눌렀을 때. 처음 누르면 그 정간 하나를 구간으로 잡고 끝 정간을 기다리고,
 * 두 번째로 누르면 첫 정간과 이어 구간을 마무리한다(앞뒤 순서와 관계없이).
 */
fun SectionSelectUiState.tap(position: ScorePosition): SectionSelectUiState {
    val start = section?.start
    return if (!picking || start == null) {
        copy(section = ScoreSection(position, position), picking = true, picked = false)
    } else {
        copy(section = ScoreSection.between(start, position), picking = false, picked = true)
    }
}

/** ‘취약 구간 보기’: 서버 추천 구간으로 되돌리고 안내도 처음 문구로 돌린다. */
fun SectionSelectUiState.resetToRecommended(): SectionSelectUiState =
    copy(section = recommended, picking = false, picked = false)

/** 서버 추천 구간(각 번호)을 이 악보의 구간으로 바꾼다. 악보에 없는 각이면 null. */
fun WeakSectionPick.toSection(score: Score): ScoreSection? {
    val last = score.columns.lastIndex
    if (startColumn < 0 || endColumn > last || startColumn > endColumn) return null
    return ScoreSection.ofColumns(score, startColumn, endColumn)
}
