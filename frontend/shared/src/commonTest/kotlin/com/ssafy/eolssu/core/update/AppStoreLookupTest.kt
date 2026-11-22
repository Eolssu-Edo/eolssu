package com.ssafy.eolssu.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStoreLookupTest {
    @Test
    fun readsVersionAndPageOfFirstResult() {
        val body = """
            {"resultCount":1,"results":[{"trackId":6700000000,"bundleId":"com.ssafy.eolssu","version":"1.0.1",
            "trackViewUrl":"https://apps.apple.com/kr/app/id6700000000?uo=4","releaseNotes":"버그 수정"}]}
        """.trimIndent()

        assertEquals(
            AppStoreRelease("1.0.1", "https://apps.apple.com/kr/app/id6700000000?uo=4"),
            parseAppStoreLookup(body),
        )
    }

    @Test
    fun appNotInStoreHasNoRelease() {
        assertNull(parseAppStoreLookup("""{"resultCount":0,"results":[]}"""))
    }

    @Test
    fun missingOrUnsafeFieldsHaveNoRelease() {
        assertNull(parseAppStoreLookup("""{"results":[{"trackViewUrl":"https://apps.apple.com/kr/app/id1"}]}"""))
        assertNull(parseAppStoreLookup("""{"results":[{"version":"1.0.1"}]}"""))
        assertNull(parseAppStoreLookup("""{"results":[{"version":"1.0.1","trackViewUrl":"http://apps.apple.com/kr/app/id1"}]}"""))
    }

    @Test
    fun malformedBodyHasNoRelease() {
        assertNull(parseAppStoreLookup("<html>Service Unavailable</html>"))
        assertNull(parseAppStoreLookup(""))
    }

    @Test
    fun looksUpKoreanStoreByBundleId() {
        assertEquals("https://itunes.apple.com/lookup?bundleId=com.ssafy.eolssu&country=kr", appStoreLookupUrl("com.ssafy.eolssu"))
    }
}
