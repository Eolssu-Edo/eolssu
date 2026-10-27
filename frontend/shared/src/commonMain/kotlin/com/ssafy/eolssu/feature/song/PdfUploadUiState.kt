package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.PdfFile
import com.ssafy.eolssu.core.model.UploadedPdf

/** UPLOAD-01 단계: 파일 고르기 전 · 올리는 중 · 올리기 완료. */
enum class PdfUploadPhase { Empty, Uploading, Uploaded }

/**
 * UPLOAD-01 PDF 올리기 상태. 파일을 고르면 바로 올리기 시작하고, 다 올려야 미리보기로 넘어갈 수 있다.
 *
 * @property progress 올린 정도(0~1).
 * @property uploaded 다 올린 파일. 올리는 중이면 null.
 */
data class PdfUploadUiState(
    val file: PdfFile? = null,
    val progress: Float = 0f,
    val uploaded: UploadedPdf? = null,
) {
    val phase: PdfUploadPhase
        get() = when {
            file == null -> PdfUploadPhase.Empty
            uploaded == null -> PdfUploadPhase.Uploading
            else -> PdfUploadPhase.Uploaded
        }

    val canProceed: Boolean get() = uploaded != null

    /** 새 파일을 올리기 시작한다. 이전 파일은 버린다. */
    fun startUpload(file: PdfFile): PdfUploadUiState = PdfUploadUiState(file = file)

    /** 진행률은 0~1 사이에서 앞으로만 간다. 올리는 중이 아니면 무시한다. */
    fun withProgress(value: Float): PdfUploadUiState =
        if (phase != PdfUploadPhase.Uploading) this else copy(progress = maxOf(progress, value.coerceIn(0f, 1f)))

    fun finishUpload(result: UploadedPdf): PdfUploadUiState =
        if (phase != PdfUploadPhase.Uploading) this else copy(progress = 1f, uploaded = result)

    /** '파일 빼기': 고르기 전으로 돌아간다. */
    fun removeFile(): PdfUploadUiState = PdfUploadUiState()
}

/** 파일 크기 표시: 1MB 미만은 'KB'(올림), 이상은 소수 첫째 자리 'MB'(2.4MB). 1KB = 1024B. */
fun formatFileSize(bytes: Long): String {
    val kb = 1024L
    val mb = kb * kb
    if (bytes < mb) return "${(bytes + kb - 1) / kb}KB"
    val tenths = (bytes * 10 + mb / 2) / mb
    return "${tenths / 10}.${tenths % 10}MB"
}
