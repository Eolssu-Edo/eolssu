package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** SHT-01·02. 성공한 서버 응답만 게시하며 예시 곡에 연결하지 않는다. */
class ApiMySheetRepository(private val client: HttpClient, private val member: SignedInMember? = null) : MySheetRepository {
    private val state = MutableStateFlow<List<MySheet>>(emptyList())
    override val sheets = state.asStateFlow()
    private val lock = Mutex()
    init { member?.onChange { state.value = emptyList() } }

    override suspend fun refresh(): Boolean = member.inSession {
        lock.withLock {
            try {
                val all = mutableListOf<MySheet>()
                var page = 0
                do {
                    val response = client.sendOrNull { get("/api/users/me/sheets") {
                        parameter("kind", "JEONGGANBO"); parameter("page", page); parameter("size", 100)
                    } } ?: return@withLock false
                    if (response.status.value != 200) return@withLock false
                    val result = response.apiResponse<SheetPage>()?.takeIf { it.isSuccess }?.result ?: return@withLock false
                    if (result.page != page || result.totalPages < 0 || (page > 0 && result.items.isEmpty())) return@withLock false
                    all += result.items.map { it.model() }
                    page++
                } while (page < result.totalPages)
                if (all.map { it.id }.distinct().size != all.size) return@withLock false
                ensureAccountSession()
                state.value = all
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        }
    }

    override suspend fun delete(sheetId: String) = member.inSession {
        require(validCatalogId(sheetId))
        lock.withLock {
            val response = client.sendOrNull { delete("/api/users/me/sheets/$sheetId") }
            // 404도 이미 지워졌음을 확인한 응답이다. 서버 오류일 때 목록에서 지우지 않는다.
            if (response?.status?.value !in listOf(204, 404)) throw CatalogUnavailable()
            ensureAccountSession()
            state.update { items -> items.filterNot { it.id == sheetId } }
        }
    }

    @Serializable private class SheetPage(val items: List<SheetItem>, val page: Int, val totalPages: Int)
    @Serializable private class SheetItem(
        val sheetId: String, val kind: String, val title: String, val status: String,
        val source: String, val updatedAt: String, val jangdanName: String? = null,
        val conversionStatus: String? = null, val hasDraft: Boolean = false,
    ) {
        fun model(): MySheet {
            require(validCatalogId(sheetId) && kind == "JEONGGANBO" && title.isNotBlank())
            return MySheet(sheetId, MySheetOrigin.Imported, title, jangdanName = jangdanName,
                updatedAt = KoreanDateTime.fromEpochMillis(Instant.parse(updatedAt).toEpochMilliseconds()),
                status = status, conversionStatus = conversionStatus, hasDraft = hasDraft)
        }
    }
}
