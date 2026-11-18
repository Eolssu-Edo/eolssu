package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** 악보를 분석 음표로 풀 수 없다. 분석 흐름은 AI를 부르지 않고 `SERVER_ERROR`로 기록한다. */
class ScoreNotesException(message: String) : RuntimeException(message)

/**
 * 공통 악보 데이터(`songs.score_json`, schemaVersion 1)의 연습 구간을 분석 음표로 푼다(PRA-03 '음표 번호'·'음표 값').
 * 음표 번호는 `events` 중 `NOTE`만 연주 순서대로 0부터 센다. 쉼은 정답 음이 없어 채점 음표가 아니다.
 */
class ScoreJsonNotes(private val jsonMapper: JsonMapper) {
    fun notes(scoreJson: String, startIdx: Int, endIdx: Int, pitches: Map<Pair<String, Int>, ScorePitchRow>): List<ScoreNote> {
        val score = try {
            jsonMapper.readTree(scoreJson)
        } catch (e: JacksonException) {
            throw ScoreNotesException("score_json is not JSON")
        }
        if (score.path("schemaVersion").asInt() != SCHEMA_VERSION) throw ScoreNotesException("score_json schemaVersion is not $SCHEMA_VERSION")
        val ticksPerBeat = score.path("music").int("ticksPerBeat").takeIf { it > 0 }
            ?: throw ScoreNotesException("music.ticksPerBeat must be positive")
        val events = score.path("events").takeIf { it.isArray } ?: throw ScoreNotesException("events is not an array")
        // 모르는 kind를 그냥 건너뛰면 뒤 음표 번호가 모두 밀려 엉뚱한 음으로 채점된다.
        if (events.any { it.path("kind").asString() !in EVENT_KINDS }) throw ScoreNotesException("events has an unknown kind")
        val notes = events.filter { it.path("kind").asString() == "NOTE" }
        if (startIdx < 0 || endIdx < startIdx || endIdx >= notes.size) {
            throw ScoreNotesException("practice range $startIdx..$endIdx is outside ${notes.size} notes")
        }
        return (startIdx..endIdx).map { idx ->
            val event = notes[idx]
            val pitch = pitches[event.path("yul").asString() to event.int("octave")] ?: throw ScoreNotesException("note $idx has no pitch")
            val durationTick = event.int("durationTick").takeIf { it > 0 } ?: throw ScoreNotesException("note $idx has no length")
            ScoreNote(
                idx = idx,
                semitone = pitch.semitone.toInt(),
                startBeat = event.int("startTick").toDouble() / ticksPerBeat,
                beats = durationTick.toDouble() / ticksPerBeat,
                sigimsae = !event.path("sigimsae").isEmpty(),
                pitchId = pitch.id,
            )
        }
    }

    private fun JsonNode.int(field: String): Int =
        path(field).takeIf { it.isInt }?.intValue() ?: throw ScoreNotesException("$field is not an integer")

    private companion object {
        const val SCHEMA_VERSION = 1
        val EVENT_KINDS = setOf("NOTE", "REST")
    }
}
