package com.ssafy.eolssu.backend.feature.song.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.song.domain.*
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** CAT-04 uses the same NOTE indices, pitch lookup and beat conversion as PRA-03. */
class CommonScoreTimelineDecoder(private val json: JsonMapper) : SongScoreDecoder {
    override fun decode(score: PublishedSongScore, pitches: List<ScorePitch>): DecodedSongScore =
        decode(score, pitches, null)

    /** Authoring uses the renderer preflight rather than the published catalog's legacy BPM band. */
    fun decodeAuthoring(score: PublishedSongScore, pitches: List<ScorePitch>): DecodedSongScore =
        decode(score, pitches, null, authoring = true)

    /** 시간축을 검증하면서 초안에서만 편집할 수 있는 미등록 율명을 찾는다. */
    fun unregisteredPitches(score: PublishedSongScore, pitches: List<ScorePitch>, authoring: Boolean = false): List<UnregisteredScorePitch> =
        buildList { decode(score, pitches, this, authoring) }

    private fun decode(
        score: PublishedSongScore,
        pitches: List<ScorePitch>,
        unregistered: MutableList<UnregisteredScorePitch>?,
        authoring: Boolean = false,
    ): DecodedSongScore = try {
        decodeValidated(score, pitches, unregistered, authoring)
    } catch (_: JacksonException) {
        throw InvalidStoredScore()
    }

    private fun decodeValidated(
        stored: PublishedSongScore,
        pitches: List<ScorePitch>,
        unregistered: MutableList<UnregisteredScorePitch>?,
        authoring: Boolean = false,
    ): DecodedSongScore {
        val score = json.readTree(stored.scoreJson)
        val schemaVersion = score.int("schemaVersion")
        ensure(schemaVersion == 1 && stored.scoreVersion > 0 && stored.noteCount >= 0)
        ensure(score.path("score").int("scoreVersion") == stored.scoreVersion)
        val music = score.path("music")
        val ticksPerBeat = music.int("ticksPerBeat")
        val ticksPerJeonggan = music.int("ticksPerJeonggan")
        ensure(ticksPerBeat > 0 && ticksPerJeonggan > 0)
        ensure((if (authoring) stored.defaultBpm > 0 else stored.defaultBpm in 30..400) && stored.bpmUnit in BPM_UNITS)
        ensure(music.text("bpmUnit") == stored.bpmUnit)

        val events = score.array("events").toList()
        val ids = events.map { it.text("id") }
        ensure(ids.size == ids.toSet().size)
        val positions = positions(score, ids.toSet())
        val pitchesByYul = pitches.associateBy { it.yul to it.octave }
        ensure(pitchesByYul.size == pitches.size && pitches.all { it.id > 0 })
        val registeredYuls = if (unregistered != null) pitches.mapTo(hashSetOf(), ScorePitch::yul) else emptySet()
        ensure(events.count { it.text("kind") == "NOTE" } == stored.noteCount)
        var noteIndex = 0
        var endTick = 0L
        val usedPitches = mutableListOf<ScorePitch>()
        val timeline = events.map { event ->
            val id = event.text("id")
            val match = EVENT_ID.matchEntire(id) ?: throw InvalidStoredScore()
            val column = match.groupValues[1].toIntOrNull() ?: throw InvalidStoredScore()
            val cell = match.groupValues[2].toIntOrNull() ?: throw InvalidStoredScore()
            val row = match.groupValues[3].toIntOrNull() ?: throw InvalidStoredScore()
            val col = match.groupValues[4].toIntOrNull() ?: throw InvalidStoredScore()
            val cellId = event.text("cellId")
            ensure(cellId == "c$column-j$cell")
            val position = positions[id]?.singleOrNull { it.cellId == cellId && it.row == row && it.col == col }
                ?: throw InvalidStoredScore()
            val start = event.int("startTick")
            val duration = event.int("durationTick")
            ensure(start >= 0 && duration > 0 && start.toLong() == endTick)
            endTick = start.toLong() + duration
            ensure(event.path("breathAfter").isBoolean && event.array("sigimsae").all { it.isString })
            val kind = event.text("kind")
            val onset = start.toDouble() / ticksPerBeat
            val beats = duration.toDouble() / ticksPerBeat
            if (kind == "NOTE") {
                val yul = event.text("yul")
                val octave = event.int("octave")
                val pitch = pitchesByYul[yul to octave]
                if (pitch != null) {
                    usedPitches += pitch
                } else {
                    // Recognition drafts preserve the original MIDI octave; final decoding still requires a registered pitch.
                    if (unregistered == null || yul !in registeredYuls || octave !in -6..5) throw InvalidStoredScore()
                    unregistered += UnregisteredScorePitch(id, cellId, yul, octave)
                }
                ScoreTimelineEvent(id, kind, noteIndex++, pitch?.id?.toString(), column, cell, position.subIdx,
                    start, duration, onset, beats)
            } else {
                ensure(kind == "REST" && event.path("yul").isNull && event.path("octave").isNull)
                ScoreTimelineEvent(id, kind, null, null, column, cell, position.subIdx, start, duration, onset, beats)
            }
        }
        val cellCount = score.array("columns").sumOf { it.array("cells").size().toLong() }
        ensure(endTick == cellCount * ticksPerJeonggan)
        return DecodedSongScore(schemaVersion, timeline, ScoreNotationCapabilities(
            hanja = usedPitches.isNotEmpty() && usedPitches.all { it.hanja.isNotBlank() },
            hangul = usedPitches.isNotEmpty() && usedPitches.all { it.hangul.isNotBlank() },
        ), stored.sigimsaePlacementsJson?.let { sigimsaePlacements(it, events, positions) })
    }

