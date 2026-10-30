package com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.evaluation.domain.EvaluationScoreCells
import com.ssafy.eolssu.backend.feature.evaluation.domain.NoteCell
import tools.jackson.databind.json.JsonMapper

/**
 * `songs.score_json`(schemaVersion 1)의 NOTE 이벤트를 연주 순서대로 세어 음표 순번별 정간을 읽는다.
 * 순번 규칙은 분석 요청(ScoreJsonNotes)·CAT-04와 같다. 저장 악보가 깨졌으면 예외로 드러낸다.
 */
class ScoreJsonNoteCells(private val mapper: EvaluationReadMapper, private val json: JsonMapper) : EvaluationScoreCells {
    override fun noteCells(songId: Long, scoreVersion: Int): List<NoteCell>? {
        val row = mapper.score(songId)?.takeIf { it.scoreVersion == scoreVersion } ?: return null
        return json.readTree(row.scoreJson).path("events")
            .filter { it.path("kind").asString() == "NOTE" }
            .map { event ->
                val cellId = event.path("cellId").asString()
                val column = CELL_ID.matchEntire(cellId)?.groupValues?.get(1)?.toIntOrNull()
                    ?: throw IllegalStateException("score_json NOTE has an invalid cellId")
                NoteCell(column, cellId)
            }
    }

    private companion object {
        val CELL_ID = Regex("c([0-9]+)-j[0-9]+")
    }
}
