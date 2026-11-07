package com.ssafy.eolssu.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSourcesTest {
    @Test
    fun eachSourceAndLinkAppearsOnlyOnce() {
        assertEquals(DataSources.size, DataSources.map { it.id }.toSet().size)
        assertEquals(DataSources.size, DataSources.map { it.name }.toSet().size)
        val urls = DataSources.flatMap { source -> source.links.map { it.url } }
        assertEquals(urls.size, urls.toSet().size)
    }

    @Test
    fun linksOpenOnlyHttpsPages() {
        DataSources.flatMap { it.links }.forEach { link ->
            assertTrue(link.url.startsWith("https://") && ' ' !in link.url, link.url)
        }
    }

    @Test
    fun keepsTheOriginalAddressesFromTheRemovedScreenNotices() {
        val urls = DataSources.flatMap { source -> source.links.map { it.url } }.toSet()
        assertEquals(
            setOf(
                "https://www.gugak.go.kr/digitaleum/front/monotone/list.do",
                "https://www.kogl.or.kr/info/license.do",
                "https://github.com/danbinaerinHan/jeongganbo-generator",
            ),
            urls,
        )
    }
}
