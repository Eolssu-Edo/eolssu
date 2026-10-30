package com.ssafy.eolssu.backend.feature.evaluation.application

import com.ssafy.eolssu.backend.feature.evaluation.domain.EvaluationScoreCells
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import com.ssafy.eolssu.backend.feature.evaluation.domain.WeakSectionReport
import com.ssafy.eolssu.backend.feature.evaluation.domain.WeakSections
import org.springframework.transaction.annotation.Transactional

/** 저장된 판정과 세션 악보 버전의 정간 위치로 취약 구간을 계산한다(EVA-03). 결과는 저장하지 않는다. */
open class GetWeakSections(private val evaluations: PracticeEvaluations, private val scores: EvaluationScoreCells) {
    @Transactional(readOnly = true)
    open fun execute(userId: Long, sessionId: Long, limit: Int): WeakSectionsLookup {
        val row = evaluations.findOwned(sessionId, userId) ?: return WeakSectionsLookup.NotFound
        if (row.status != "DONE") return WeakSectionsLookup.NotReady
        // 곡 악보가 세션 이후 바뀌면 음표 순번과 정간이 어긋나므로 계산하지 않는다.
        val cells = scores.noteCells(row.songId, row.scoreVersion)?.takeIf { it.size > row.endNoteIdx }
            ?: return WeakSectionsLookup.ScoreChanged
        val notes = evaluations.notes(requireNotNull(row.evaluationId))
        return WeakSectionsLookup.Found(row.sessionId, WeakSections.recommend(notes, cells, row.startNoteIdx, row.endNoteIdx, limit))
    }
}

sealed interface WeakSectionsLookup {
    data object NotFound : WeakSectionsLookup
    data object NotReady : WeakSectionsLookup
    data object ScoreChanged : WeakSectionsLookup
    data class Found(val sessionId: Long, val report: WeakSectionReport) : WeakSectionsLookup
}
