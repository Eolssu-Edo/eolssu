package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.UploadedPdf

/** UPLOAD-02에서 고를 수 있는 장단(와이어프레임 예시). '모름'은 null. */
val PdfJangdanChoices: List<String> = listOf("세마치", "굿거리", "중모리", "타령")

/**
 * UPLOAD-02 PDF 미리보기·저장 상태.
 *
 * @property page 크게 보는 쪽(1부터).
 * @property jangdanName 고른 장단. 모르면 null.
 * @property savedSheetId 저장을 마친 악보. 채워지면 화면을 닫는다.
 */
data class PdfPreviewUiState(
    val upload: UploadedPdf? = null,
    val page: Int = 1,
    val title: String = "",
    val jangdanName: String? = null,
    val saving: Boolean = false,
    val savedSheetId: String? = null,
) {
    val pageCount: Int get() = upload?.pageCount ?: 0

    val canSave: Boolean get() = upload != null && title.isNotBlank() && !saving && savedSheetId == null

    /** 올린 파일을 받으면 파일 이름에서 '.pdf'를 뗀 이름을 악보 이름으로 채운다. */
    fun receiveUpload(received: UploadedPdf): PdfPreviewUiState =
        copy(upload = received, title = defaultSheetTitle(received.fileName), page = 1)

    fun selectPage(value: Int): PdfPreviewUiState = if (value in 1..pageCount) copy(page = value) else this
}

/** '상령산 정간보.pdf' → '상령산 정간보'. 확장자를 떼면 비는 이름은 그대로 둔다. */
fun defaultSheetTitle(fileName: String): String {
    val base = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName.dropLast(4) else fileName
    return base.trim().ifEmpty { fileName }
}
