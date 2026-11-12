package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** 연습 기록지 날짜별 통계(GRO-02)를 앱 공통 HTTP 클라이언트로 조회한다. 날짜는 `YYYY-MM-DD` KST 날짜로 보낸다. */
class HttpGrowthRepository(private val client: HttpClient, private val member: SignedInMember? = null) : GrowthRepository {
    override suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>? = member.inSession {
        val response = client.sendOrNull {
            get("/api/users/me/growth/calendar") {
                parameter("from", isoDate(fromDay))
                parameter("to", isoDate(toDay))
            }
        } ?: return@inSession null
        if (!response.status.isSuccess()) return@inSession null
        response.apiResponse<CalendarDto>()?.result?.items?.mapNotNull { it.toModel() }
    }

    @Serializable
    private class CalendarDto(val items: List<CalendarItemDto> = emptyList())

    @Serializable
    private class CalendarItemDto(val date: String, val practiceSeconds: Long, val practiceCount: Long) {
        fun toModel(): DailyPractice? = epochDayOf(date)?.let { DailyPractice(it, practiceSeconds, practiceCount) }
    }
}

private fun isoDate(epochDay: Long): String = KoreanDateTime.ofEpochDay(epochDay).run {
    "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

/** `YYYY-MM-DD` → 날 수. 형식이 아니면 null. */
private fun epochDayOf(date: String): Long? {
    val parts = date.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return KoreanDateTime(parts[0], parts[1], parts[2], 0, 0).epochDay
}