    /**
     * Stored slot positions are returned only when, for every event, its slots' symbols in display order
     * equal the event's sigimsae. Malformed or stale positions (e.g. after a score replacement) yield null so
     * the client keeps the event-level layout; they never make the score itself unavailable.
     */
    private fun sigimsaePlacements(raw: String, events: List<JsonNode>, positions: Map<String, List<UnitPosition>>): List<SigimsaePlacement>? = try {
        val node = json.readTree(raw)
        ensure(node.isArray)
        val slots = positions.values.flatten().map { Triple(it.cellId, it.row, it.col) }.toSet()
        val bySlot = linkedMapOf<Triple<String, Int, Int>, List<String>>()
        node.forEach { item ->
            val symbols = item.array("symbols").toList().map { symbol ->
                symbol.takeIf { it.isString }?.stringValue()?.takeIf { it.isNotBlank() } ?: throw InvalidStoredScore()
            }
            val slot = Triple(item.text("cellId"), item.int("row"), item.int("col"))
            ensure(symbols.isNotEmpty() && slot in slots && bySlot.put(slot, symbols) == null)
        }
        events.forEach { event ->
            val placed = positions[event.text("id")].orEmpty().flatMap { bySlot[Triple(it.cellId, it.row, it.col)].orEmpty() }
            ensure(placed == event.array("sigimsae").toList().map { it.stringValue() })
        }
        events.flatMap { event ->
            positions[event.text("id")].orEmpty().mapNotNull { p ->
                bySlot[Triple(p.cellId, p.row, p.col)]?.let { SigimsaePlacement(p.cellId, p.row, p.col, it) }
            }
        }
    } catch (_: InvalidStoredScore) {
        null
    } catch (_: JacksonException) {
        null
    }

    /** subIdx is the flattened top-to-bottom, left-to-right unit position in its starting cell. */
    private fun positions(score: JsonNode, eventIds: Set<String>): Map<String, List<UnitPosition>> {
        val result = mutableMapOf<String, MutableList<UnitPosition>>()
        score.array("columns").forEachIndexed { columnIndex, column ->
            ensure(column.int("index") == columnIndex)
            column.array("cells").forEachIndexed { cellIndex, cell ->
                ensure(cell.int("index") == cellIndex)
                val cellId = cell.text("id")
                ensure(cellId == "c$columnIndex-j$cellIndex")
                val units = cell.array("units").toList().sortedWith(compareBy({ it.int("row") }, { it.int("col") }))
                ensure(units.isNotEmpty() && units.map { it.int("row") to it.int("col") }.distinct().size == units.size)
                units.forEachIndexed { subIdx, unit ->
                    val row = unit.int("row")
                    val col = unit.int("col")
                    val eventId = unit.text("eventId")
                    ensure(row >= 0 && col >= 0 && eventId in eventIds && unit.path("symbol").isString)
                    result.getOrPut(eventId) { mutableListOf() }.add(UnitPosition(cellId, row, col, subIdx))
                }
            }
        }
        return result
    }

    private data class UnitPosition(val cellId: String, val row: Int, val col: Int, val subIdx: Int)
    private fun JsonNode.int(field: String): Int = path(field).takeIf { it.isInt }?.intValue() ?: throw InvalidStoredScore()
    private fun JsonNode.text(field: String): String = path(field).takeIf { it.isString }?.stringValue() ?: throw InvalidStoredScore()
    private fun JsonNode.array(field: String): JsonNode = path(field).takeIf { it.isArray } ?: throw InvalidStoredScore()
    private fun ensure(condition: Boolean) { if (!condition) throw InvalidStoredScore() }

    private companion object {
        val EVENT_ID = Regex("c([0-9]+)-j([0-9]+)-r([0-9]+)-u([0-9]+)")
        val BPM_UNITS = setOf("QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT")
    }
}
