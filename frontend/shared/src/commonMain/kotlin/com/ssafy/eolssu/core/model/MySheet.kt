package com.ssafy.eolssu.core.model

/** 내 악보의 종류. 와이어프레임 API 정리의 `kind`(jeongganbo · midi · pdf). */
enum class MySheetKind { Jeongganbo, Midi, Pdf }

/** 내 악보로 할 수 있는 일. API 정리의 `status`(evaluable · needs-review · view-only). */
enum class MySheetStatus {
    /** 음표·박자 데이터가 있어 AI 연주 평가를 할 수 있다. */
    Evaluable,

    /** 음원에서 바꾼 MIDI라 확인이 필요한 칸을 고친 뒤 평가할 수 있다. */
    NeedsReview,

    /** PDF처럼 보기·넘김만 된다. */
    ViewOnly,
}

/**
 * 사용자가 만들거나 올린 악보(SONG-03 '내 악보').
 *
 * @property jangdanName 장단 이름(예: '세마치'). 모르면 null.
 * @property pendingCells MIDI 변환 결과에서 확인이 필요한 칸 수. MIDI가 아니면 null.
 * @property pageCount PDF 쪽 수. PDF가 아니면 null.
 * @property updatedAt 마지막으로 고치거나 올린 시각.
 * @property songId 연습 화면에 넘길 곡. 내 악보로 연습하는 API(`sheetId`)가 생기기 전까지 정간보 악보를 예시 곡에 잇는다.
 */
data class MySheet(
    val id: String,
    val kind: MySheetKind,
    val status: MySheetStatus,
    val title: String,
    val jangdanName: String? = null,
    val pendingCells: Int? = null,
    val pageCount: Int? = null,
    val updatedAt: KoreanDateTime,
    val songId: String? = null,
)

/** 기기에서 고른 PDF 파일. */
data class PdfFile(val name: String, val sizeBytes: Long)

/** 서버에 올린 PDF. 저장하기 전까지 미리보기(UPLOAD-02)에 쓴다. */
data class UploadedPdf(val id: String, val fileName: String, val sizeBytes: Long, val pageCount: Int)
