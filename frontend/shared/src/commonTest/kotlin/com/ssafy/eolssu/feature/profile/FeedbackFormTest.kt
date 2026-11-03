package com.ssafy.eolssu.feature.profile

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackFormTest {
    @Test fun opensPublicGoogleFormOverHttpsWithoutQuery() {
        val url = Url(FEEDBACK_FORM_URL)
        assertEquals(URLProtocol.HTTPS, url.protocol)
        assertEquals("docs.google.com", url.host)
        assertTrue(url.encodedPath.startsWith("/forms/d/e/") && url.encodedPath.endsWith("/viewform"), url.encodedPath)
        assertTrue(url.parameters.isEmpty(), "공유 링크의 usp 같은 파라미터는 빼고 공개 응답 주소만 둔다")
    }
}
