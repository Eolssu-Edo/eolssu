package com.ssafy.eolssu.backend.feature.evaluation.domain

/**
 * 구간 재평가의 이전·이번 결과 비교(EVA-04). 비교 구간은 이번 세션의 연습 구간이다.
 * 점수는 비교하지 않는다(2026-10-30 결정): 이전 결과가 다른 구간이면 같은 구간 점수가 없고, 채점 공식은 AI 소유다.
 * 이전·이번 모두 비교 구간 음표의 저장 판정으로 오류 수·고친 음표·남은 오류를 센다.
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
        val notComparable = when {
            !sameScore -> NotComparable.SCORE_VERSION
            baseline.modelVersion != current.modelVersion -> NotComparable.MODEL
            baseline.bpm != current.bpm || baseline.bpmUnit != current.bpmUnit -> NotComparable.TEMPO
            else -> null
        }
        val beforeErrorCount = if (sameScore) before.count { it.isError } else null
        val afterScores = SectionScores(
            requireNotNull(current.totalScore), requireNotNull(current.pitchScore), requireNotNull(current.timingScore),
            requireNotNull(current.durationScore), after.count { it.isError },
        )
        val corrected = if (!sameScore) emptyList() else {
            val correctNow = after.filter { !it.isError }.map { it.noteIdx }.toSet()
            before.filter { it.isError && it.noteIdx in correctNow }.map { it.noteIdx }
        }
        return SectionComparisonResult(
            notComparable, range.first, range.last, beforeErrorCount, afterScores,
            if (notComparable == null) SectionDelta(afterScores.errorCount - requireNotNull(beforeErrorCount)) else null,
            corrected, after.filter { it.isError },
        )
    }
}

/** 오류 수를 바로 견줄 수 없는 이유. 확인 순서대로 하나만 고른다. */
enum class NotComparable { SCORE_VERSION, MODEL, TEMPO }

/** beforeErrorCount는 악보 버전이 다르면 null이다. */
data class SectionComparisonResult(
    val notComparable: NotComparable?, val startNoteIdx: Int, val endNoteIdx: Int,
    val beforeErrorCount: Int?, val after: SectionScores, val delta: SectionDelta?,
    val correctedNoteIndices: List<Int>, val remainingErrors: List<PracticeEvaluationNote>,
)

/** 이번 세션의 저장 점수와 비교 구간 오류 수. */
data class SectionScores(val totalScore: Int, val pitchScore: Int, val timingScore: Int, val durationScore: Int, val errorCount: Int)

data class SectionDelta(val errorCount: Int)
