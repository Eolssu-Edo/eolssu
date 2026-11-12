package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ApiError
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Renderer-provided bounds apply to this score and its beat unit. */
data class SheetTempoIssue(val reason: String?, val minimum: Int?, val maximum: Int?) {
    fun guidance(): String = when (reason) {
        "PLAN_POSITIONS_CHANGED" -> "구간 빠르기를 유지하려면 칸 배치 변경을 되돌려 주세요."
        "DURATION_LIMIT" -> "10분을 넘어요. 빠르기를 ${minimum ?: 1} 이상으로 높여 주세요."
        "PCM_NOTE_TOO_SHORT" -> "너무 짧은 음이 있어요. 빠르기를 ${maximum ?: 1} 이하로 낮춰 주세요."
        "MIDI_NOTE_TOO_SHORT" -> "구간 빠르기가 너무 빨라요. 해당 구간을 수정해 다시 가져와 주세요."
        else -> "이 빠르기는 사용할 수 없어요. ${minimum ?: 1}~${maximum ?: Int.MAX_VALUE}에서 조절해 주세요."
    }

    companion object {
        fun from(error: ApiError) = SheetTempoIssue(
            error.details?.get("reason")?.jsonPrimitive?.content,
            error.details?.get("minimumBpm")?.jsonPrimitive?.intOrNull,
            error.details?.get("maximumBpm")?.jsonPrimitive?.intOrNull,
        )
    }
}
