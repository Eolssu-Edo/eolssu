package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.BasicAccompaniment
import com.ssafy.eolssu.data.song.basicAccompaniment
import com.ssafy.eolssu.data.song.withBasicAccompaniment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val SheetDraft.basicAccompaniment: BasicAccompaniment?
    get() = original?.let { runCatching { authoring?.basicAccompaniment(toCommonScore()) }.getOrNull() }

internal val SheetDraft.canSelectBasicAccompaniment: Boolean
    get() = original?.music?.bpmUnit in setOf("QUARTER", "DOTTED_QUARTER") &&
        (authoring?.get("source") as? JsonObject)?.let { source ->
            (source["scoreSha256"] as? JsonPrimitive)?.content?.matches(Regex("[0-9a-f]{64}"))
        } == true

internal fun SheetDraft.withBasicAccompaniment(setting: BasicAccompaniment?): SheetDraft = copy(
    authoring = authoring?.let { if (setting == null && "accompaniment" !in it) it else it.withBasicAccompaniment(setting) },
)

internal val SheetDraft.accompanimentName: String
    get() = basicAccompaniment?.displayPattern?.name ?: jangdanName
