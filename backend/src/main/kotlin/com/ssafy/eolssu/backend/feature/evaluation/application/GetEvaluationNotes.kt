package com.ssafy.eolssu.backend.feature.evaluation.application

import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluationNote
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import org.springframework.transaction.annotation.Transactional

/** 세션 연습 구간 안의 음표 판정과 판정별 오류 수를 같은 읽기 전용 트랜잭션에서 조회한다(EVA-02). */
open class GetEvaluationNotes(private val evaluations: PracticeEvaluations) {
    @Transactional(readOnly = true)
    open fun execute(userId: Long, sessionId: Long, query: EvaluationNotesQuery): EvaluationNotesLookup {
        val row = evaluations.findOwned(sessionId, userId) ?: return EvaluationNotesLookup.NotFound
        if (row.status != "DONE") return EvaluationNotesLookup.NotReady
        val start = query.startNoteIdx ?: row.startNoteIdx
        val end = query.endNoteIdx ?: row.endNoteIdx
        if (start < row.startNoteIdx || end > row.endNoteIdx || start > end) return EvaluationNotesLookup.OutOfRange
        val id = requireNotNull(row.evaluationId)
        val notes = evaluations.notes(id).filter { it.noteIdx in start..end }
        val errors = notes.filter { it.isError }
        return EvaluationNotesLookup.Found(EvaluationNotes(
            id, if (query.errorsOnly) errors else notes, errors.size, errors.groupingBy { it.verdict }.eachCount(),
        ))
    }
}

/** 구간을 비우면 세션 연습 구간 전체다. 양 끝은 포함한다. */
data class EvaluationNotesQuery(val startNoteIdx: Int?, val endNoteIdx: Int?, val errorsOnly: Boolean)

data class EvaluationNotes(
    val evaluationId: Long, val notes: List<PracticeEvaluationNote>, val errorCount: Int, val errorCountsByVerdict: Map<String, Int>,
)

sealed interface EvaluationNotesLookup {
    data object NotFound : EvaluationNotesLookup
    data object NotReady : EvaluationNotesLookup
    data object OutOfRange : EvaluationNotesLookup
    data class Found(val notes: EvaluationNotes) : EvaluationNotesLookup
}
