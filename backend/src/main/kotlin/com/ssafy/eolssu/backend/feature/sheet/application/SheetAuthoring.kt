package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import kotlin.math.ln
import kotlin.math.roundToInt
import java.security.MessageDigest

/** Editing metadata lives beside common-score v1. Source pitches cannot be replaced by a client. */
class SheetAuthoring(private val json: JsonMapper) {
    fun current(sheet: UserSheet): ObjectNode? {
        sheet.authoringJson?.let { return json.readTree(it) as ObjectNode }
        sheet.imported?.resultJson?.let { raw ->
            (json.readTree(raw).path("authoring") as? ObjectNode)?.let { return it.deepCopy() }
        }
        sheet.draft?.json?.let { raw ->
            (json.readTree(raw).path("authoring") as? ObjectNode)?.let { return it.deepCopy() }
        }
        val raw = sheet.scoreJson ?: sheet.draft?.json?.let { json.readTree(it).path("score").toString() } ?: return null
        val score = runCatching { json.readTree(raw) }.getOrNull()?.takeIf {
            val hwang = it.path("music").path("hwangHz")
            hwang.isNumber && hwang.doubleValue().isFinite() && hwang.doubleValue() > 0 &&
                it.path("score").path("instrument").isString && it.path("score").path("scoreVersion").isInt && it.path("events").isArray
        } ?: return null
        return capture(score, "EXISTING_SCORE")
    }

    fun capture(score: JsonNode, kind: String, originals: JsonNode? = null): ObjectNode {
        val root = json.createObjectNode().put("schema", "sheet-authoring/v1")
        val source = root.putObject("source").put("kind", kind)
            .put("instrument", score.path("score").path("instrument").asString())
            .put("scoreVersion", score.path("score").path("scoreVersion").intValue())
            .put("scoreSha256", MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(score)).toHexString())
        source.set("score", score.deepCopy())
        val hwang = score.path("music").path("hwangHz").doubleValue()
        val hwangMidi = (69 + 12 * ln(hwang / 440.0) / ln(2.0)).roundToInt()
        val byId = originals?.takeIf { it.isArray }?.associateBy { it.path("eventId").asString() }.orEmpty()
        val notes = source.putArray("notes")
        score.path("events").filter { it.path("kind").asString() == "NOTE" }.forEach { event ->
            val id = event.path("id").asString()
            val original = byId[id]
            val midi = original?.path("midi")?.takeIf { it.isInt }?.intValue()
                ?: (hwangMidi + YUL.indexOf(event.path("yul").asString()) + 12 * event.path("octave").intValue())
            notes.addObject().put("eventId", id).put("cellId", event.path("cellId").asString()).put("midi", midi)
                .put("startTick", event.path("startTick").longValue()).put("durationTick", event.path("durationTick").longValue())
        }
        return root
    }

    fun merge(sheet: UserSheet, requested: String?): String? {
        val baseline = current(sheet) ?: return null
        if (requested == null) return json.writeValueAsString(baseline)
        val incoming = json.readTree(requested)
        require(incoming.isObject && incoming.path("schema").isString && incoming.path("schema").asString() == "sheet-authoring/v1")
        // A constructed LongNode becomes an IntNode over HTTP/MySQL when the value fits.
        val source = json.readTree(json.writeValueAsBytes(baseline.path("source")))
        val suppliedSource = incoming.path("source")
        val reference = suppliedSource.isObject && suppliedSource.size() == 1 &&
            suppliedSource.path("scoreSha256") == source.path("scoreSha256")
        require(reference || suppliedSource == source) { "Original pitches are immutable" }
        require(incoming.properties().all { it.key in setOf("schema", "source", "adaptation") })
        val adaptation = incoming.path("adaptation")
        if (!adaptation.isMissingNode && !adaptation.isNull) {
            require(adaptation.isObject && adaptation.path("transpose").isInt && adaptation.path("transpose").intValue() in -6..6)
            require(adaptation.path("instrument").isString && adaptation.path("changes").isArray)
            baseline.set("adaptation", adaptation)
        }
        return json.writeValueAsString(baseline)
    }

    companion object { private const val YUL = "황대태협고중유임이남무응" }
}
