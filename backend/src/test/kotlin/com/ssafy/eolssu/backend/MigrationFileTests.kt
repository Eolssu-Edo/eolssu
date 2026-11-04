package com.ssafy.eolssu.backend

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

// Jenkins는 databaseTest를 돌리지 않는다. 병합으로 생긴 번호 중복·공백을 배포 전 test 단계에서 막는다.
class MigrationFileTests {
    private val names = PathMatchingResourcePatternResolver()
        .getResources("classpath*:db/migration/*")
        .mapNotNull { it.filename }

    @Test
    fun `migration files follow the Flyway naming rule`() {
        assertTrue(names.isNotEmpty())
        val invalid = names.filterNot { NAME.matches(it) }
        assertTrue(invalid.isEmpty(), "V숫자__소문자_설명.sql 형식이 아니다: $invalid")
    }

    @Test
    fun `each version is used once without gaps`() {
        val byVersion = names.mapNotNull { name -> NAME.matchEntire(name)?.let { it.groupValues[1].toInt() to name } }
            .groupBy({ it.first }, { it.second })
        val duplicated = byVersion.filterValues { it.size > 1 }
        assertTrue(duplicated.isEmpty(), "같은 버전이 여러 파일에 있다. 나중에 병합한 쪽을 develop 최신 다음 번호로 옮긴다: $duplicated")
        assertEquals((1..byVersion.size).toList(), byVersion.keys.sorted(), "버전 번호가 1부터 이어지지 않는다")
    }

    private companion object {
        val NAME = Regex("""V([1-9][0-9]*)__[a-z0-9_]+\.sql""")
    }
}
