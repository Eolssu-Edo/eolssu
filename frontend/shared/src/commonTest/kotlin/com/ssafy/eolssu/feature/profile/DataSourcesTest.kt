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
    fun keepsOriginalAddressesAndLinksTheSanjoChart() {
        assertTrue(DataSources.all { it.links.isNotEmpty() }, "원본 링크가 없는 출처가 있어요")
        val urls = DataSources.flatMap { source -> source.links.map { it.url } }.toSet()
        assertEquals(
            setOf(
                "https://www.gugak.go.kr/digitaleum/front/monotone/list.do",
                "https://www.kogl.or.kr/info/license.do",
                "https://www.catseoul.com/gugak-library/",
                "https://github.com/danbinaerinHan/jeongganbo-generator",
                "https://kukaksa.co.kr/web/myimg/daekum%20use.jpg",
                "https://academy.gugak.go.kr/",
                "https://www.gugak.go.kr/site/program/board/basicboard/list?boardtypeid=138&menuid=001003003006002&lang=ko",
                "https://www.gugak.go.kr/site/program/board/basicboard/view?boardid=17409&boardtypeid=88&menuid=001003002002&pagesize=10",
            ),
            urls,
        )
    }
}
