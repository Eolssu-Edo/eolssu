package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.application.PreparedScore
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionJob
import java.security.MessageDigest
import java.util.HexFormat
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

data class BuiltRecognition(
    val scoreJson: String?, val draftJson: String?, val columnCount: Int, val noteCount: Int,
    val pendingCellCount: Int, val resultJson: String, val jangdanName: String, val bpm: Int, val bpmUnit: String,
)

/** OMR 응답을 앱 계약으로 변환한다. 저장 전에 기존 공통 악보 검증기를 거친다. */
class RecognitionResultBuilder(private val json: JsonMapper,private val scores: SheetScores) {
    fun build(job: ConversionJob,response: JsonNode,defaultBpm: Int=60): BuiltRecognition {
        if(!response.path("jgb").isObject) throw RecognitionFailure("SERVER_ERROR")
        val root=response.path("score").deepCopy() as? ObjectNode ?: throw RecognitionFailure("SERVER_ERROR")
        val score=root.path("score") as? ObjectNode ?: throw RecognitionFailure("SERVER_ERROR")
        val sourceId="sheet-${job.sheetId}"
        score.put("sourceId",sourceId).put("scoreVersion",1).put("title",job.title)
        val converter=root.path("provenance").path("converter").takeIf { it.isString }?.asString() ?: "omr"
        val digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(response.path("jgb"))))
        root.putObject("provenance").put("sourceId",sourceId).put("jgbSha256",digest).put("review","inferred").put("converter",converter)
        val raw=json.writeValueAsString(root)
        val unfilled=scores.unfilledCellCount(raw) ?: throw RecognitionFailure("SERVER_ERROR")
        val prepared=scores.prepare(raw,1)
        if(prepared is PreparedScore.Malformed) throw RecognitionFailure("SERVER_ERROR")
        val bpm=job.bpm ?: defaultBpm
        val unit=root.path("music").path("bpmUnit").asString()
        val notes=root.path("events").count { it.path("kind").asString()=="NOTE" }
        val unregistered = if (unfilled > 0) emptyList() else
            scores.recognitionUnregisteredPitches(raw, 1, notes, bpm, unit) ?: throw RecognitionFailure("SERVER_ERROR")
        val cells=root.path("columns").flatMap { it.path("cells").toList().map { c -> c.path("id").asString() } }.toSet()
        val events=root.path("events").toList().map { it.path("eventId").takeIf(JsonNode::isString)?.asString() ?: it.path("id").asString() }.toSet()
        val details=json.createObjectNode()
        val issues=details.putArray("issues")
        val notices=details.putArray("notices")
        val pending=mutableSetOf<String>()
        for((index,item) in response.path("issues").withIndex()) {
            val cell=item.path("cellId").takeIf { it.isString && it.asString() in cells }?.asString()
            val event=item.path("eventId").takeIf { it.isString && it.asString() in events }?.asString()
            val mapped=annotation(item,response)
            if(cell==null && event==null) { notices.add(mapped); continue }
            mapped.put("issueId","i${index+1}").put("cellId",cell).put("eventId",event).putArray("candidates")
            issues.add(mapped); pending += cell ?: checkNotNull(event)
        }
        response.path("notices").forEach { notices.add(annotation(it,response)) }
        val recognitionIssueCount = response.path("issues").size()
        unregistered.forEachIndexed { index, note ->
            val issue = issues.addObject().put("issueId", "i${recognitionIssueCount + index + 1}")
                .put("type", "UNREADABLE").put("cellId", note.cellId).put("eventId", note.eventId)
                .putNull("pageIdx").putNull("bbox")
            issue.putArray("candidates")
            issue.putObject("detail").put("reason", "UNREGISTERED_PITCH").put("yul", note.yul).put("octave", note.octave)
            pending += note.cellId
        }
        val draft=if(unfilled>0 || unregistered.isNotEmpty()) json.createObjectNode().put("title",job.title).put("bpm",bpm)
            .putNull("jangdanId").set("score",root) else null
        return BuiltRecognition(if(draft==null) raw else null,draft?.let(json::writeValueAsString),root.path("columns").size(),notes,
            pending.size,json.writeValueAsString(details),root.path("music").path("jangdan").asString(),bpm,unit)
    }

    private fun annotation(item: JsonNode,response: JsonNode): ObjectNode {
        val mapped=json.createObjectNode().put("type",item.path("kind").asString())
        mapped.set("detail",item.path("detail").takeIf { it.isObject } ?: json.nullNode())
        val page=item.path("page").takeIf { it.isInt }?.intValue()
        if(page==null) mapped.putNull("pageIdx") else mapped.put("pageIdx",page)
        val dimensions=response.path("pages").firstOrNull { it.path("index").isInt && it.path("index").intValue()==page }
        val bbox=item.path("bbox")
        if(dimensions!=null && bbox.isArray && bbox.size()==4 && bbox.all(JsonNode::isNumber)) {
            val w=dimensions.path("width").doubleValue(); val h=dimensions.path("height").doubleValue()
            if(w<=0 || h<=0) throw RecognitionFailure("SERVER_ERROR")
            val x=bbox.get(0).doubleValue()/w; val y=bbox.get(1).doubleValue()/h
            val width=bbox.get(2).doubleValue()/w; val height=bbox.get(3).doubleValue()/h
            if(listOf(x,y,width,height).any { !it.isFinite() || it !in 0.0..1.0 } || x+width>1.000001 || y+height>1.000001) throw RecognitionFailure("SERVER_ERROR")
            mapped.putObject("bbox").put("x",x).put("y",y).put("w",width).put("h",height)
        } else mapped.putNull("bbox")
        return mapped
    }
}
