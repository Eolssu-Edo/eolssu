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

/** 앱에서는 PDF 1개만 올린다(S15P31E102-1225). 서버는 사진도 받지만 앱은 사진 악보 올리기를 지원하지 않는다. */
fun validSheetFiles(files: List<SheetFile>): Boolean = files.singleOrNull()?.let {
    it.bytes.isNotEmpty() && it.bytes.size <= SHEET_MAX_BYTES && it.contentType == "application/pdf"
} ?: false

/**
 * 악보 파일을 고를 곳. 화면은 파일 선택 버튼 아래 메뉴에 [sheetFileSources]에 든 것만 두고, 하나뿐이면 메뉴 없이 바로 연다.
 * 새 방법(예: 카메라 문서 스캔)은 항목과 플랫폼 구현만 더한다.
 */
enum class SheetFileSource {
    /** 기기 사진 앨범(사진 선택기). 사진만 고른다. */
    Album,
    /** 파일·드라이브(문서 선택기). PDF와 사진을 고른다. */
    Files,
}

/** 이 플랫폼에서 쓸 수 있는 고를 곳(보여 줄 순서). */
expect val sheetFileSources: List<SheetFileSource>

/** 돌려준 함수에 고를 곳을 넘기면 그 선택 창을 연다. 결과는 [onResult]로 한 번 온다. */
@Composable
expect fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): (SheetFileSource) -> Unit
