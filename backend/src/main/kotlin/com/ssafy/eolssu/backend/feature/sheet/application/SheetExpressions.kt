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

    fun prepare(source: String?, originalScore: String?, preparedScore: String, oldBpm: Int, newBpm: Int): String? {
        if (source == null) return null
        require(source.toByteArray().size <= EditUserSheet.MAX_SCORE_BYTES)
        val value = json.readTree(source) as? ObjectNode ?: throw IllegalArgumentException("expressions")
        require(value.path("schema").asString() == "common-score-expressions/v1")
        require(originalScore != null && value.path("sourceSha256").asString() == hash(originalScore))
        val digest = hash(preparedScore)
        val plan = value.get("performancePlan")
        if (plan != null && !plan.isNull) {
            require(plan is ObjectNode && plan.path("input_sha256").asString() == hash(originalScore))
            if (positions(json.readTree(originalScore)) != positions(json.readTree(preparedScore))) {
                throw SheetExpressionPositionChanged()
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
