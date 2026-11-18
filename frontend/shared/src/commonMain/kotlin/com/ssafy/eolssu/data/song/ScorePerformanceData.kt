package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.ScorePerformance
import com.ssafy.eolssu.core.model.ScorePerformanceSegment
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/** The caller supplies the SHA-256 of the actual source JSON bytes, never the JGB provenance hash. */
fun ModelAudio.withPerformance(json: String, song: Song, expectedSourceSha256: String): ModelAudio {
    val data = try {
        val root = PerformanceJson.parseToJsonElement(json) as? JsonObject
            ?: throw IllegalArgumentException("Performance must be an object")
        requireTypes(root, listOf("schema", "policy", "sourceSha256", "sourceId", "instrument", "bpmUnit", "audioSha256"),
            listOf("scoreVersion", "bpm", "durationMs"))
        val rawSegments = root["segments"] as? JsonArray ?: throw IllegalArgumentException("Segments must be an array")
        rawSegments.forEach { raw ->
            val segment = raw as? JsonObject ?: throw IllegalArgumentException("Segment must be an object")
            requireTypes(segment, listOf("occurrenceId", "eventId", "cellId"),
                listOf("row", "col", "startTick", "endTick", "startMs", "endMs"))
            if ("tempoRatio" in segment) requireTypes(segment, emptyList(), listOf("tempoRatio"))
            if ("accompanimentStartTick" in segment || "accompanimentEndTick" in segment) {
                requireTypes(segment, emptyList(), listOf("accompanimentStartTick", "accompanimentEndTick"))
            }
            require((segment["sigimsae"] as? JsonArray)?.all { (it as? JsonPrimitive)?.isString == true } == true) { "Symbols must be strings" }
        }
        PerformanceJson.decodeFromJsonElement<PerformanceData>(root)
    } catch (error: SerializationException) {
        throw IllegalArgumentException("Invalid common-score performance JSON", error)
    }
    require(data.schema == "common-score-performance/v1" && data.policy == "educational-preview/v1") { "Unsupported performance schema/policy" }
    require(Sha256.matches(expectedSourceSha256) && data.sourceSha256 == expectedSourceSha256) { "Performance source SHA-256 mismatch" }
    require(Sha256.matches(data.audioSha256) && data.audioSha256 == sha256) { "Performance audio SHA-256 mismatch" }
    val source = requireNotNull(song.score.source) { "Performance requires a common score" }
    val timing = requireNotNull(song.score.timing)
    require(copy(performance = null).matches(song) && data.scoreVersion == scoreVersion && data.scoreVersion == source.scoreVersion &&
        data.sourceId == source.sourceId && data.instrument == source.instrument &&
        data.bpm.isFinite() && data.bpm == bpm.toDouble() && data.bpmUnit == bpmUnit) { "Performance score/audio identity mismatch" }
    require(data.instrument in setOf("DANSO", "SOGEUM", "JEONGAK_DAEGEUM", "SANJO_DAEGEUM")) { "Unsupported performance instrument" }
    require(data.durationMs.isFinite() && data.durationMs > 0 && startOffsetMs >= 0 &&
        data.durationMs + startOffsetMs <= durationMs + 1.0) { "Performance exceeds the audio duration" }
    require(data.segments.isNotEmpty()) { "Performance has no display segments" }
    val units = song.displayUnits.associateBy { it.key }
    val occurrences = mutableSetOf<String>()
    val annotations = mutableMapOf<ScoreUnitKey, List<String>>()
    var end = 0.0
    val totalTicks = song.timeline.maxOf { requireNotNull(it.startTick) + requireNotNull(it.durationTick) }.toDouble()
    val segments = data.segments.map { segment ->
        require(segment.occurrenceId.isNotBlank() && occurrences.add(segment.occurrenceId)) { "Duplicate/blank performance occurrence" }
        val key = ScoreUnitKey(segment.cellId, segment.row, segment.col)
        val unit = requireNotNull(units[key]) { "Unknown performance display slot: $key" }
        require(segment.eventId == unit.event.id) { "Performance event/display mismatch: $key" }
        val startTick = unit.unitStart * timing.ticksPerJeonggan
        val endTick = (unit.unitStart + unit.unitDuration) * timing.ticksPerJeonggan
        require(segment.startTick.isFinite() && segment.endTick.isFinite() &&
            near(segment.startTick, startTick) && near(segment.endTick, endTick)) { "Performance changed written ticks: $key" }
        require(segment.startMs.isFinite() && segment.endMs.isFinite() && segment.startMs >= 0 &&
            segment.endMs > segment.startMs && near(segment.startMs, end)) { "Performance time must be continuous and ordered" }
        require(segment.tempoRatio.isFinite() && segment.tempoRatio > 0) { "Performance tempo ratio must be positive" }
        val accompanimentStart = segment.accompanimentStartTick ?: startTick
        val accompanimentEnd = segment.accompanimentEndTick ?: endTick
        require(accompanimentStart.isFinite() && accompanimentEnd.isFinite() && accompanimentStart >= 0 &&
            accompanimentEnd > accompanimentStart && accompanimentEnd <= totalTicks + 0.000001) { "Invalid accompaniment clock range" }
        require(segment.sigimsae.all { it.isNotBlank() && it.none(Char::isISOControl) }) { "Invalid performance symbol" }
        val previous = annotations.put(key, segment.sigimsae.toList())
        require(previous == null || previous == segment.sigimsae) { "Repeated slot changed its notation: $key" }
        end = segment.endMs
        ScorePerformanceSegment(segment.occurrenceId, unit, startTick, endTick,
            segment.startMs, segment.endMs, segment.sigimsae.toList(), segment.tempoRatio,
            accompanimentStart, accompanimentEnd)
    }
    require(near(end, data.durationMs)) { "Performance duration does not match its final segment" }
    require(annotations.keys == units.keys) { "Performance omitted written display slots" }
    song.timeline.forEach { event ->
        val symbols = song.displayUnits.filter { it.event.id == event.id }.flatMap { annotations.getValue(it.key) }
        require(symbols == event.sigimsae) { "Performance changed original event symbols: ${event.id}" }
    }
    return copy(performance = ScorePerformance(data.sourceSha256, data.audioSha256, data.durationMs,
        segments, song.id, song.score, song.baseBpm, durationMs, startOffsetMs))
}

