package com.ssafy.eolssu.core.model

/** 목록용 곡 정보. 악보는 선택한 곡의 상세를 열 때 따로 읽는다. ID는 서버의 opaque 식별자다. */
data class SongSummary(
    val id: String,
    val title: String,
    val difficulty: Int,
    val jangdanName: String,
    val baseBpm: Int,
    val evaluable: Boolean,
    val instrumentId: String? = null,
    val scoreVersion: Int? = null,
    val instrumentCode: String? = null,
    val noteCount: Int? = null,
) {
    /** data/reference/songs.json에 정의된 1~3만 대응한다. 다른 값을 중급으로 바꾸지 않는다. */
    val level: SongLevel? get() = when (difficulty) {
        1 -> SongLevel.Intro
        2 -> SongLevel.Elementary
        3 -> SongLevel.Intermediate
        else -> null
    }
}

fun Song.toSummary(): SongSummary = SongSummary(
    id = id,
    title = title,
    difficulty = when (level) {
        SongLevel.Intro -> 1
        SongLevel.Elementary -> 2
        SongLevel.Intermediate -> 3
    },
    jangdanName = jangdanName,
    baseBpm = baseBpm,
    evaluable = evaluable,
    instrumentId = instrumentId,
    scoreVersion = scoreVersion,
    instrumentCode = score.source?.instrument,
    noteCount = noteCount,
)
