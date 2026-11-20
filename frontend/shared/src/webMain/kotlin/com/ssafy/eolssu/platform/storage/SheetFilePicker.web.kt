@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.core.storage

import androidx.compose.runtime.*
import kotlin.io.encoding.Base64
import kotlin.js.*
import kotlinx.browser.document
import kotlinx.coroutines.*
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

/** 브라우저는 파일 선택 창 하나로 PDF 하나만 고른다(S15P31E102-1225). */
actual val sheetFileSources: List<SheetFileSource> = listOf(SheetFileSource.Files)

@Composable
actual fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): (SheetFileSource) -> Unit {
    val callback by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val input = remember {
        (document.createElement("input") as HTMLInputElement).apply {
            type = "file"
            accept = "application/pdf"
            multiple = false
            style.display = "none"
        }
    }
    DisposableEffect(input) {
        document.body?.appendChild(input)
        input.addEventListener("cancel", { _: Event -> callback(SheetFileSelection.Cancelled) })
        input.onchange = {
            val chosen = input.files
            val files = (0 until (chosen?.length ?: 0)).mapNotNull { chosen?.get(it) }
            if (files.isEmpty()) callback(SheetFileSelection.Cancelled)
            else if (files.size > 10) callback(SheetFileSelection.Failed)
            else if (files.sumOf { it.size.toDouble() } > SHEET_MAX_BYTES) callback(SheetFileSelection.TooLarge)
            else scope.launch {
                val result = try {
                    SheetFileSelection.Selected(files.map { SheetFile(it.name, readSheetFile(it)) })
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { SheetFileSelection.Failed }
                callback(result)
            }
        }
        onDispose { input.onchange = null; input.remove() }
    }
    return { _ -> input.value = ""; input.click() }
}

private suspend fun readSheetFile(file: File): ByteArray = suspendCancellableCoroutine { continuation ->
    val reader = FileReader()
    reader.onload = {
        if (continuation.isActive) {
            val bytes = runCatching { Base64.Default.decode(reader.result.toString().substringAfter(',')) }
            continuation.resumeWith(bytes)
        }
    }
    reader.onerror = { if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("파일 읽기 실패"))) }
    continuation.invokeOnCancellation { reader.abort() }
    reader.readAsDataURL(file)
}
