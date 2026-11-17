package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.CommonScoreData
import kotlinx.serialization.json.*

/**
 * 정간 번호는 0부터의 연주 순서, 끝은 변화가 끝나는 정간 시작(악보 끝도 허용)이다.
 * 조금 빠르게·느리게(span)의 끝은 앞 빠르기로 돌아가는 정간 시작이다. 화면에서는 그 앞 정간을 끝 정간으로 보인다.
 */
data class SheetTempoChange(val cell: Int, val mark: HanbaeMark?, val targetBpm: Double?, val endCell: Int? = null,
    val startBpm: Double? = null) {
    val resolved: Boolean get() = targetBpm != null && (mark?.needsEnd != true || endCell != null)
}

private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.integer(key: String) = (get(key) as? JsonPrimitive)?.intOrNull
private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.objects(key: String) = (get(key) as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
private fun JsonObject.changed(vararg pairs: Pair<String, JsonElement>) = JsonObject(this + pairs)
private fun SheetDraft.position(index: Int): Pair<Int, Int> =
    if (index == cells.size) columnCount + 1 to 1 else columnOf(index) + 1 to rowOf(index) + 1
private fun SheetDraft.flatPosition(gak: Int?, cell: Int?, terminal: Boolean = false): Int? {
    if (gak == null || cell == null) return null
    if (terminal && gak == columnCount + 1 && cell == 1) return cells.size
    if (gak !in 1..columnCount || cell !in 1..sizes[gak - 1]) return null
    return sizes.take(gak - 1).sum() + cell - 1
}
private val SheetDraft.cellBpmFactor: Double get() = original?.music?.let {
    it.ticksPerBeat.toDouble() / it.ticksPerJeonggan
} ?: 1.0
private val SheetDraft.referenceBpm: Int get() = tempoReferenceBpm ?: bpm
private fun SheetDraft.nativeBpm(value: Double) = value * cellBpmFactor * referenceBpm / bpm
private fun SheetDraft.displayBpm(value: Double) = value / cellBpmFactor * bpm / referenceBpm

internal fun SheetDraft.withTempoExpressions(value: JsonObject?, referenceBpm: Int = bpm): SheetDraft =
    copy(tempoExpressions = value, tempoReferenceBpm = referenceBpm)

private fun SheetDraft.inferredPlacements(score: CommonScoreData): List<JsonObject> {
    val seen = mutableSetOf<String>()
    val events = score.events.associateBy { it.id }
    return score.columns.flatMap { column -> column.cells.flatMap { cell -> cell.units.mapNotNull { unit ->
        val event = events[unit.eventId]?.takeIf { seen.add(it.id) && it.sigimsae.isNotEmpty() } ?: return@mapNotNull null
        buildJsonObject { put("eventId", event.id); put("cellId", cell.id); put("row", unit.row); put("col", unit.col)
            put("symbols", JsonArray(event.sigimsae.map(::JsonPrimitive))) }
    } } }
}

private fun SheetDraft.editingExpressions(): JsonObject = tempoExpressions ?: buildJsonObject {
    put("schema", "common-score-expressions/v1")
    put("placements", JsonArray(original?.let(::inferredPlacements).orEmpty()))
}

/** DATA common_preview의 교육용 자동 반복과 같은 각 단위 2회 재생을 명시화한다. */
private fun initialPerformancePlan(score: CommonScoreData, expression: JsonObject): JsonObject {
    val repeatedCells = expression.objects("placements").filter { placement ->
        (placement["symbols"] as? JsonArray).orEmpty().any {
            (it as? JsonPrimitive)?.content?.substringBefore('@') in setOf("반복표", "도돌이표")
        }
    }.mapNotNull { it.string("cellId") }.toSet()
    return buildJsonObject {
        put("schema", "${score.score.instrument.lowercase().replace('_', '-')}-performance/v1")
        put("repeats", buildJsonArray {
            score.columns.filter { column -> column.cells.any { it.id in repeatedCells } }.forEach {
                add(buildJsonObject { put("start_gak", it.index + 1); put("end_gak", it.index + 1); put("times", 2) })
            }
        })
    }
}

private val SheetDraft.tempoMarks: Map<Int, HanbaeMark> get() = editingExpressions().objects("placements").mapNotNull { placement ->
    val index = sourceCellIds?.indexOf(placement.string("cellId"))?.takeIf { it >= 0 } ?: return@mapNotNull null
    val mark = (placement["symbols"] as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>()
        .firstNotNullOfOrNull { HanbaeMark.fromSymbol(it.content) } ?: return@mapNotNull null
    index to mark
}.toMap()

private val SheetDraft.tempoEntries: Map<Int, JsonObject> get() =
    (editingExpressions()["performancePlan"] as? JsonObject)?.objects("tempos").orEmpty().mapNotNull { entry ->
        (flatPosition(entry.integer("gak"), entry.integer("cell")) ?: return@mapNotNull null) to entry
    }.toMap()

private val SheetDraft.nativeBaseBpm: Double get() = nativeBpm(bpm.toDouble())
private fun sameBpm(a: Double, b: Double) = kotlin.math.abs(a - b) <= 1e-6 * maxOf(1.0, kotlin.math.abs(b))
private fun JsonObject.hasEnd() = "end_gak" in this || "end_cell" in this

/**
 * 조금 빠르게·느리게는 공유 계획에 시작 항목과 끝 다음 정간의 복귀 항목(기호 없는 즉시 변경)으로 저장한다.
 * 읽을 때 복귀 항목을 다시 한 구간으로 합친다. 복귀 항목이 없으면 다음 변경 또는 악보 끝까지를 구간으로 본다.
 */
/**
 * 정간 위치에 묶인 빠르기·반복 계획이 실제로 있는지 본다. 구간을 모두 지워 빈 performancePlan만 남았을 때
 * 정간·장단 도구를 막지 않는다(S15P31E102-1080). 반복 구간도 각 번호에 묶이므로 함께 본다.
 */
internal fun JsonObject?.hasPositionedTempoPlan(): Boolean {
    val plan = this?.get("performancePlan") as? JsonObject ?: return false
    return plan.objects("tempos").isNotEmpty() || plan.objects("repeats").isNotEmpty()
}

/** 한배 기호가 붙은 정간(연주 순서). 기호는 칸 안의 음 자리에 붙으므로 칸이 비면 둘 곳이 없다. */
internal val SheetDraft.tempoMarkCells: Set<Int> get() = tempoMarks.keys

/**
 * 장단 재시간 보정으로 정간 tick 비율이 바뀔 때 계획 숫자를 같은 기준 빠르기 대비 배율로 옮긴다(S15P31E102-1091).
 * 저장값은 `ticksPerBeat/ticksPerJeonggan` 비율에 묶여 있으므로 그 비율 변화만 곱한다. 기본 빠르기 변화는
 * 저장할 때 서버가 다시 비례 조정하므로 여기서 곱하지 않는다. 좌표·기호는 그대로 둔다.
 */
internal fun SheetDraft.retimedTempoExpressions(nextBeatTicks: Int, nextCellTicks: Int): JsonObject? {
    val expression = tempoExpressions ?: return null
    val plan = expression["performancePlan"] as? JsonObject ?: return expression
    val factor = nextBeatTicks.toDouble() / nextCellTicks / cellBpmFactor
    val tempos = plan.objects("tempos").map { entry -> JsonObject(entry.mapValues { (key, value) ->
        val number = (value as? JsonPrimitive)?.doubleOrNull
        if (key in setOf("bpm", "start_bpm") && number != null) JsonPrimitive(number * factor) else value
    }) }
    return expression.changed("performancePlan" to plan.changed("tempos" to JsonArray(tempos)))
}

/** 화면에 보이는 구간 빠르기(시작·목표)가 모두 편집 범위 안인지 본다. */
internal fun SheetDraft.tempoChangesWithinRange(): Boolean = tempoChanges.all { cue ->
    listOfNotNull(cue.targetBpm, cue.startBpm).all { it.isFinite() && it >= SheetBpmRange.first - 1e-6 && it <= SheetBpmRange.last + 1e-6 }
}

internal val SheetDraft.tempoChanges: List<SheetTempoChange> get() {
    val marks = tempoMarks
    val entries = tempoEntries
    val keys = (marks.keys + entries.keys).sorted()
    val consumed = mutableSetOf<Int>()
    var prevailing = nativeBaseBpm
    val result = mutableListOf<SheetTempoChange>()
    keys.forEachIndexed { order, index ->
        if (index in consumed) return@forEachIndexed
        val mark = marks[index]
        val entry = entries[index]
        if (entry == null) {
            result += SheetTempoChange(index, mark, bpm.toDouble().takeIf { mark == HanbaeMark.Original })
            if (mark == HanbaeMark.Original) prevailing = nativeBaseBpm
            return@forEachIndexed
        }
        val target = entry.number("bpm")
        var end = flatPosition(entry.integer("end_gak"), entry.integer("end_cell"), terminal = true)
        if (mark?.span == true && target != null && !entry.hasEnd()) {
            val next = keys.getOrNull(order + 1)
            end = next ?: cells.size
            val back = next?.let(entries::get)
            if (back != null && marks[next] == null && !back.hasEnd() && back.number("bpm")?.let { sameBpm(it, prevailing) } == true)
                consumed += next
        }
        result += SheetTempoChange(index, mark, target?.let(::displayBpm), end, entry.number("start_bpm")?.let(::displayBpm))
        if (mark?.span != true && target != null) prevailing = target
    }
    return result
}

/** 재생·길이 계산용. 조금 빠르게·느리게 구간을 시작의 즉시 변경과 끝 다음의 복귀로 펼친다(표시 BPM). */
internal val SheetDraft.playbackTempoChanges: List<SheetTempoChange> get() {
    // 복귀 항목은 끝 정간에 다른 기호(목표 미지정 포함)가 없을 때만 저장되므로 같은 기준을 쓴다.
    val starts = tempoChanges.map { it.cell }.toSet()
    val changes = tempoChanges.filter { it.resolved }
    var prevailing = bpm.toDouble()
    return changes.flatMap { cue ->
        val target = requireNotNull(cue.targetBpm)
        if (cue.mark?.span != true) { prevailing = target; return@flatMap listOf(cue) }
        val end = requireNotNull(cue.endCell)
        listOfNotNull(SheetTempoChange(cue.cell, null, target),
            SheetTempoChange(end, null, prevailing).takeIf { end < cells.size && end !in starts })
    }
}

/** 논리 변경 목록을 공유 계획의 tempos로 다시 쓴다. 손대지 않은 항목은 원래 JSON을 그대로 둔다. */
private fun SheetDraft.planTempos(changes: List<SheetTempoChange>, edited: SheetTempoChange?, previousEntry: JsonObject?): JsonArray {
    val raw = tempoEntries
    val starts = changes.map { it.cell }.toSet()
    var prevailing = nativeBaseBpm
    val out = mutableListOf<JsonObject>()
    for (cue in changes.sortedBy { it.cell }) {
        val entry = if (cue === edited) cue.targetBpm?.let { target -> buildJsonObject {
            val (gak, cell) = position(cue.cell); put("gak", gak); put("cell", cell); put("bpm", nativeBpm(target))
            if (cue.mark?.span != true) cue.endCell?.let { val (endGak, endCell) = position(it); put("end_gak", endGak); put("end_cell", endCell)
                cue.startBpm?.let { start -> put("start_bpm", nativeBpm(start)) }
                    ?: previousEntry?.get("start_bpm")?.let { start -> put("start_bpm", start) } }
        } } else raw[cue.cell] ?: cue.targetBpm?.let { target -> buildJsonObject {
            val (gak, cell) = position(cue.cell); put("gak", gak); put("cell", cell); put("bpm", nativeBpm(target))
        } }
        if (entry == null) continue
        val bpmValue = entry.number("bpm") ?: continue
        if (cue.mark?.span == true) {
            out += JsonObject(entry - setOf("end_gak", "end_cell", "start_bpm"))
            val end = cue.endCell
            if (end != null && end < cells.size && end !in starts) out += buildJsonObject {
                val (gak, cell) = position(end); put("gak", gak); put("cell", cell); put("bpm", prevailing)
            }
        } else {
            out += entry
            prevailing = bpmValue
        }
    }
    return JsonArray(out.sortedBy { flatPosition(it.integer("gak"), it.integer("cell")) })
}

internal fun SheetDraft.tempoChangeError(change: SheetTempoChange, replacing: Int? = change.cell): String? {
    if (change.cell !in cells.indices) return "실제 정간을 입력해 주세요."
    // 구간 목표 빠르기도 악보 전체 빠르기와 같은 범위만 받는다(S15P31E102-996).
    if (change.targetBpm?.let { !it.isFinite() || it < SheetBpmRange.first || it > SheetBpmRange.last } == true)
        return "목표 빠르기는 ${SheetBpmRange.first}~${SheetBpmRange.last} 사이로 입력해 주세요."
    if (change.mark == null && change.targetBpm == null) return "목표 빠르기를 입력해 주세요."
    val needsEnd = change.mark?.needsEnd ?: (change.endCell != null)
    if (needsEnd && change.targetBpm != null && (change.endCell == null || change.endCell !in (change.cell + 1)..cells.size))
        return if (change.mark?.span == true) "끝 정간은 시작 정간이거나 그 뒤로 지정해 주세요."
        else "점점 바꿀 때는 시작 뒤의 끝 위치를 지정해 주세요."
    if (!needsEnd && change.endCell != null) return "바로 바꾸는 빠르기에는 끝 위치를 지정하지 않습니다."
    if (tempoChanges.any { it.cell == change.cell && it.cell != replacing }) return "같은 정간의 빠르기는 기존 항목에서 수정해 주세요."
    return tempoSequenceError(tempoChanges.filterNot { it.cell == replacing } + change)
}

private fun SheetDraft.tempoSequenceError(changes: List<SheetTempoChange>): String? {
    var previous = bpm.toDouble()
    var finish = -1
    var spanEnd = -1
    var spanTarget = previous
    for (cue in changes.sortedBy { it.cell }) {
        if (cue.cell < finish) return "빠르기가 바뀌는 구간 안에 다른 빠르기를 넣을 수 없습니다."
        if (!cue.resolved) continue
        val target = requireNotNull(cue.targetBpm)
        // 구간이 끝나는 정간에서 바로 이어지면 복귀 항목 없이 이어지므로 실제 앞 빠르기는 그 구간의 빠르기다.
        val initial = cue.startBpm ?: if (cue.cell == spanEnd) spanTarget else previous
        if (cue.mark in setOf(HanbaeMark.Slower, HanbaeMark.GraduallySlower) && target >= initial)
            return "느리게의 목표는 앞 빠르기보다 작아야 합니다."
        if (cue.mark in setOf(HanbaeMark.Faster, HanbaeMark.GraduallyFaster) && target <= initial)
            return "빠르게의 목표는 앞 빠르기보다 커야 합니다."
        // 장단 보정의 배율 환산으로 생기는 실수 오차는 같은 빠르기로 본다(S15P31E102-1091).
        if (cue.mark == HanbaeMark.Original && !sameBpm(target, bpm.toDouble())) return "본래 속도로는 전체 빠르기로 돌아갑니다."
        finish = cue.endCell ?: cue.cell
        // 조금 빠르게·느리게는 끝 다음부터 앞 빠르기로 돌아가므로 뒤 변경의 기준을 바꾸지 않는다.
        if (cue.mark?.span == true) { spanEnd = finish; spanTarget = target } else previous = target
    }
    return null
}

internal fun SheetDraft.withTempoChange(change: SheetTempoChange, replacing: Int? = null): SheetDraft {
    if (tempoChangeError(change, replacing) != null) return this
    val score = toCommonScoreWithoutTempo()
    val index = replacing ?: change.cell
    val expression = editingExpressions()
    val placements = expression.objects("placements").mapNotNull { placement ->
        if (placement.string("cellId") != sourceCellIds?.getOrNull(index)) placement else {
            val symbols = (placement["symbols"] as? JsonArray).orEmpty().filterNot { HanbaeMark.fromSymbol((it as? JsonPrimitive)?.content.orEmpty()) != null }
            if (symbols.isEmpty()) null else placement.changed("symbols" to JsonArray(symbols))
        }
    }.toMutableList()
    if (change.mark != null) {
        val cell = score.columns.flatMap { it.cells }[change.cell]
        val unit = cell.units.firstOrNull() ?: return this
        val at = placements.indexOfFirst { it.string("cellId") == cell.id && it.integer("row") == unit.row && it.integer("col") == unit.col }
        val mark = JsonPrimitive(change.mark.symbol)
        if (at >= 0) placements[at] = placements[at].changed("symbols" to JsonArray((placements[at]["symbols"] as? JsonArray).orEmpty() + mark))
        else placements += buildJsonObject { put("eventId", unit.eventId); put("cellId", cell.id); put("row", unit.row); put("col", unit.col)
            put("symbols", JsonArray(listOf(mark))) }
    }
    val plan = expression["performancePlan"] as? JsonObject ?: initialPerformancePlan(score, expression)
    val changes = tempoChanges.filterNot { it.cell in setOf(index, change.cell) } + change
    val tempos = planTempos(changes, change, tempoEntries[index])
    return copy(tempoExpressions = expression.changed("placements" to JsonArray(placements),
        "performancePlan" to plan.changed("tempos" to tempos)),
        tempoReferenceBpm = referenceBpm)
}

internal fun SheetDraft.tempoDeletionError(cell: Int): String? = tempoSequenceError(tempoChanges.filterNot { it.cell == cell })

internal fun SheetDraft.withoutTempoChange(cell: Int): SheetDraft {
    if (tempoDeletionError(cell) != null) return this
    val expression = editingExpressions()
    val placements = expression.objects("placements").mapNotNull { placement ->
        if (placement.string("cellId") != sourceCellIds?.getOrNull(cell)) placement else {
            val symbols = (placement["symbols"] as? JsonArray).orEmpty().filterNot { HanbaeMark.fromSymbol((it as? JsonPrimitive)?.content.orEmpty()) != null }
            if (symbols.isEmpty()) null else placement.changed("symbols" to JsonArray(symbols))
        }
    }
    val plan = expression["performancePlan"] as? JsonObject ?: initialPerformancePlan(toCommonScoreWithoutTempo(), expression)
    // 지운 구간의 복귀 항목도 함께 사라지고, 남은 구간의 복귀 항목은 새 순서로 다시 계산한다.
    val tempos = planTempos(tempoChanges.filterNot { it.cell == cell }, null, null)
    return copy(tempoExpressions = expression.changed("placements" to JsonArray(placements), "performancePlan" to plan.changed(
        "tempos" to tempos)))
}

internal fun SheetDraft.tempoAnnotations(): Map<ScoreUnitKey, List<String>> = editingExpressions().objects("placements")
    .mapNotNull { placement ->
        val symbols = (placement["symbols"] as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>()
            .mapNotNull { HanbaeMark.fromSymbol(it.content)?.symbol }
        val cell = placement.string("cellId") ?: return@mapNotNull null
        if (symbols.isEmpty()) null else ScoreUnitKey(cell, placement.integer("row") ?: 0, placement.integer("col") ?: 0) to symbols
    }.sortedWith(compareBy({ sourceCellIds?.indexOf(it.first.cellId) ?: -1 }, { it.first.row }, { it.first.col })).toMap()

/** 음·길이를 보존하고 위치별 빠르기 기호만 기존 이벤트에 다시 모은다. */
internal fun SheetDraft.scoreWithTempo(source: CommonScoreData): CommonScoreData {
    if (tempoExpressions == null) return source
    val byEvent = orderedPlacements(source).groupBy { it.string("eventId") }
    return source.copy(events = source.events.map { event -> event.copy(sigimsae = byEvent[event.id].orEmpty()
        .flatMap { (it["symbols"] as? JsonArray).orEmpty() }.map { it.jsonPrimitive.content }) })
}

/** 이벤트 기호와 companion은 동일한 기보 위치 순서로 합쳐야 DATA 검증을 통과한다. */
private fun SheetDraft.orderedPlacements(score: CommonScoreData): List<JsonObject> {
    val expression = editingExpressions()
    // 정간을 합쳐 기호가 붙어 있던 음 자리가 사라지면 같은 정간의 첫 음 자리로 옮긴다(S15P31E102-1091).
    val units = score.columns.flatMap { it.cells }.associate { cell -> cell.id to cell.units.map { ScoreUnitKey(cell.id, it.row, it.col) } }
    val desiredTempo = mutableMapOf<ScoreUnitKey, List<String>>()
    tempoAnnotations().forEach { (key, symbols) ->
        val inCell = units[key.cellId].orEmpty()
        val target = key.takeIf { it in inCell } ?: inCell.firstOrNull() ?: return@forEach
        desiredTempo[target] = desiredTempo[target].orEmpty() + symbols.filterNot { it in desiredTempo[target].orEmpty() }
    }
    val existing = expression.objects("placements")
    val byId = score.events.associateBy { it.id }
    val ordinary = mutableMapOf<ScoreUnitKey, List<String>>()
    val seen = mutableSetOf<String>()
    score.columns.forEach { column -> column.cells.forEach { cell -> cell.units.forEach { unit ->
        val event = byId[unit.eventId] ?: return@forEach
        if (seen.add(event.id)) {
            val signs = event.sigimsae.filter { HanbaeMark.fromSymbol(it) == null }
            val before = existing.filter { it.string("eventId") == event.id }.mapNotNull { placement ->
                val key = ScoreUnitKey(placement.string("cellId") ?: return@mapNotNull null, placement.integer("row") ?: 0, placement.integer("col") ?: 0)
                val symbols = (placement["symbols"] as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>().map { it.content }.filter { HanbaeMark.fromSymbol(it) == null }
                key to symbols
            }
            if (before.flatMap { it.second } == signs) before.forEach { ordinary[it.first] = it.second }
            else ordinary[ScoreUnitKey(cell.id, unit.row, unit.col)] = signs
        }
    } } }
    return score.columns.flatMap { column -> column.cells.flatMap { cell -> cell.units.mapNotNull { unit ->
        val key = ScoreUnitKey(cell.id, unit.row, unit.col)
        val symbols = ordinary[key].orEmpty() + desiredTempo[key].orEmpty()
        if (symbols.isEmpty()) null else buildJsonObject { put("eventId", unit.eventId); put("cellId", cell.id); put("row", unit.row); put("col", unit.col)
            put("symbols", JsonArray(symbols.map(::JsonPrimitive))) }
    } } }
}

internal fun SheetDraft.exportExpressions(template: JsonObject?, sourceSha256: String, serverBpm: Int): JsonObject? {
    if (tempoExpressions == null) return template
    val score = toCommonScore()
    val expression = editingExpressions()
    val placements = orderedPlacements(score)
    val plan = expression["performancePlan"] as? JsonObject ?: return template
    val entries = plan.objects("tempos")
    val originals = tempoChanges.filter { cue -> cue.mark == HanbaeMark.Original && entries.none {
        flatPosition(it.integer("gak"), it.integer("cell")) == cue.cell
    } }.map { cue -> buildJsonObject {
        val (gak, cell) = position(cue.cell); put("gak", gak); put("cell", cell); put("bpm", nativeBpm(bpm.toDouble()))
    } }
    val tempos = (entries + originals).sortedBy { flatPosition(it.integer("gak"), it.integer("cell")) }.map { entry -> JsonObject(entry.mapValues { (key, value) ->
        if (key in setOf("bpm", "start_bpm")) JsonPrimitive((value as JsonPrimitive).double * serverBpm / referenceBpm) else value
    }) }
    val result = (expression + (template?.filterKeys { it !in setOf("placements", "performancePlan") }.orEmpty())).toMutableMap()
    result["schema"] = JsonPrimitive("common-score-expressions/v1")
    result["sourceSha256"] = JsonPrimitive(sourceSha256)
    result["placements"] = JsonArray(placements)
    val slots = expression["sourceSlots"] as? JsonArray
    if (slots != null) result["sourceSlots"] = JsonArray(slots.map { raw ->
        val slot = raw as? JsonObject ?: return@map raw
        val cid = slot.string("cellId")
        val row = slot.integer("row")
        val cols = (slot["cols"] as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>().mapNotNull { it.intOrNull }
        val flattened = cols.flatMap { col -> placements.filter { it.string("cellId") == cid && it.integer("row") == row && it.integer("col") == col }
            .flatMap { (it["symbols"] as? JsonArray).orEmpty() } }
        val prefix = if (slot.string("kind") == "relative") (slot["symbols"] as? JsonArray).orEmpty().take(1) else emptyList()
        slot.changed("symbols" to JsonArray(prefix + flattened))
    })
    result["performancePlan"] = plan.changed("input_sha256" to JsonPrimitive(sourceSha256), "tempos" to JsonArray(tempos))
    return JsonObject(result)
}
