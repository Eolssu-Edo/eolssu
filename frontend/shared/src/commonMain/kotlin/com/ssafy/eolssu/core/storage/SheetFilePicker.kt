package com.ssafy.eolssu.core.storage

import androidx.compose.runtime.Composable

const val SHEET_MAX_BYTES = 20 * 1024 * 1024

/** 선택한 원본은 화면 메모리에서만 유지한다. 기기 경로·읽기 권한을 서버에 넘기지 않는다. */
class SheetFile(val name: String, val bytes: ByteArray) {
    val contentType: String? get() = when {
        bytes.startsWith(0x25, 0x50, 0x44, 0x46, 0x2d) -> "application/pdf"
        bytes.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "image/png"
        bytes.startsWith(0xff, 0xd8, 0xff) -> "image/jpeg"
        else -> null
    }
}

private fun ByteArray.startsWith(vararg prefix: Int) = size >= prefix.size && prefix.indices.all { (this[it].toInt() and 255) == prefix[it] }

sealed interface SheetFileSelection {
    data class Selected(val files: List<SheetFile>) : SheetFileSelection
    data object Cancelled : SheetFileSelection
    data object Failed : SheetFileSelection
    data object TooLarge : SheetFileSelection
}

/** PDF 1개 또는 PNG/JPEG 1~10장, 파일 순서는 선택 결과 그대로다. */
fun validSheetFiles(files: List<SheetFile>): Boolean = files.size in 1..10 &&
    files.sumOf { it.bytes.size.toLong() } <= SHEET_MAX_BYTES &&
    files.all { it.bytes.isNotEmpty() && it.contentType != null } &&
    (files.none { it.contentType == "application/pdf" } || files.size == 1)

@Composable
expect fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): () -> Unit
