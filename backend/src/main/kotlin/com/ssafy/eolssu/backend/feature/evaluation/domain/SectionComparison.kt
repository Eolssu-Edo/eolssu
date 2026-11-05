package com.ssafy.eolssu.backend.feature.evaluation.domain

/**
 * 이전·이번 연주 결과 비교(EVA-04). 비교 구간은 이번 세션의 연습 구간이다.
 * 이전·이번 모두 비교 구간 음표의 저장 판정으로 오류 수·고친 음표·남은 오류를 센다.
 * 점수는 음표별 부분 점수를 저장하기 전까지 비교하지 않는다. 채점 공식은 AI 소유라 서버가 만들지 않는다.
 */
object SectionComparison {
    fun compare(
        current: PracticeEvaluationSnapshot, currentNotes: List<PracticeEvaluationNote>,
        baseline: PracticeEvaluationSnapshot?, baselineNotes: List<PracticeEvaluationNote> = emptyList(),
    ): SectionComparisonResult {
        val range = current.startNoteIdx..current.endNoteIdx
        val after = currentNotes.filter { it.noteIdx in range }
        val errors = after.filter { it.isError }
        val afterScores = SectionScores(
            requireNotNull(current.totalScore), requireNotNull(current.pitchScore), requireNotNull(current.timingScore),
            requireNotNull(current.durationScore), errors.size,
        )
        if (baseline == null) {
            return SectionComparisonResult(false, emptyList(), false, range.first, range.last, null, afterScores, null, emptyList(), errors)
        }
        // 악보 버전이 다르면 같은 noteIdx가 같은 음이라는 보장이 없어 이전 음표를 이번 구간에 대지 않는다.
        val sameScore = baseline.scoreVersion == current.scoreVersion
        require(!sameScore || baseline.startNoteIdx <= range.first && range.last <= baseline.endNoteIdx)
        val before = if (sameScore) baselineNotes.filter { it.noteIdx in range } else emptyList()
        val beforeErrorCount = if (sameScore) before.count { it.isError } else null
        val correctNow = after.filter { !it.isError }.map { it.noteIdx }.toSet()
        return SectionComparisonResult(
            sameScore,
            buildList {
                if (!sameScore) add(NotComparableReason.SCORE_VERSION_CHANGED)
                if (baseline.modelVersion != current.modelVersion) add(NotComparableReason.MODEL_CHANGED)
                add(NotComparableReason.NO_NOTE_SCORES)
            },
            baseline.bpm != current.bpm || baseline.bpmUnit != current.bpmUnit,
            range.first, range.last, beforeErrorCount, afterScores, beforeErrorCount?.let { errors.size - it },
            before.filter { it.isError && it.noteIdx in correctNow }.map { it.noteIdx }, errors,
        )
    }
}

/** 점수나 오류 수를 바로 견줄 수 없는 이유. 해당하는 것을 모두 준다. SCORING_VERSION_CHANGED는 합산 규칙을 저장한 뒤에 추가한다. */
enum class NotComparableReason { SCORE_VERSION_CHANGED, MODEL_CHANGED, NO_NOTE_SCORES }

/** 비교 대상이 없으면 errorComparable·bpmChanged는 false, 사유는 비어 있고 이전 결과에서 나온 값은 null이다. */
data class SectionComparisonResult(
    val errorComparable: Boolean, val notComparableReasons: List<NotComparableReason>, val bpmChanged: Boolean,
    val startNoteIdx: Int, val endNoteIdx: Int, val beforeErrorCount: Int?, val after: SectionScores, val errorDelta: Int?,
    val correctedNoteIndices: List<Int>, val remainingErrors: List<PracticeEvaluationNote>,
) {
    /** 음표별 부분 점수를 저장하기 전까지 항상 false다. */
    val scoreComparable: Boolean get() = false
}

/** 이번 세션의 저장 점수와 비교 구간 오류 수. */
data class SectionScores(val totalScore: Int, val pitchScore: Int, val timingScore: Int, val durationScore: Int, val errorCount: Int)
