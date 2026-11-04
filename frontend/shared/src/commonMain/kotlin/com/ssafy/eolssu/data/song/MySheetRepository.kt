package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 내 악보. 와이어프레임 API 정리의 `GET /me/sheets`, `DELETE /me/sheets/{id}`에 대응한다.
 * 지금은 [SampleMySheetRepository]가 앱이 켜져 있는 동안만 악보를 들고 있다.
 */
interface MySheetRepository {
    /** 최근에 고친 순. */
    val sheets: StateFlow<List<MySheet>>

    /** 내 악보를 지운다. 없는 악보면 아무것도 하지 않는다. */
    suspend fun delete(sheetId: String)
}

/**
 * 와이어프레임(Song-Practice-Mine) 예시 정간보 2개로 시작하고, 지우면 앱이 켜져 있는 동안 목록에서 빠진다.
 * [member]가 바뀌면(로그아웃·다른 계정) 지운 내용이 다음 계정에 남지 않게 예시 2개로 되돌린다.
 */
class SampleMySheetRepository(private val member: SignedInMember? = null) : MySheetRepository {
    private val _sheets = MutableStateFlow(SampleSheets)
    override val sheets: StateFlow<List<MySheet>> = _sheets.asStateFlow()

    init { member?.onChange { _sheets.value = SampleSheets } }

    override suspend fun delete(sheetId: String) = member.inSession {
        _sheets.update { sheets -> sheets.filterNot { it.id == sheetId } }
    }
}

/** 상령산은 예시 곡이 없어 같은 영산회상 곡인 세령산에 잇는다. */
private val SampleSheets = listOf(
    MySheet(
        "sample-arirang", MySheetOrigin.CopiedSong, "아리랑 (내 편곡)",
        jangdanName = SampleJangdans.Semachi.name, updatedAt = KoreanDateTime(2026, 10, 19, 21, 0), songId = "arirang",
    ),
    MySheet(
        "sample-sangnyeongsan", MySheetOrigin.Imported, "상령산",
        suiteName = "영산회상", updatedAt = KoreanDateTime(2026, 10, 17, 19, 0), songId = "seryeongsan",
    ),
)
