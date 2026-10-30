package com.ssafy.eolssu.backend.feature.evaluation.domain

/**
 * 구간 재평가의 이전·이번 결과 비교(EVA-04). 비교 구간은 이번 세션의 연습 구간이다.
 * 점수는 저장값만 쓴다. 채점 공식은 AI 소유라 이전 결과가 다른 구간이면 구간 점수를 다시 계산하지 않고 비우며,
 * 오류 수·고친 음표·남은 오류만 저장 판정에서 센다.
 */
object SectionComparison {
    fun compare(
        current: PracticeEvaluationSnapshot, currentNotes: List<PracticeEvaluationNote>,
        baseline: PracticeEvaluationSnapshot, baselineNotes: List<PracticeEvaluationNote>,
    ): SectionComparisonResult {
        val range = current.startNoteIdx..current.endNoteIdx
        // 악보 버전이 다르면 같은 noteIdx가 같은 음이라는 보장이 없어 이전 음표를 이번 구간에 대지 않는다.
        val sameScore = baseline.scoreVersion == current.scoreVersion
        require(!sameScore || baseline.startNoteIdx <= range.first && range.last <= baseline.endNoteIdx)
        val after = currentNotes.filter { it.noteIdx in range }
        val before = if (sameScore) baselineNotes.filter { it.noteIdx in range } else emptyList()
        val sameRange = baseline.startNoteIdx == range.first && baseline.endNoteIdx == range.last
        val notComparable = when {
            !sameScore -> NotComparable.SCORE_VERSION
            !sameRange -> NotComparable.RANGE
            baseline.modelVersion != current.modelVersion -> NotComparable.MODEL
            baseline.bpm != current.bpm || baseline.bpmUnit != current.bpmUnit -> NotComparable.TEMPO
            else -> null
        }
        val beforeScores = when {
            !sameScore -> SectionScores(null, null, null, null, null)
            sameRange -> baseline.scores(before)
            else -> SectionScores(null, null, null, null, before.count { it.isError })
        }
        val afterScores = current.scores(after)
        val corrected = if (!sameScore) emptyList() else {
            val correctNow = after.filter { !it.isError }.map { it.noteIdx }.toSet()
            before.filter { it.isError && it.noteIdx in correctNow }.map { it.noteIdx }
        }
        return SectionComparisonResult(
            notComparable, range.first, range.last, beforeScores, afterScores,
            if (notComparable == null) SectionDelta(
                requireNotNull(afterScores.totalScore) - requireNotNull(beforeScores.totalScore),
                requireNotNull(afterScores.errorCount) - requireNotNull(beforeScores.errorCount),
            ) else null,
            corrected, after.filter { it.isError },
        )
    }

    private fun PracticeEvaluationSnapshot.scores(notes: List<PracticeEvaluationNote>) =
        SectionScores(totalScore, pitchScore, timingScore, durationScore, notes.count { it.isError })
}

/** 점수를 바로 견줄 수 없는 이유. 확인 순서대로 하나만 고른다. */
enum class NotComparable { SCORE_VERSION, RANGE, MODEL, TEMPO }

data class SectionComparisonResult(
    val notComparable: NotComparable?, val startNoteIdx: Int, val endNoteIdx: Int,
    val before: SectionScores, val after: SectionScores, val delta: SectionDelta?,
    val correctedNoteIndices: List<Int>, val remainingErrors: List<PracticeEvaluationNote>,
)

/** 이전 결과가 다른 구간이면 점수 4개는 null이다. errorCount는 비교 구간의 저장 판정으로 세며 악보 버전이 다르면 null이다. */
data class SectionScores(val totalScore: Int?, val pitchScore: Int?, val timingScore: Int?, val durationScore: Int?, val errorCount: Int?)

data class SectionDelta(val totalScore: Int, val errorCount: Int)
