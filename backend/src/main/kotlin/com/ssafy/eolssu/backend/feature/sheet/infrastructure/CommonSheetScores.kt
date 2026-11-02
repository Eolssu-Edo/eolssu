package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.application.CopiedScore
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.song.domain.InvalidStoredScore
import com.ssafy.eolssu.backend.feature.song.domain.PublishedSongScore
import com.ssafy.eolssu.backend.feature.song.domain.ScorePitch
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import com.ssafy.eolssu.backend.feature.song.domain.SongScoreDecoder
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/** 제공 곡과 같은 디코더로 검증한다. 음 목록은 요청마다 읽는다(CAT-04와 같다). */
class CommonSheetScores(
    private val json: JsonMapper,
    private val decoder: SongScoreDecoder,
    private val pitches: () -> List<ScorePitch>,
) : SheetScores {
    override fun copy(scoreJson: String, scoreVersion: Int): CopiedScore? {
        val root = try {
            json.readTree(scoreJson)
        } catch (_: JacksonException) {
            return null
        }
        val score = root.path("score")
        val columns = root.path("columns")
        val events = root.path("events")
        if (root.path("schemaVersion").intValue() != 1 || score !is ObjectNode || !columns.isArray || columns.isEmpty || !events.isArray) {
            return null
        }
        score.put("scoreVersion", scoreVersion)
        return CopiedScore(json.writeValueAsString(root), columns.size(), events.count { it.path("kind").stringValue() == "NOTE" })
    }

    override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String): List<ScoreTimelineEvent>? =
        try {
            // 디코더는 곡 ID를 쓰지 않는다. 내 악보의 BPM을 곡 기본 BPM 자리에 넣어 같은 범위 검사를 받는다.
            decoder.decode(PublishedSongScore(0, scoreVersion, noteCount, bpm, bpmUnit, scoreJson), pitches()).events
        } catch (_: InvalidStoredScore) {
            null
        }
}
