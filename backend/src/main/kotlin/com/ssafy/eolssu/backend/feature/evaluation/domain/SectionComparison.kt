package com.ssafy.eolssu.backend.feature.evaluation.domain

/**
 * 이전·이번 연주 결과 비교(EVA-04). 비교 구간은 이번 세션의 연습 구간이다.
 * 이전·이번 모두 비교 구간 음표의 저장 판정으로 오류 수·고친 음표·남은 오류를 센다.
 * 이번 점수는 저장 점수이고, 이전 점수는 AI가 평가 때 준 음표별 부분 점수를 AI의 합산 규칙대로 구간만 합친다.
 * 채점 공식은 AI 소유라 서버가 만들지 않는다.
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
            return SectionComparisonResult(false, false, emptyList(), false, range.first, range.last, null, null, afterScores, null, emptyList(), errors)
        }
        // 악보 버전이 다르면 같은 noteIdx가 같은 음이라는 보장이 없어 이전 음표를 이번 구간에 대지 않는다.
        val sameScore = baseline.scoreVersion == current.scoreVersion
        require(!sameScore || baseline.startNoteIdx <= range.first && range.last <= baseline.endNoteIdx)
        val before = if (sameScore) baselineNotes.filter { it.noteIdx in range } else emptyList()
        val beforeErrorCount = if (sameScore) before.count { it.isError } else null
        val correctNow = after.filter { !it.isError }.map { it.noteIdx }.toSet()
        val rated = current.scoringVersion != null && baseline.scoringVersion != null
        val reasons = buildList {
            if (!sameScore) add(NotComparableReason.SCORE_VERSION_CHANGED)
            if (baseline.modelVersion != current.modelVersion) add(NotComparableReason.MODEL_CHANGED)
            if (rated && baseline.scoringVersion != current.scoringVersion) add(NotComparableReason.SCORING_VERSION_CHANGED)
            if (!rated) add(NotComparableReason.NO_NOTE_SCORES)
        }
        return SectionComparisonResult(
            reasons.isEmpty(), sameScore, reasons,
            baseline.bpm != current.bpm || baseline.bpmUnit != current.bpmUnit,
            range.first, range.last, beforeErrorCount,
            if (reasons.isEmpty()) sectionScores(before, requireNotNull(baseline.scoreWeights)) else null,
            afterScores, beforeErrorCount?.let { errors.size - it },
            before.filter { it.isError && it.noteIdx in correctNow }.map { it.noteIdx }, errors,
        )
    }

    /** /v1/analyses의 합산 규칙. 곡 전체를 합친 값이 AI 점수와 같도록 AI의 `combine()`과 같은 순서·같은 부동소수 연산으로 계산한다. */
    private fun sectionScores(notes: List<PracticeEvaluationNote>, weights: ScoreWeights): SectionItemScores {
        val pitch = merge(notes) { it.pitchScore }
        val timing = merge(notes) { it.timingScore }
        val duration = merge(notes) { it.durationScore }
        val total = Math.rint(pitch * weights.pitch + timing * weights.timing + duration * weights.duration).toInt()
        return SectionItemScores(total, pitch, timing, duration)
    }

    /** 값이 null인 음(시김새의 음정)은 뺀다. 합칠 음이 없으면 만점이고, 0.5는 AI의 round처럼 짝수 쪽으로 간다. */
    private fun merge(notes: List<PracticeEvaluationNote>, part: (PracticeEvaluationNote) -> Double?): Int {
        var total = 0.0
        var weightSum = 0.0
        for (note in notes) {
            val value = part(note) ?: continue
            val weight = requireNotNull(note.weight)
            total += value * weight
            weightSum += weight
        }
        return if (weightSum > 0) Math.rint(total / weightSum).toInt() else 100
    }
}

/** 점수나 오류 수를 바로 견줄 수 없는 이유. 해당하는 것을 모두 준다. */
enum class NotComparableReason { SCORE_VERSION_CHANGED, MODEL_CHANGED, SCORING_VERSION_CHANGED, NO_NOTE_SCORES }

/** 비교 대상이 없으면 두 비교 가능 여부와 bpmChanged는 false, 사유는 비어 있고 이전 결과에서 나온 값은 null이다. */
data class SectionComparisonResult(
    val scoreComparable: Boolean, val errorComparable: Boolean, val notComparableReasons: List<NotComparableReason>, val bpmChanged: Boolean,
    val startNoteIdx: Int, val endNoteIdx: Int, val beforeErrorCount: Int?, val beforeScores: SectionItemScores?,
    val after: SectionScores, val errorDelta: Int?, val correctedNoteIndices: List<Int>, val remainingErrors: List<PracticeEvaluationNote>,
) {
    /** 이번 − 이전. scoreComparable이 false면 null이다. */
    val scoreDelta: SectionItemScores? get() = beforeScores?.let {
        SectionItemScores(after.totalScore - it.totalScore, after.pitchScore - it.pitchScore, after.timingScore - it.timingScore, after.durationScore - it.durationScore)
    }
}

/** 총점과 항목 점수. 이전 구간 점수나 이번과의 차이를 담는다. */
data class SectionItemScores(val totalScore: Int, val pitchScore: Int, val timingScore: Int, val durationScore: Int)

/** 이번 세션의 저장 점수와 비교 구간 오류 수. */
data class SectionScores(val totalScore: Int, val pitchScore: Int, val timingScore: Int, val durationScore: Int, val errorCount: Int)
