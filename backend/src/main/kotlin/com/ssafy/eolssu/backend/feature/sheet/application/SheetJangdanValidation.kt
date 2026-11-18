package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.catalog.domain.JangdanTimingCatalog
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetJangdan
import tools.jackson.databind.json.JsonMapper

/** New writes/exports only; old score reads are not rewritten or gated by this policy. */
class SheetJangdanValidation(private val json: JsonMapper) {
    fun valid(scoreJson: String, jangdan: SheetJangdan?): Boolean = runCatching {
        val score = json.readTree(scoreJson)
        val music = score.path("music")
        if (jangdan == null) return@runCatching music.path("jangdan").isString && music.path("jangdan").asString().isEmpty()
        if (music.path("jangdan").asString() != jangdan.name || music.path("bpmUnit").asString() != jangdan.bpmUnit) return@runCatching false
        val pattern = json.readTree(jangdan.pattern ?: return@runCatching false)
        val cell = JangdanTimingCatalog.cellEighths(jangdan.name, jangdan.bpmUnit, pattern) ?: return@runCatching false
        val cycle = JangdanTimingCatalog.cycleTicks(pattern.path("beats").size(), cell, jangdan.bpmUnit,
            music.path("ticksPerBeat").longValue()) ?: return@runCatching false
        JangdanTimingCatalog.matchesGrid(score, cycle)
    }.getOrDefault(false)
}
