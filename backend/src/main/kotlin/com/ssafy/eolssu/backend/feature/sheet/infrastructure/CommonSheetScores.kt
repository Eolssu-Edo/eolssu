package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.application.PreparedScore
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.song.domain.InvalidStoredScore
import com.ssafy.eolssu.backend.feature.song.domain.PublishedSongScore
import com.ssafy.eolssu.backend.feature.song.domain.ScorePitch
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent
import com.ssafy.eolssu.backend.feature.song.domain.UnregisteredScorePitch
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import java.security.MessageDigest
import java.util.HexFormat
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/** 앱 편집기로 저장한 악보의 provenance.converter. 편집기 저장 규칙이 바뀌면 올린다. */
private const val EDITOR_CONVERTER = "app-editor-1"

/** 제공 곡과 같은 디코더로 검증한다. 음 목록은 요청마다 읽는다(CAT-04와 같다). */
class CommonSheetScores(
    private val json: JsonMapper,
    private val decoder: CommonScoreTimelineDecoder,
    private val pitches: () -> List<ScorePitch>,
) : SheetScores {
    override fun timeline(scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String): List<ScoreTimelineEvent>? =
        try {
            // 디코더는 곡 ID를 쓰지 않는다. 내 악보의 BPM을 곡 기본 BPM 자리에 넣어 같은 범위 검사를 받는다.
            decoder.decode(PublishedSongScore(0, scoreVersion, noteCount, bpm, bpmUnit, scoreJson), pitches()).events
        } catch (_: InvalidStoredScore) {
            null
        }

    override fun recognitionUnregisteredPitches(
        scoreJson: String, scoreVersion: Int, noteCount: Int, bpm: Int, bpmUnit: String,
    ): List<UnregisteredScorePitch>? =
        try {
            decoder.unregisteredPitches(PublishedSongScore(0, scoreVersion, noteCount, bpm, bpmUnit, scoreJson), pitches())
        } catch (_: InvalidStoredScore) {
            null
        }

    override fun prepare(scoreJson: String, scoreVersion: Int): PreparedScore {
        val root = read(scoreJson) ?: return PreparedScore.Malformed
        val score = root.path("score")
        val music = root.path("music")
        val columns = root.path("columns")
        // Jackson 3의 stringValue()·intValue()는 타입이 다르면 예외를 던지므로 읽기 전에 타입을 본다.
        if (!shaped(root) || score !is ObjectNode || !music.path("bpmUnit").isString || !music.path("jangdan").isString ||
            !root.path("events").all { it.path("kind").isString }
        ) {
            return PreparedScore.Malformed
        }
        val unfilled = columns.flatMap { column -> column.path("cells").filter { it.path("units").let { units -> units.isArray && units.isEmpty } } }
        // 알릴 칸 ID가 없는 빈 칸은 앱이 강조할 수 없으므로 악보 형식 오류다.
        if (unfilled.any { cell -> cell.path("id").let { !it.isString || it.stringValue().isEmpty() } }) return PreparedScore.Malformed
        if (unfilled.isNotEmpty()) return PreparedScore.Unfilled(unfilled.map { it.path("id").stringValue() })
        // DATA 생성기(validate_common)는 provenance.sourceId가 score.sourceId와 같아야 재생한다.
        val sourceId = score.path("sourceId").takeIf { it.isString && it.stringValue().isNotEmpty() }?.stringValue()
            ?: return PreparedScore.Malformed
        score.put("scoreVersion", scoreVersion)
        fillProvenance(root as ObjectNode, sourceId)
        return PreparedScore.Ready(
            json.writeValueAsString(root), columns.size(), root.path("events").count { it.path("kind").stringValue() == "NOTE" },
            music.path("jangdan").stringValue(), music.path("bpmUnit").stringValue(),
        )
    }

    override fun unfilledCellCount(scoreJson: String): Int? {
        val root = read(scoreJson)?.takeIf(::shaped) ?: return null
        return root.path("columns").sumOf { column -> column.path("cells").count { it.path("units").let { units -> units.isArray && units.isEmpty } } }
    }

    /**
     * 앱 편집기 악보에는 변환한 .jgb.json이 없으므로 클라이언트 값 대신 서버가 정한다. jgbSha256 자리에는
     * provenance를 뺀 저장 문서(새 scoreVersion 반영)의 SHA-256을 넣어 DATA 스키마 형식(16진 64자)을 맞춘다.
     * 받은 키 순서대로 직렬화하므로 같은 문서면 같은 값이지만 키 순서가 다르면 값도 다르다.
     */
    private fun fillProvenance(root: ObjectNode, sourceId: String) {
        root.remove("provenance")
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(root)))
        root.putObject("provenance").put("sourceId", sourceId).put("jgbSha256", digest).put("review", "inferred").put("converter", EDITOR_CONVERTER)
    }

    private fun read(scoreJson: String): JsonNode? = try {
        json.readTree(scoreJson)
    } catch (_: JacksonException) {
        null
    }

    // 초안도 확정 저장과 같은 최상위 모양이어야 다시 열 수 있다. 칸 안의 검증은 확정 저장에서 한다.
    private fun shaped(root: JsonNode) = root.path("schemaVersion").let { it.isInt && it.intValue() == 1 } && root.path("score").isObject &&
        root.path("music").isObject && root.path("columns").isArray && root.path("events").isArray && root.path("provenance").isObject &&
        root.path("columns").all { it.isObject && it.path("cells").isArray && it.path("cells").all { cell -> cell.isObject } }
}
