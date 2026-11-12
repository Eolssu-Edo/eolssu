package com.ssafy.eolssu.backend.feature.catalog.domain

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** data/reference/jangdans.json is bundled unchanged; DB pattern identity must match it. */
object JangdanTimingCatalog {
    private val json = JsonMapper.builder().build()
    private val rows by lazy {
        requireNotNull(javaClass.getResourceAsStream("/catalog/jangdans.json")).use { input ->
            json.readTree(input).path("rows").associateBy { it.path("name").asString() }
        }
    }

    fun cellEighths(name: String, bpmUnit: String, pattern: JsonNode): Int? {
        val row = rows[name] ?: return null
        if (row.path("bpm_unit").asString() != bpmUnit || row.path("pattern") != pattern) return null
        return row.path("pattern_cell_eighths").takeIf { it.isInt }?.intValue()?.takeIf { it > 0 }
    }

    fun cycleTicks(beatCount: Int, cellEighths: Int, bpmUnit: String, ticksPerBeat: Long): Long? {
        val beatEighths = when (bpmUnit) { "QUARTER" -> 2; "DOTTED_QUARTER" -> 3; else -> return null }
        if (beatCount !in 1..64 || cellEighths !in 1..64 || ticksPerBeat !in 1..1_000_000_000L) return null
        val numerator = beatCount.toLong() * cellEighths * ticksPerBeat
        return (numerator / beatEighths).takeIf { numerator % beatEighths == 0L && it in 1..1_000_000_000L }
    }

    /** A leading REST is valid. Each displayed column contains one or more complete cycles. */
    fun matchesGrid(score: JsonNode, cycleTicks: Long): Boolean {
        val music = score.path("music")
        val ticks = music.path("ticksPerJeonggan")
        val first = score.path("events").path(0)
        val columns = score.path("columns")
        return ticks.isIntegralNumber && ticks.longValue() in 1..1_000_000_000L && cycleTicks > 0 &&
            first.path("cellId").asString() == "c0-j0" && first.path("startTick").isIntegralNumber &&
            first.path("startTick").longValue() == 0L && columns.isArray && !columns.isEmpty && columns.all {
                val cells = it.path("cells")
                cells.isArray && !cells.isEmpty && cells.size().toLong() * ticks.longValue() % cycleTicks == 0L
            }
    }
}
