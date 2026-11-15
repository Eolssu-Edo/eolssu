package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.BasicAccompaniment
import com.ssafy.eolssu.core.model.BasicPulseUnit
import kotlinx.serialization.json.*

/** 저장 계약의 경계에서만 JSON을 해석한다. 알 수 없는 설정을 다른 박자로 대체하지 않는다. */
internal fun JsonObject.basicAccompaniment(score: CommonScoreData): BasicAccompaniment? {
    val value = get("accompaniment")?.takeUnless { it == JsonNull } ?: return null
    val setting = value as? JsonObject ?: throw IllegalArgumentException("반주 설정 형식")
    require(setting.keys == setOf("schema", "groups", "pulseUnit", "startTick"))
    require((setting["schema"] as? JsonPrimitive)?.content == "basic-accompaniment/v1")
    require(score.music.bpmUnit in setOf("QUARTER", "DOTTED_QUARTER"))
    val pulse = BasicPulseUnit.entries.firstOrNull { it.name == (setting["pulseUnit"] as? JsonPrimitive)?.content }
        ?: throw IllegalArgumentException("반주 박 음가")
    val groups = (setting["groups"] as? JsonArray)?.map {
        val number = it as? JsonPrimitive ?: throw IllegalArgumentException("반주 묶음")
        require(!number.isString)
        number.intOrNull ?: throw IllegalArgumentException("반주 묶음")
    } ?: throw IllegalArgumentException("반주 묶음")
    val rawStart = setting["startTick"] as? JsonPrimitive ?: throw IllegalArgumentException("첫 강박 위치")
    require(!rawStart.isString)
    val start = rawStart.longOrNull ?: throw IllegalArgumentException("첫 강박 위치")
    val total = score.columns.sumOf { it.cells.size.toLong() } * score.music.ticksPerJeonggan
    require(start in 0 until total)
    return BasicAccompaniment(pulse, groups, start)
}

internal fun JsonObject.withBasicAccompaniment(setting: BasicAccompaniment?): JsonObject = JsonObject(this + (
    "accompaniment" to (setting?.let { buildJsonObject {
        put("schema", "basic-accompaniment/v1")
        put("groups", JsonArray(it.groups.map(::JsonPrimitive)))
        put("pulseUnit", it.pulseUnit.name)
        put("startTick", it.startTick)
    } } ?: JsonNull)
))
