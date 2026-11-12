package com.ssafy.eolssu.core.model

/** 곡 선택·연습 기록 화면의 곡 이름 정렬. 처음에는 가나다 오름차순이고 정렬 버튼으로 내림차순과 오간다. */
enum class SongSortOrder {
    Ascending,
    Descending,
    ;

    fun toggled(): SongSortOrder = if (this == Ascending) Descending else Ascending
}

/** [order]에 따라 곡 이름(앞뒤 공백 무시)으로 정렬한다. 이름이 같은 곡은 받은 순서를 유지한다. */
fun <T> List<T>.sortedByTitle(order: SongSortOrder, title: (T) -> String): List<T> {
    val byTitle = compareBy<T> { title(it).trim() }
    return sortedWith(if (order == SongSortOrder.Ascending) byTitle else byTitle.reversed())
}