private fun near(left: Double, right: Double): Boolean = abs(left - right) <= 0.000001
private fun requireTypes(value: JsonObject, strings: List<String>, numbers: List<String>) {
    require(strings.all { (value[it] as? JsonPrimitive)?.isString == true }) { "Expected performance string field" }
    require(numbers.all { key -> (value[key] as? JsonPrimitive)?.let { !it.isString && it.doubleOrNull?.isFinite() == true } == true }) {
        "Expected finite performance number field"
    }
}
private val Sha256 = Regex("[a-f0-9]{64}")
private val PerformanceJson = Json { ignoreUnknownKeys = false; isLenient = false }

@Serializable
private data class PerformanceData(
    val schema: String, val policy: String, val sourceSha256: String, val scoreVersion: Int,
    val sourceId: String, val instrument: String, val bpm: Double, val bpmUnit: String,
    val audioSha256: String, val durationMs: Double, val segments: List<PerformanceSegmentData>,
)

@Serializable
private data class PerformanceSegmentData(
    val occurrenceId: String, val eventId: String, val cellId: String, val row: Int, val col: Int,
    val startTick: Double, val endTick: Double, val startMs: Double, val endMs: Double,
    val sigimsae: List<String>,
    val tempoRatio: Double = 1.0,
    val accompanimentStartTick: Double? = null,
    val accompanimentEndTick: Double? = null,
)
