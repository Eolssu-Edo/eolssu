package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import tools.jackson.databind.json.JsonMapper

/** 세션의 곡 악보와 `pitches`(율명·옥타브 → 반음·음 ID)로 연습 구간의 음표를 만든다. */
class MyBatisPracticeScoreNotes(private val mapper: PracticeAnalysisMapper, jsonMapper: JsonMapper) : PracticeScoreNotes {
    private val scoreNotes = ScoreJsonNotes(jsonMapper)

    override fun notesFor(analysis: PendingAnalysis): List<ScoreNote> {
        val score = mapper.findScore(analysis.songId) ?: throw ScoreNotesException("song ${analysis.songId} not found")
        // 세션을 만든 뒤 악보가 바뀌었으면 음표 번호가 어긋나 채점하지 않는다.
        if (score.scoreVersion != analysis.scoreVersion) throw ScoreNotesException("score version changed since the session started")
        val pitches = mapper.findPitches().associateBy { it.yul to it.octave.toInt() }
        return scoreNotes.notes(score.scoreJson, analysis.startNoteIdx, analysis.endNoteIdx, pitches)
    }
}
