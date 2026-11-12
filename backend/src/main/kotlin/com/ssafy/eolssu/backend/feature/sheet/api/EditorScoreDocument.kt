package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** SHT-08을 건너뛸 수 있도록 앱의 CommonScoreData 디코딩 조건을 확인한다. */
internal fun UserSheet.editorInstrumentCode(json: JsonMapper): String? {
    return try {
        // 앱은 초안을 열기 전에 확정본도 디코딩한다.
        val original = scoreJson?.let(json::readTree)
        if (original != null && !original.isEditorScore()) return null

        val document = draft
        val editableScore = if (document == null) {
            original
        } else {
            if (document.baseScoreVersion != scoreVersion || draftRevision < 0) return null
            val draftJson = json.readTree(document.json)
            val bpm = draftJson.path("bpm")
            if (!draftJson.path("title").isString || !bpm.isEditorInt() || bpm.intValue() <= 0) return null
            draftJson.path("score").takeIf { it.isEditorScore() }
        }
        editableScore?.path("score")?.path("instrument")?.stringValue()?.takeIf { it.isNotBlank() }
    } catch (_: JacksonException) {
        null
    }
}

private fun JsonNode.isEditorScore(): Boolean =
    hasFields("schemaVersion", "score", "music", "columns", "events", "provenance") &&
        path("schemaVersion").isEditorInt() &&
        path("score").isEditorHeader() &&
        path("music").isEditorMusic() &&
        path("columns").isArrayOf { it.isEditorColumn() } &&
        path("events").isArrayOf { it.isEditorEvent() } &&
        path("provenance").isEditorProvenance()

private fun JsonNode.isEditorHeader(): Boolean =
    hasFields("title", "instrument", "part", "sourceId", "scoreVersion") &&
        stringFields("title", "instrument", "sourceId") && intFields("part", "scoreVersion")

private fun JsonNode.isEditorMusic(): Boolean =
    hasFields("hwangHz", "jangdan", "bpmUnit", "ticksPerBeat", "ticksPerJeonggan") &&
        path("hwangHz").let { it.isNumber && it.asDouble().isFinite() } &&
        stringFields("jangdan", "bpmUnit") && intFields("ticksPerBeat", "ticksPerJeonggan")

private fun JsonNode.isEditorColumn(): Boolean =
    hasFields("index", "cells") && intFields("index") && path("cells").isArrayOf { it.isEditorCell() }

private fun JsonNode.isEditorCell(): Boolean =
    hasFields("id", "index", "units", optional = "lyric") && stringFields("id") && intFields("index") &&
        path("lyric").let { it.isMissingNode || it.isNull || it.isString } &&
        // 빈 units는 아직 채우지 않은 칸이다. 앱은 초안에서 빈 배열도 읽는다.
        path("units").isArrayOf { it.isEditorUnit() }

private fun JsonNode.isEditorUnit(): Boolean =
    hasFields("row", "col", "symbol", "eventId") &&
        intFields("row", "col") && stringFields("symbol", "eventId")

private fun JsonNode.isEditorEvent(): Boolean =
    hasFields("id", "kind", "yul", "octave", "startTick", "durationTick", "cellId", "breathAfter", "sigimsae") &&
        stringFields("id", "kind", "cellId") &&
        path("yul").let { it.isNull || it.isString } &&
        path("octave").let { it.isNull || it.isEditorInt() } &&
        path("startTick").let { it.isIntegralNumber && it.canConvertToLong() } &&
        path("durationTick").let { it.isIntegralNumber && it.canConvertToLong() } &&
        path("breathAfter").isBoolean && path("sigimsae").isArrayOf { it.isString }

private fun JsonNode.isEditorProvenance(): Boolean =
    hasFields("sourceId", "jgbSha256", "review", "converter") &&
        stringFields("sourceId", "jgbSha256", "review", "converter")

private fun JsonNode.hasFields(vararg required: String, optional: String? = null): Boolean =
    isObject && size() == required.size + (if (optional != null && has(optional)) 1 else 0) &&
        required.all { has(it) }

private fun JsonNode.stringFields(vararg fields: String): Boolean = fields.all { path(it).isString }

private fun JsonNode.intFields(vararg fields: String): Boolean = fields.all { path(it).isEditorInt() }

private fun JsonNode.isEditorInt(): Boolean = isIntegralNumber && canConvertToInt()

private fun JsonNode.isArrayOf(matches: (JsonNode) -> Boolean): Boolean = isArray && all(matches)
