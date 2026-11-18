package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.ModelTempoChange
import com.ssafy.eolssu.data.song.commonSourceSha256
import kotlinx.serialization.json.*

/** 빠르기가 바뀌는 방식. 정간보 한배 기호 하나가 바뀌는 곳 하나다(S15P31E102-1089). */
enum class SheetTempoKind { Step, Ramp }

/**
 * 빠르기가 바뀌는 곳. 정간 번호는 0부터의 연주 순서다.
 * - [SheetTempoKind.Step]: [start]부터 [bpm]으로 바로 바꾸고 다음 변경까지 유지한다(ㅈㅅ/ㅈㄴ, 기본 빠르기로 돌아가면 ㅂㅅ).
 * - [SheetTempoKind.Ramp]: 바로 앞 빠르기에서 [start]..[end](포함) 동안 [bpm]까지 점점 바꾸고, 그 뒤로 유지한다(ㅈㅈㅅ/ㅈㅈㄴ).
 * 시작 빠르기는 받지 않는다. 숫자가 없는 인식 기호는 [bpm]이 비어 있고 앞 빠르기를 유지한다. [mark]는 읽어 온 기호다.
 */
data class SheetTempoCue(val start: Int, val kind: SheetTempoKind, val bpm: Double? = null, val end: Int? = null,
    val mark: HanbaeMark? = null) {
    val resolved: Boolean get() = bpm != null && (kind != SheetTempoKind.Ramp || end != null)
    /** 이 변경이 차지하는 마지막 정간. 점점이 아니면 시작 정간 하나다. */
    val last: Int get() = if (kind == SheetTempoKind.Ramp) end ?: start else start
}

