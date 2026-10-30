package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/** 서버 ID/code를 그대로 보존한다. UI enum의 순서로 DB ID를 만들지 않는다. */
@Serializable
data class CatalogInstrument(val instrumentId: String, val code: String, val name: String)

/** 활성 악기 조회 API. 가입과 회원 preferences에 서버의 실제 ID를 전달한다. */
class InstrumentCatalog(private val client: HttpClient) {
    suspend fun load(): List<CatalogInstrument> {
        val response = client.sendOrNull { get("/api/instruments") } ?: throw CatalogUnavailable()
        if (response.status.value != 200) throw CatalogUnavailable()
        val items = response.apiResponse<InstrumentList>()?.takeIf { it.isSuccess }?.result?.items ?: throw CatalogUnavailable()
        if (items.any { !validCatalogId(it.instrumentId) || it.code.isBlank() || it.name.isBlank() } ||
            items.map { it.instrumentId }.distinct().size != items.size || items.map { it.code }.distinct().size != items.size
        ) throw CatalogUnavailable()
        return items
    }

    @Serializable private class InstrumentList(val items: List<CatalogInstrument>)
}
