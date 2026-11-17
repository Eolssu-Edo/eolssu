package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.sheetAudioSha256
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

class SheetExpressionPositionChanged : IllegalArgumentException("Performance locations changed")

/** Optional numeric performance companion. Absence never reconstructs an original tempo plan. */
class SheetExpressions(private val json: JsonMapper) {
    /** Omission keeps the latest draft plan; a missing or JSON-null draft field falls back to the confirmed plan. */
    fun current(sheet: UserSheet): String? = sheet.draft?.json?.let(json::readTree)?.get("expressions")
        ?.takeUnless { it.isNull }?.let(json::writeValueAsString) ?: sheet.expressionsJson

    /** MySQL JSON may reorder object keys; source identity must survive that round trip. */
    fun hash(score: String) = json.writeValueAsBytes(canonical(json.readTree(score))).sheetAudioSha256()

    private fun canonical(value: JsonNode): JsonNode = when {
        value.isObject -> json.createObjectNode().also { out ->
            value.properties().sortedBy { it.key }.forEach { out.set(it.key, canonical(it.value)) }
        }
        value.isArray -> json.createArrayNode().also { out -> value.forEach { out.add(canonical(it)) } }
        else -> value
    }

    /**
     * Binds a companion to the prepared score. A companion bound to [originalScore] (the confirmed score; older clients)
     * keeps its coordinates only while the cell grid is unchanged. A companion bound to [requestScore] (the score in the
     * same request, before preparation) was already remapped by the client after adding or deleting gak, so its
     * coordinates are checked against that score instead (S15P31E102-1095).
     */
    fun prepare(source: String?, originalScore: String?, preparedScore: String, oldBpm: Int, newBpm: Int,
        requestScore: String? = null): String? {
        if (source == null) return null
        require(source.toByteArray().size <= EditUserSheet.MAX_SCORE_BYTES)
        val value = json.readTree(source) as? ObjectNode ?: throw IllegalArgumentException("expressions")
        require(value.path("schema").asString() == "common-score-expressions/v1")
        val originalHash = originalScore?.let(::hash)
        val requestHash = requestScore?.let(::hash)
        val bound = value.path("sourceSha256").asString()
        require(bound.isNotEmpty() && (bound == originalHash || bound == requestHash))
        val digest = hash(preparedScore)
        val plan = value.get("performancePlan")
        if (plan != null && !plan.isNull) {
            require(plan is ObjectNode)
            when (plan.path("input_sha256").asString()) {
                originalHash -> if (positions(json.readTree(requireNotNull(originalScore))) != positions(json.readTree(preparedScore))) {
                    throw SheetExpressionPositionChanged()
                }
                requestHash -> if (requestHash != null && !coordinatesExist(plan, json.readTree(preparedScore))) throw SheetExpressionPositionChanged()
                else -> throw IllegalArgumentException("performance plan binding")
            }
            plan.put("input_sha256", digest)
            val instrument = json.readTree(preparedScore).path("score").path("instrument").asString().lowercase().replace('_', '-')
            plan.put("schema", "$instrument-performance/v1")
            plan.path("tempos").forEach { cue ->
                require(cue is ObjectNode && cue.path("bpm").isNumber)
                val scaled = cue.path("bpm").asDouble() * newBpm / oldBpm
                require(scaled.isFinite() && scaled > 0)
                cue.put("bpm", scaled)
                cue.get("start_bpm")?.let { start ->
                    require(start.isNumber)
                    val scaledStart = start.asDouble() * newBpm / oldBpm
                    require(scaledStart.isFinite() && scaledStart > 0)
                    cue.put("start_bpm", scaledStart)
                }
            }
        }
        value.put("sourceSha256", digest)
        return json.writeValueAsString(value)
    }

    /** Every numeric cue and repeat must point at a real (gak, cell) of [score]; a ramp end may be the score end. */
    private fun coordinatesExist(plan: JsonNode, score: JsonNode): Boolean {
        val sizes = score.path("columns").toList().map { it.path("cells").size() }
        fun at(gak: JsonNode, cell: JsonNode, end: Boolean = false): Boolean {
            if (!gak.isInt || !cell.isInt) return false
            val g = gak.asInt()
            val c = cell.asInt()
            return (end && g == sizes.size + 1 && c == 1) || (g in 1..sizes.size && c in 1..sizes[g - 1])
        }
        val tempos = plan.path("tempos").toList().all { cue ->
            at(cue.path("gak"), cue.path("cell")) &&
                (!cue.has("end_gak") && !cue.has("end_cell") || at(cue.path("end_gak"), cue.path("end_cell"), end = true))
        }
        val repeats = plan.path("repeats").toList().all { repeat ->
            val start = repeat.path("start_gak")
            val end = repeat.path("end_gak")
            start.isInt && end.isInt && start.asInt() in 1..end.asInt() && end.asInt() <= sizes.size
        }
        return tempos && repeats
    }

    /**
     * Cue coordinates are (gak, cell) and marks are bound by cell id. Splitting or merging inside a cell and a jangdan
     * retiming (tick ratio, BPM unit) keep every cell in place, so only the cell grid is compared (S15P31E102-1091).
     * Adding or removing gak or cells still moves the coordinates and is rejected.
     */
    private fun positions(score: JsonNode): List<Any> =
        score.path("columns").toList().map { column -> listOf(column.path("index").toString(), column.path("cells").toList().map { cell ->
            listOf(cell.path("id").toString(), cell.path("index").toString())
        }) }
}
