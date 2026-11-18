package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import tools.jackson.databind.json.JsonMapper

/** 세션의 곡 악보와 `pitches`(율명·옥타브 → 반음·음 ID)로 연습 구간의 음표를 만든다. */
class MyBatisPracticeScoreNotes(private val mapper: PracticeAnalysisMapper, jsonMapper: JsonMapper) : PracticeScoreNotes {
    private val scoreNotes = ScoreJsonNotes(jsonMapper)

    override fun notesFor(analysis: PendingAnalysis): List<ScoreNote> {
        val score = mapper.findScoreRevision(analysis.songId, analysis.scoreVersion)
            ?: throw ScoreNotesException("song ${analysis.songId} score revision ${analysis.scoreVersion} not found")
        // 보관 판본을 포함해 조회한 정답은 세션에 기록된 판본과 같아야 한다.
        if (score.scoreVersion != analysis.scoreVersion) throw ScoreNotesException("score version changed since the session started")
        val pitches = mapper.findPitches().associateBy { it.yul to it.octave.toInt() }
        return scoreNotes.notes(score.scoreJson, analysis.startNoteIdx, analysis.endNoteIdx, pitches)
    }
}