/** 앞 빠르기와 목표를 비교해 붙일 기호. 같으면 바뀌는 것이 없으므로 null, 기본 빠르기로 바로 돌아가면 본래 속도로다. */
internal fun SheetTempoCue.markFrom(before: Double, base: Double): HanbaeMark? {
    val target = bpm ?: return mark
    return when {
        sameBpm(target, before) -> null
        kind == SheetTempoKind.Step && sameBpm(target, base) -> HanbaeMark.Original
        kind == SheetTempoKind.Ramp && target > before -> HanbaeMark.GraduallyFaster
        kind == SheetTempoKind.Ramp && target < before -> HanbaeMark.GraduallySlower
        kind == SheetTempoKind.Step && target > before -> HanbaeMark.Faster
        kind == SheetTempoKind.Step && target < before -> HanbaeMark.Slower
        else -> null
    }
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

/**
 * 편집 중 companion의 칸·이벤트 ID는 불러온 악보(original) 기준이다. 각을 넣거나 지우면 내보낼 악보의 칸 ID가
 * `c{각}-j{정간}`으로 다시 매겨지므로 같은 정간 순서의 새 ID로 옮긴다(S15P31E102-1095). 지운 칸은 대응이 없다.
 */
private class CompanionIds(private val cells: Map<String, String>?, private val events: Map<String, String>?) {
    fun cell(id: String?): String? = id?.let { if (cells == null) it else cells[it] }
    fun event(id: String?): String? = id?.let { if (events == null) it else events[it] }
}

private fun SheetDraft.companionIds(score: CommonScoreData): CompanionIds {
    val ids = sourceCellIds ?: return CompanionIds(null, null)
    val next = score.columns.flatMap { it.cells }
    val old = original?.columns?.flatMap { it.cells }.orEmpty().associateBy { it.id }
    val cells = mutableMapOf<String, String>()
    val events = mutableMapOf<String, String>()
    ids.forEachIndexed { index, id ->
        val cell = next.getOrNull(index) ?: return@forEachIndexed
        if (id == null) return@forEachIndexed
        cells[id] = cell.id
        val units = cell.units.associateBy { it.row to it.col }
        old[id]?.units?.forEach { unit ->
            val moved = units[unit.row to unit.col]?.eventId
            if (unit.eventId.isNotEmpty() && !moved.isNullOrEmpty()) events.getOrPut(unit.eventId) { moved }
        }
    }
    return CompanionIds(cells, events)
}

private fun JsonObject.shifted(key: String, shift: (Int) -> Int): JsonObject =
    integer(key)?.let { changed(key to JsonPrimitive(shift(it))) } ?: this

/**
 * 각을 지우면 막을지 본다. 빠르기 변경(기호만 있는 것 포함)이 시작하는 각, 점점 구간이
 * 걸친 각, 반복 범위의 각은 지울 수 없다(S15P31E102-1095, 사용자 결정). 바로 바뀐 빠르기가 이어지기만 하는 각은 지운다.
 */
internal fun SheetDraft.tempoColumnDeletionBlocked(column: Int): Boolean {
    val expression = tempoExpressions ?: return false
    if (column !in 0 until columnCount) return false
    val from = indexOf(column, 0)
    val to = from + sizes[column]
    if (tempoCues.any { cue -> cue.start < to && cue.last >= from }) return true
    val plan = expression["performancePlan"] as? JsonObject ?: return false
    return plan.objects("repeats").any { (it.integer("start_gak") ?: 0) <= column + 1 && column + 1 <= (it.integer("end_gak") ?: 0) }
}

private fun JsonObject.withPlan(transform: (JsonObject) -> JsonObject, continuations: (List<JsonObject>) -> List<JsonObject>): JsonObject {
    val result = toMutableMap()
    (this["performancePlan"] as? JsonObject)?.let { result["performancePlan"] = transform(it) }
    (this["gakContinuations"] as? JsonArray)?.let { result["gakContinuations"] = JsonArray(continuations(it.filterIsInstance<JsonObject>())) }
    return JsonObject(result)
}

private fun JsonObject.mapped(key: String, transform: (List<JsonObject>) -> List<JsonObject>): JsonObject =
    if (key in this) changed(key to JsonArray(transform(objects(key)))) else this

/**
 * [column](0부터) 뒤에 각을 넣으면 그 뒤 위치를 한 각씩 민다. 새 각은 앞 각 끝의 빠르기를 잇는다.
 * 구간 끝이 바로 다음 각 시작이면 새 각도 그 구간에 든다. 반복은 끝 각을 포함하므로 끝 각 바로 뒤의 새 각은 반복 밖이다.
 */
internal fun SheetDraft.tempoExpressionsAfterColumnInsert(column: Int): JsonObject? = tempoExpressionsWithColumnAt(column + 1)

/** [column](0부터) 자리에 각이 들어오면 그 자리부터 뒤 위치를 한 각씩 민다. */
internal fun SheetDraft.tempoExpressionsWithColumnAt(column: Int): JsonObject? {
    val expression = tempoExpressions ?: return null
    val first = column + 1
    fun shift(gak: Int) = if (gak >= first) gak + 1 else gak
    return expression.withPlan({ plan ->
        plan.mapped("tempos") { it.map { e -> e.shifted("gak", ::shift).shifted("end_gak", ::shift) } }
            .mapped("repeats") { it.map { r -> r.shifted("start_gak", ::shift).shifted("end_gak", ::shift) } }
    }) { entries -> entries.filter { it.integer("gak") != first }.map { it.shifted("gak", ::shift) } }
}

/**
 * [column](0부터) 각을 지우면 뒤 위치를 한 각씩 당긴다. 막는 경우([tempoColumnDeletionBlocked])는 미리 거른다.
 * 지운 각 첫 정간의 복귀 항목은 다음 각 시작으로 남고, 그 자리에 다른 변경이 오거나 악보 끝이 되면 버린다.
 */
internal fun SheetDraft.tempoExpressionsAfterColumnDelete(column: Int): JsonObject? {
    val expression = tempoExpressions ?: return null
    val deleted = column + 1
    val last = columnCount
    fun shift(gak: Int) = if (gak > deleted) gak - 1 else gak
    return expression.withPlan({ plan ->
        plan.mapped("tempos") { entries ->
            val moved = entries.map { e -> e.shifted("gak", ::shift).shifted("end_gak", ::shift) }
            moved.filterIndexed { i, e ->
                if (entries[i].integer("gak") != deleted) true
                else deleted != last && moved.withIndex().none { (j, o) -> j != i && entries[j].integer("gak") != deleted &&
                    o.integer("gak") == e.integer("gak") && o.integer("cell") == e.integer("cell") }
            }
        }.mapped("repeats") { it.map { r -> r.shifted("start_gak", ::shift).shifted("end_gak", ::shift) } }
    }) { entries -> entries.filter { it.integer("gak") !in setOf(deleted, deleted + 1) }.map { it.shifted("gak", ::shift) } }
}

/** DATA common_preview의 교육용 자동 반복과 같은 각 단위 2회 재생을 명시화한다. */
private fun SheetDraft.initialPerformancePlan(score: CommonScoreData, expression: JsonObject): JsonObject {
    val ids = companionIds(score)
    val repeatedCells = expression.objects("placements").filter { placement ->
        (placement["symbols"] as? JsonArray).orEmpty().any {
            (it as? JsonPrimitive)?.content?.substringBefore('@') in setOf("반복표", "도돌이표")
        }
    }.mapNotNull { ids.cell(it.string("cellId")) }.toSet()
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

/** 화면에 보이는 바뀌는 곳의 빠르기가 모두 편집 범위 안인지 본다. */
internal fun SheetDraft.tempoCuesWithinRange(): Boolean = tempoCues.all { cue ->
    listOfNotNull(cue.bpm).all { it.isFinite() && it >= SheetBpmRange.first - 1e-6 && it <= SheetBpmRange.last + 1e-6 }
}

private fun HanbaeMark.kind(): SheetTempoKind = if (gradual) SheetTempoKind.Ramp else SheetTempoKind.Step

/**
 * 공유 계획(tempos)과 기호(placements)를 바뀌는 곳 목록으로 읽는다. 숫자 없는 본래 속도로 기호는 기본 빠르기로 바로 바꾸는 곳이다.
 * 시작 빠르기(start_bpm)가 앞 빠르기와 다른 예전·가져온 점점 구간은 '바로'와 '점점' 두 곳으로 나눠 기호 하나씩 보인다.
 */
internal val SheetDraft.tempoCues: List<SheetTempoCue> get() {
    val marks = tempoMarks
    val entries = tempoEntries
    val keys = (marks.keys + entries.keys).sorted()
    val base = bpm.toDouble()
    var before = base
    val result = mutableListOf<SheetTempoCue>()
    for (index in keys) {
        if (result.any { index in it.start..it.last }) continue
        val mark = marks[index]
        val entry = entries[index]
        val target = entry?.number("bpm")?.let(::displayBpm)
        if (target == null) {
            if (mark == HanbaeMark.Original) { if (!sameBpm(before, base)) { result += SheetTempoCue(index, SheetTempoKind.Step, base, mark = mark); before = base } }
            else if (mark != null) result += SheetTempoCue(index, mark.kind(), mark = mark)
            continue
        }
        val until = if (entry.hasEnd()) flatPosition(entry.integer("end_gak"), entry.integer("end_cell"), terminal = true) else null
        if (until != null && until > index) {
            val from = entry.number("start_bpm")?.let(::displayBpm)
            if (from != null && !sameBpm(from, before)) {
                // 시작값으로 뛰어오르는 곳은 첫 정간에서 바로 바꾸고, 나머지 정간에서 점점 바꾼다.
                result += SheetTempoCue(index, SheetTempoKind.Step, from)
                before = from
                if (until - 1 > index && !sameBpm(target, from)) {
                    result += SheetTempoCue(index + 1, SheetTempoKind.Ramp, target, until - 1)
                    before = target
                }
            } else if (!sameBpm(target, before)) {
                result += SheetTempoCue(index, SheetTempoKind.Ramp, target, until - 1, mark)
                before = target
            }
            continue
        }
        if (sameBpm(target, before)) continue
        result += SheetTempoCue(index, SheetTempoKind.Step, target, mark = mark)
        before = target
    }
    return result.sortedBy { it.start }
}

/** [cues] 순서대로 각 변경 직전의 빠르기(표시 BPM). 숫자가 없는 기호는 앞 빠르기를 유지한다. */
internal fun SheetDraft.tempoBefore(cues: List<SheetTempoCue>): List<Double> {
    var current = bpm.toDouble()
    return cues.sortedBy { it.start }.map { cue ->
        val before = current
        if (cue.resolved) current = requireNotNull(cue.bpm)
        before
    }
}

/** 재생·길이 계산용 즉시/선형 변경(표시 BPM). */
internal val SheetDraft.playbackTempoChanges: List<ModelTempoChange> get() {
    val cues = tempoCues
    return cues.zip(tempoBefore(cues)).filter { it.first.resolved }.map { (cue, before) ->
        when (cue.kind) {
            SheetTempoKind.Step -> ModelTempoChange(cue.start, requireNotNull(cue.bpm))
            SheetTempoKind.Ramp -> ModelTempoChange(cue.start, requireNotNull(cue.bpm), cue.last + 1, before)
        }
    }
}

private fun SheetDraft.withoutTempoMarks(placements: List<JsonObject>) = placements.mapNotNull { placement ->
    val symbols = (placement["symbols"] as? JsonArray).orEmpty()
        .filterNot { HanbaeMark.fromSymbol((it as? JsonPrimitive)?.content.orEmpty()) != null }
    when {
        symbols.size == (placement["symbols"] as? JsonArray).orEmpty().size -> placement
        symbols.isEmpty() -> null
        else -> placement.changed("symbols" to JsonArray(symbols))
    }
}

/** 바뀌는 곳 목록 전체를 기호(placements)와 공유 계획(tempos)으로 다시 쓴다. 다른 기호·반복 계획은 그대로 둔다. */
internal fun SheetDraft.withTempoCues(cues: List<SheetTempoCue>): SheetDraft {
    val score = toCommonScoreWithoutTempo()
    val expression = editingExpressions()
    val placements = withoutTempoMarks(expression.objects("placements")).toMutableList()
    val flatCells = score.columns.flatMap { it.cells }
    val sorted = cues.sortedBy { it.start }
    val base = bpm.toDouble()
    val tempos = mutableListOf<JsonObject>()
    for ((cue, before) in sorted.zip(tempoBefore(sorted))) {
        val mark = if (cue.resolved) cue.markFrom(before, base) else cue.mark
        val cell = flatCells.getOrNull(cue.start)
        val unit = cell?.units?.firstOrNull()
        // companion은 불러온 악보의 칸·이벤트 ID를 쓰고 저장 때 새 ID로 옮긴다(S15P31E102-1095).
        // 저장 전 새로 넣은 각에는 대응 ID가 없어 숫자만 두고, 기호는 저장 뒤 다시 편집할 때 붙는다.
        val cellId = if (sourceCellIds == null) cell?.id else sourceCellIds.getOrNull(cue.start)
        if (mark != null && cell != null && unit != null && cellId != null) {
            val eventId = original?.columns?.flatMap { it.cells }?.firstOrNull { it.id == cellId }?.units
                ?.firstOrNull { it.row == unit.row && it.col == unit.col }?.eventId ?: unit.eventId
            val symbol = JsonPrimitive(mark.symbol)
            val at = placements.indexOfFirst { it.string("cellId") == cellId && it.integer("row") == unit.row && it.integer("col") == unit.col }
            if (at >= 0) placements[at] = placements[at].changed("symbols" to JsonArray((placements[at]["symbols"] as? JsonArray).orEmpty() + symbol))
            else placements += buildJsonObject { put("eventId", eventId); put("cellId", cellId); put("row", unit.row); put("col", unit.col)
                put("symbols", JsonArray(listOf(symbol))) }
        }
        if (!cue.resolved) continue
        val (gak, cellNo) = position(cue.start)
        tempos += buildJsonObject {
            put("gak", gak); put("cell", cellNo)
            put("bpm", nativeBpm(requireNotNull(cue.bpm)))
            if (cue.kind == SheetTempoKind.Ramp) {
                val (endGak, endCell) = position(cue.last + 1)
                put("end_gak", endGak); put("end_cell", endCell)
            }
        }
    }
    val plan = expression["performancePlan"] as? JsonObject ?: initialPerformancePlan(score, expression)
    return copy(tempoExpressions = expression.changed("placements" to JsonArray(placements),
        "performancePlan" to plan.changed("tempos" to JsonArray(tempos))),
        tempoReferenceBpm = referenceBpm)
}

private fun SheetDraft.positionText(index: Int) = "${columnOf(index) + 1}각 ${rowOf(index) + 1}정간"

/** 바뀌는 곳 목록 전체의 문제. 같은 빠르기로 바꾸는 곳과 점점 구간 안의 변경을 막는다. */
private fun SheetDraft.tempoSequenceError(cues: List<SheetTempoCue>): String? {
    val sorted = cues.sortedBy { it.start }
    sorted.zipWithNext().firstOrNull { (a, b) -> b.start <= a.last }?.let { (a, _) ->
        return if (a.kind == SheetTempoKind.Ramp) "점점 바뀌는 구간(${positionText(a.start)}~${positionText(a.last)}) 안에는 넣을 수 없어요."
            else "${positionText(a.start)}에는 이미 바뀌는 곳이 있어요."
    }
    for ((cue, before) in sorted.zip(tempoBefore(sorted))) {
        if (!cue.resolved) continue
        if (sameBpm(requireNotNull(cue.bpm), before)) return "${positionText(cue.start)}: 앞 빠르기(${before.roundToBpm()})와 같아요."
    }
    return null
}

private fun Double.roundToBpm(): String = if (this == kotlin.math.round(this)) toLong().toString() else ((this * 10).toLong() / 10.0).toString()

/** 넣거나 고칠 변경의 문제. [replacing]은 고치는 기존 변경의 시작 정간이다. */
internal fun SheetDraft.tempoCueError(cue: SheetTempoCue, replacing: Int? = null): String? {
    if (cue.start !in cells.indices) return "악보 안의 정간을 골라 주세요."
    if (cue.kind == SheetTempoKind.Ramp) {
        val end = cue.end ?: return "끝 정간을 골라 주세요."
        if (end !in cells.indices) return "악보 안의 정간을 골라 주세요."
        if (end < cue.start) return "끝 정간은 시작 정간과 같거나 그 뒤여야 해요."
    }
    val target = cue.bpm
    if (target == null || !target.isFinite() || target < SheetBpmRange.first || target > SheetBpmRange.last)
        return "빠르기는 ${SheetBpmRange.first}~${SheetBpmRange.last} 사이로 정해 주세요."
    return tempoSequenceError(tempoCues.filterNot { it.start == replacing } + cue)
}

internal fun SheetDraft.withTempoCue(cue: SheetTempoCue, replacing: Int? = null): SheetDraft {
    if (tempoCueError(cue, replacing) != null) return this
    return withTempoCues(tempoCues.filterNot { it.start == replacing } + cue)
}

/** 지우면 뒤의 변경이 앞 빠르기와 같아지는 경우를 알린다. */
internal fun SheetDraft.tempoCueDeletionError(start: Int): String? = tempoSequenceError(tempoCues.filterNot { it.start == start })
    ?.let { "지우면 $it 뒤의 변경부터 고쳐 주세요." }

internal fun SheetDraft.withoutTempoCue(start: Int): SheetDraft =
    if (tempoCueDeletionError(start) != null) this else withTempoCues(tempoCues.filterNot { it.start == start })

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
    val ids = companionIds(score)
    // 정간을 합쳐 기호가 붙어 있던 음 자리가 사라지면 같은 정간의 첫 음 자리로 옮긴다(S15P31E102-1091).
    val units = score.columns.flatMap { it.cells }.associate { cell -> cell.id to cell.units.map { ScoreUnitKey(cell.id, it.row, it.col) } }
    val desiredTempo = mutableMapOf<ScoreUnitKey, List<String>>()
    tempoAnnotations().forEach { (old, symbols) ->
        val cellId = ids.cell(old.cellId) ?: return@forEach
        val key = old.copy(cellId = cellId)
        val inCell = units[cellId].orEmpty()
        val target = key.takeIf { it in inCell } ?: inCell.firstOrNull() ?: return@forEach
        desiredTempo[target] = desiredTempo[target].orEmpty() + symbols.filterNot { it in desiredTempo[target].orEmpty() }
    }
    val existing = expression.objects("placements").mapNotNull { placement ->
        val cellId = ids.cell(placement.string("cellId")) ?: return@mapNotNull null
        placement.changed("cellId" to JsonPrimitive(cellId),
            "eventId" to JsonPrimitive(ids.event(placement.string("eventId")) ?: ""))
    }
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

/**
 * 편집한 companion을 보낼 악보에 결합한다. 각을 넣거나 지워도 좌표가 이 악보를 가리키도록 결합 해시는 보낼 악보의
 * 정규화 SHA다. 서버는 이 결합이면 위치 동일성 대신 좌표 유효성을 검사한다(S15P31E102-1095).
 */
internal fun SheetDraft.exportExpressions(template: JsonObject?, serverBpm: Int): JsonObject? {
    if (tempoExpressions == null) return template
    val score = toCommonScore()
    val sourceSha256 = commonSourceSha256(Json.encodeToJsonElement(score))
    val ids = companionIds(score)
    val expression = editingExpressions()
    val placements = orderedPlacements(score)
    val plan = expression["performancePlan"] as? JsonObject ?: return template
    val entries = plan.objects("tempos")
    val originals = tempoMarks.filter { (index, mark) -> mark == HanbaeMark.Original && entries.none {
        flatPosition(it.integer("gak"), it.integer("cell")) == index
    } }.keys.sorted().map { index -> buildJsonObject {
        val (gak, cell) = position(index); put("gak", gak); put("cell", cell); put("bpm", nativeBpm(bpm.toDouble()))
    } }
    val tempos = (entries + originals).sortedBy { flatPosition(it.integer("gak"), it.integer("cell")) }.map { entry -> JsonObject(entry.mapValues { (key, value) ->
        if (key in setOf("bpm", "start_bpm")) JsonPrimitive((value as JsonPrimitive).double * serverBpm / referenceBpm) else value
    }) }
    val result = (expression + (template?.filterKeys { it !in setOf("placements", "performancePlan") }.orEmpty())).toMutableMap()
    result["schema"] = JsonPrimitive("common-score-expressions/v1")
    result["sourceSha256"] = JsonPrimitive(sourceSha256)
    result["placements"] = JsonArray(placements)
    val slots = expression["sourceSlots"] as? JsonArray
    if (slots != null) result["sourceSlots"] = JsonArray(slots.mapNotNull { raw ->
        val original = raw as? JsonObject ?: return@mapNotNull raw
        val slot = original.changed("cellId" to JsonPrimitive(ids.cell(original.string("cellId")) ?: return@mapNotNull null))
        val cid = slot.string("cellId")
        val row = slot.integer("row")
        val cols = (slot["cols"] as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>().mapNotNull { it.intOrNull }
        val flattened = cols.flatMap { col -> placements.filter { it.string("cellId") == cid && it.integer("row") == row && it.integer("col") == col }
            .flatMap { (it["symbols"] as? JsonArray).orEmpty() } }
        val prefix = if (slot.string("kind") == "relative") (slot["symbols"] as? JsonArray).orEmpty().take(1) else emptyList()
        slot.changed("symbols" to JsonArray(prefix + flattened))
    })
    (expression["slurGroups"] as? JsonArray)?.let { groups -> result["slurGroups"] = JsonArray(groups.mapNotNull { raw ->
        val group = raw as? JsonObject ?: return@mapNotNull raw
        val events = (group["eventIds"] as? JsonArray).orEmpty().map { ids.event((it as? JsonPrimitive)?.contentOrNull) ?: return@mapNotNull null }
        if (events.toSet().size != events.size) null else group.changed("eventIds" to JsonArray(events.map(::JsonPrimitive)))
    }) }
    result["performancePlan"] = plan.changed("input_sha256" to JsonPrimitive(sourceSha256), "tempos" to JsonArray(tempos))
    return JsonObject(result)
}
