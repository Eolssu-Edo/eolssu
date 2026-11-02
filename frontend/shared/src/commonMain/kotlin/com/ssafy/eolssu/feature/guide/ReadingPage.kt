package com.ssafy.eolssu.feature.guide

/**
 * 정간보 쉽게 읽기의 쪽(와이어프레임 READ_PAGES). 휴대폰은 이 순서로 한 쪽씩 넘기고, 태블릿은 [readingTabletRows]대로 펼친다.
 * [id]는 화면 경로 인자로 쓴다(기초부터 배우기 7·8·9단계가 이 쪽을 펼쳐 연다).
 */
enum class ReadingPage(val id: String) {
    Intro("intro"),
    Order("order"),
    Length("length"),
    Pitch("pitch"),
    Sigimsae("sig"),
    SigimsaeMore("sig-more"),
    ;

    /** 쪽 머리의 표시. 정간보란은 井, 나머지는 번호이고 딸린 쪽은 앞 쪽 번호를 쓴다. */
    val mark: String
        get() = when (this) {
            Intro -> "井"
            SigimsaeMore -> Sigimsae.mark
            else -> "$ordinal"
        }

    /** 앞 쪽에 딸린 쪽인지(더 많은 시김새). 쪽 표시를 흰 원으로 그린다. */
    val sub: Boolean get() = this == SigimsaeMore

    /** 이전 쪽. 첫 쪽이면 없어서 '이전'을 끈다. */
    val previous: ReadingPage? get() = entries.getOrNull(ordinal - 1)

    /** 다음 쪽. 마지막 쪽이면 없어서 '다음'을 끈다. */
    val next: ReadingPage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** 경로 인자의 쪽. 비었거나 모르는 값이면 첫 쪽이다. */
        fun fromId(id: String?): ReadingPage = entries.firstOrNull { it.id == id } ?: Intro
    }
}

/**
 * 태블릿 격자의 줄. 세로 2열은 정간보란·1 / 2·3, 가로 3열은 정간보란 한 줄 · 1·2·3 한 줄이고, 시김새와 더 많은 시김새는 늘 한 줄 전체다.
 */
fun readingTabletRows(columns: Int): List<List<ReadingPage>> {
    val fullRow = { page: ReadingPage ->
        page == ReadingPage.Sigimsae || page == ReadingPage.SigimsaeMore || (page == ReadingPage.Intro && columns >= 3)
    }
    val rows = mutableListOf<List<ReadingPage>>()
    var row = mutableListOf<ReadingPage>()
    ReadingPage.entries.forEach { page ->
        if (fullRow(page)) {
            if (row.isNotEmpty()) rows += row
            rows += listOf(page)
            row = mutableListOf()
        } else {
            row += page
            if (row.size == columns) {
                rows += row
                row = mutableListOf()
            }
        }
    }
    if (row.isNotEmpty()) rows += row
    return rows
}
