package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ApiError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 음원 생성기가 거절한 시김새의 위치(공통 악보 eventId)와 기호·이유. */
data class SheetSigimsaeIssue(val eventId: String, val symbols: List<String>, val message: String?) {
    companion object {
        fun from(error: ApiError): SheetSigimsaeIssue? {
            val details = error.details ?: return null
            if (error.errorCode != "INVALID_REQUEST" || details.text("reason") != "SIGIMSAE_UNRENDERABLE") return null
            val eventId = details.text("eventId")?.takeIf { it.length <= 128 } ?: return null
            val symbols = (details["symbols"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { name -> name.isNotBlank() && name.length <= 30 } }
                .take(8)
            return SheetSigimsaeIssue(eventId, symbols, details.text("message")?.take(200))
        }

        private fun kotlinx.serialization.json.JsonObject.text(key: String): String? =
            (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
