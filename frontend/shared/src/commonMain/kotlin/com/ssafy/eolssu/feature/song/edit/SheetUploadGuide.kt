package com.ssafy.eolssu.feature.song.edit

/** 회원 한 명이 둘 수 있는 내 악보 수. 서버 `SHEET_LIMIT_PER_MEMBER`와 같아야 한다(넘으면 409 SHEET_LIMIT_EXCEEDED). */
const val MY_SHEET_LIMIT = 10

/** 악보 올리기 화면 위 `이런 악보가 잘 읽혀요` 안내 줄(S15P31E102-997). */
enum class SheetUploadTip {
    /** 표준악보집 같은 PDF가 가장 정확하다. */
    DigitalPdf,
    /** 사진은 정면·전체·밝게. */
    PhotoFraming,
    /** 오선보 사진은 인식이 약하다. 오선보에만 보인다. */
    StaffPhotoWeak,
}

/** 고른 악보 종류에 맞는 안내 줄. 정간보에는 오선보 사진 경고를 넣지 않는다. */
fun sheetUploadTips(kind: ImportKind): List<SheetUploadTip> = buildList {
    add(SheetUploadTip.DigitalPdf)
    add(SheetUploadTip.PhotoFraming)
    if (kind == ImportKind.Staff) add(SheetUploadTip.StaffPhotoWeak)
}
