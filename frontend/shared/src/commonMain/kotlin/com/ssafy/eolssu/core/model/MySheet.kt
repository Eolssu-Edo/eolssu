package com.ssafy.eolssu.core.model

/** 내 악보를 만든 경로. 2차 결정으로 내 악보에는 정간보만 두고, 모두 점수 없이 연습할 수 있다. */
enum class MySheetOrigin {
    /** 제공 곡을 복사해 고쳤다. */
    CopiedSong,

    /** 정간보 사진·PDF를 인식해 가져왔다. */
    Imported,
}

/**
 * 사용자가 만들거나 가져온 정간보 악보(SONG-03 '내 악보').
 *
 * @property jangdanName 장단 이름(예: '세마치'). 모르면 null.
 * @property suiteName 장단 대신 보조 문구에 적는 곡 묶음 이름(예: '영산회상'). 장단이 있으면 장단을 먼저 쓴다.
 * @property updatedAt 마지막으로 고친 시각.
 * @property songId 연습 화면에 넘길 곡. 내 악보로 연습하는 API(`sheetId`)가 생기기 전까지 예시 곡에 잇는다.
 */
data class MySheet(
    val id: String,
    val origin: MySheetOrigin,
    val title: String,
    val jangdanName: String? = null,
    val suiteName: String? = null,
    val updatedAt: KoreanDateTime,
    val songId: String? = null,
    val status: String? = null,
    val conversionStatus: String? = null,
    val hasDraft: Boolean = false,
) {
    val practiceReady: Boolean get() = status == "EVALUABLE"
}
