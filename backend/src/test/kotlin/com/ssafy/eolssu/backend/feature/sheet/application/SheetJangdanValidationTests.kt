package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.catalog.domain.JangdanTimingCatalog
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetJangdan
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.*

class SheetJangdanValidationTests {
    private val json = JsonMapper.builder().build()
    private val validator = SheetJangdanValidation(json)
    private val four = SheetJangdan(12,"QUARTER","4박 기본 반주", """{"beats":["덩","덕","궁","덕"],"group":[1,1,1,1]}""")
    private val danmori = SheetJangdan(7,"QUARTER","단모리", """{"beats":["덩","","따","따","궁","따","궁",""],"group":[2,2,2,2]}""")
    private fun score(name: String = four.name, sizes: List<Int> = listOf(4,4), first: String = "REST", start: Int = 0): String =
        """{"music":{"jangdan":"$name","bpmUnit":"QUARTER","ticksPerBeat":12,"ticksPerJeonggan":12},
        "columns":[${sizes.map { count -> "{\"cells\":[${List(count) { "{}" }.joinToString()}]}" }.joinToString()}],
        "events":[{"kind":"$first","cellId":"c0-j0","startTick":$start}]}"""

    @Test fun `both distinct four beat patterns use 48 ticks and allow opening rests`() {
        for (row in listOf(four,danmori)) {
            val cell = assertNotNull(JangdanTimingCatalog.cellEighths(row.name,row.bpmUnit,json.readTree(row.pattern!!)))
            assertEquals(48L,JangdanTimingCatalog.cycleTicks(json.readTree(row.pattern).path("beats").size(),cell,"QUARTER",12))
            assertTrue(validator.valid(score(row.name),row))
        }
        assertNotEquals(four.pattern,danmori.pattern)
        assertTrue(validator.valid(score(first="NOTE"),four))
    }
    @Test fun `every column including the ending needs a complete cycle at origin zero`() {
        assertFalse(validator.valid(score(sizes=listOf(4,2)),four))
        assertFalse(validator.valid(score(sizes=listOf(2,4)),four))
        assertFalse(validator.valid(score(start=1),four))
        assertTrue(validator.valid(score(sizes=listOf(8,4)),four))
    }
    @Test fun `name alone unit mismatch or a changed catalog pattern never passes`() {
        assertFalse(validator.valid(score(),null))
        assertTrue(validator.valid(score(name=""),null))
        assertFalse(validator.valid(score(),danmori))
        assertFalse(validator.valid(score().replace("QUARTER","DOTTED_QUARTER"),four))
        assertFalse(validator.valid(score(),four.copy(pattern=four.pattern!!.replace("궁","덕"))))
        assertFalse(validator.valid(score(),four.copy(pattern=null)))
    }
}
