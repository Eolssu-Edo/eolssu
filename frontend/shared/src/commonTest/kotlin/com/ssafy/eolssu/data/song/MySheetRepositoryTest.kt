package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.testing.runImmediately
import kotlin.test.Test
import kotlin.test.assertEquals

class MySheetRepositoryTest {

    @Test
    fun startsWithWireframeSampleSheets() {
        val sheets = SampleMySheetRepository().sheets.value
        assertEquals(listOf("아리랑 (내 편곡)", "상령산"), sheets.map { it.title })
        assertEquals(listOf(MySheetOrigin.CopiedSong, MySheetOrigin.Imported), sheets.map { it.origin })
        assertEquals("세마치", sheets[0].jangdanName)
        assertEquals("영산회상", sheets[1].suiteName)
        assertEquals("arirang", sheets.first().songId)
    }

    @Test
    fun deletingRemovesOnlyThatSheet() = runImmediately {
        val repository = SampleMySheetRepository()
        repository.delete("sample-arirang")
        assertEquals(listOf("sample-sangnyeongsan"), repository.sheets.value.map { it.id })

        repository.delete("missing")
        assertEquals(1, repository.sheets.value.size, "없는 악보는 지우지 않는다")
    }
}
