package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.application.CopiedScore
import com.ssafy.eolssu.backend.feature.sheet.application.PreparedScore
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.song.domain.InvalidStoredScore
import com.ssafy.eolssu.backend.feature.song.domain.PublishedSongScore
import com.ssafy.eolssu.backend.feature.song.domain.ScorePitch
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import com.ssafy.eolssu.backend.feature.song.domain.SongScoreDecoder
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
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

    override fun prepare(scoreJson: String, scoreVersion: Int): PreparedScore {
        val root = read(scoreJson) ?: return PreparedScore.Malformed
        val score = root.path("score")
        val music = root.path("music")
        val columns = root.path("columns")
        if (!shaped(root) || score !is ObjectNode || !music.path("bpmUnit").isString || !music.path("jangdan").isString) {
            return PreparedScore.Malformed
        }
        val unfilled = columns.flatMap { column -> column.path("cells").filter { it.path("units").let { units -> units.isArray && units.isEmpty } } }
        // 알릴 칸 ID가 없는 빈 칸은 앱이 강조할 수 없으므로 악보 형식 오류다.
        if (unfilled.any { !it.path("id").isString }) return PreparedScore.Malformed
        if (unfilled.isNotEmpty()) return PreparedScore.Unfilled(unfilled.map { it.path("id").stringValue() })
        score.put("scoreVersion", scoreVersion)
        return PreparedScore.Ready(
            json.writeValueAsString(root), columns.size(), root.path("events").count { it.path("kind").stringValue() == "NOTE" },
            music.path("jangdan").stringValue(), music.path("bpmUnit").stringValue(),
        )
    }

    override fun unfilledCellCount(scoreJson: String): Int? {
        val root = read(scoreJson)?.takeIf(::shaped) ?: return null
        return root.path("columns").sumOf { column -> column.path("cells").count { it.path("units").let { units -> units.isArray && units.isEmpty } } }
    }

    private fun read(scoreJson: String): JsonNode? = try {
        json.readTree(scoreJson)
    } catch (_: JacksonException) {
        null
    }

    // 초안도 확정 저장과 같은 최상위 모양이어야 다시 열 수 있다. 칸 안의 검증은 확정 저장에서 한다.
    private fun shaped(root: JsonNode) = root.path("schemaVersion").intValue() == 1 && root.path("score").isObject &&
        root.path("music").isObject && root.path("columns").isArray && root.path("events").isArray && root.path("provenance").isObject &&
        root.path("columns").all { it.isObject && it.path("cells").isArray && it.path("cells").all { cell -> cell.isObject } }
}
