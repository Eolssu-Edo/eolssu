package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.ModelTempoChange
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
 * 정간 위치에 묶인 빠르기·반복 계획이 실제로 있는지 본다. 구간을 모두 지워 빈 performancePlan만 남았을 때
 * 정간·장단 도구를 막지 않는다(S15P31E102-1080). 반복 구간도 각 번호에 묶이므로 함께 본다.
 */
internal fun JsonObject?.hasPositionedTempoPlan(): Boolean {
    val plan = this?.get("performancePlan") as? JsonObject ?: return false
    return plan.objects("tempos").isNotEmpty() || plan.objects("repeats").isNotEmpty()
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
        if (mark != null && cell != null && unit != null) {
            val symbol = JsonPrimitive(mark.symbol)
            val at = placements.indexOfFirst { it.string("cellId") == cell.id && it.integer("row") == unit.row && it.integer("col") == unit.col }
            if (at >= 0) placements[at] = placements[at].changed("symbols" to JsonArray((placements[at]["symbols"] as? JsonArray).orEmpty() + symbol))
            else placements += buildJsonObject { put("eventId", unit.eventId); put("cellId", cell.id); put("row", unit.row); put("col", unit.col)
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
