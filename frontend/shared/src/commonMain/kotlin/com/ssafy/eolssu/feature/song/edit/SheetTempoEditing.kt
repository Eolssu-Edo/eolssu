package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.CommonScoreData
import kotlinx.serialization.json.*

/** 정간 번호는 0부터의 연주 순서, 끝은 변화가 끝나는 정간 시작(악보 끝도 허용)이다. */
data class SheetTempoChange(val cell: Int, val mark: HanbaeMark?, val targetBpm: Double?, val endCell: Int? = null,
    val startBpm: Double? = null) {
    val resolved: Boolean get() = targetBpm != null && (mark?.gradual != true || endCell != null)
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

internal val SheetDraft.tempoChanges: List<SheetTempoChange> get() {
    val expression = editingExpressions()
    val marks = expression.objects("placements").mapNotNull { placement ->
        val index = sourceCellIds?.indexOf(placement.string("cellId"))?.takeIf { it >= 0 } ?: return@mapNotNull null
        val mark = (placement["symbols"] as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>()
            .firstNotNullOfOrNull { HanbaeMark.fromSymbol(it.content) } ?: return@mapNotNull null
        index to mark
    }.toMap()
    val plan = (expression["performancePlan"] as? JsonObject)?.objects("tempos").orEmpty().mapNotNull { entry ->
        val index = flatPosition(entry.integer("gak"), entry.integer("cell")) ?: return@mapNotNull null
        index to SheetTempoChange(index, marks[index], entry.number("bpm")?.let(::displayBpm),
            flatPosition(entry.integer("end_gak"), entry.integer("end_cell"), terminal = true),
            entry.number("start_bpm")?.let(::displayBpm))
    }.toMap()
    return (marks.keys + plan.keys).sorted().map { index -> plan[index] ?: SheetTempoChange(index, marks[index],
        bpm.toDouble().takeIf { marks[index] == HanbaeMark.Original }) }
}

internal fun SheetDraft.tempoChangeError(change: SheetTempoChange, replacing: Int? = change.cell): String? {
    if (change.cell !in cells.indices || change.targetBpm?.let { !it.isFinite() || it <= 0 || it > Int.MAX_VALUE } == true)
        return "실제 정간과 양수 빠르기를 입력해 주세요."
    if (change.mark == null && change.targetBpm == null) return "목표 빠르기를 입력해 주세요."
    val gradual = change.mark?.gradual ?: (change.endCell != null)
    if (gradual && change.targetBpm != null && (change.endCell == null || change.endCell !in (change.cell + 1)..cells.size))
        return "점점 바꿀 때는 시작 뒤의 끝 위치를 지정해 주세요."
    if (!gradual && change.endCell != null) return "바로 바꾸는 빠르기에는 끝 위치를 지정하지 않습니다."
    if (tempoChanges.any { it.cell == change.cell && it.cell != replacing }) return "같은 정간의 빠르기는 기존 항목에서 수정해 주세요."
    return tempoSequenceError(tempoChanges.filterNot { it.cell == replacing } + change)
}

private fun SheetDraft.tempoSequenceError(changes: List<SheetTempoChange>): String? {
    var previous = bpm.toDouble()
    var finish = -1
    for (cue in changes.sortedBy { it.cell }) {
        if (cue.cell < finish) return "점점 바뀌는 구간 안에 다른 빠르기를 넣을 수 없습니다."
        if (!cue.resolved) continue
        val target = requireNotNull(cue.targetBpm)
        val initial = cue.startBpm ?: previous
        if (cue.mark in setOf(HanbaeMark.Slower, HanbaeMark.GraduallySlower) && target >= initial)
            return "느리게의 목표는 앞 빠르기보다 작아야 합니다."
        if (cue.mark in setOf(HanbaeMark.Faster, HanbaeMark.GraduallyFaster) && target <= initial)
            return "빠르게의 목표는 앞 빠르기보다 커야 합니다."
        if (cue.mark == HanbaeMark.Original && target != bpm.toDouble()) return "본래 속도로는 전체 빠르기로 돌아갑니다."
        finish = cue.endCell ?: cue.cell
        previous = target
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
    val entries = plan.objects("tempos").filterNot {
        flatPosition(it.integer("gak"), it.integer("cell")) in setOf(index, change.cell)
    }.toMutableList()
    if (change.targetBpm != null) {
        val old = plan.objects("tempos").firstOrNull { flatPosition(it.integer("gak"), it.integer("cell")) == index }
        entries += buildJsonObject {
            val (gak, cell) = position(change.cell); put("gak", gak); put("cell", cell); put("bpm", nativeBpm(change.targetBpm))
            change.endCell?.let { val (endGak, endCell) = position(it); put("end_gak", endGak); put("end_cell", endCell)
                change.startBpm?.let { start -> put("start_bpm", nativeBpm(start)) }
                    ?: old?.get("start_bpm")?.let { start -> put("start_bpm", start) } }
        }
    }
    return copy(tempoExpressions = expression.changed("placements" to JsonArray(placements),
        "performancePlan" to plan.changed("tempos" to JsonArray(entries.sortedBy { flatPosition(it.integer("gak"), it.integer("cell")) }))),
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
    return copy(tempoExpressions = expression.changed("placements" to JsonArray(placements), "performancePlan" to plan.changed(
        "tempos" to JsonArray(plan.objects("tempos").filterNot { flatPosition(it.integer("gak"), it.integer("cell")) == cell }))))
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
    val desiredTempo = tempoAnnotations()
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
