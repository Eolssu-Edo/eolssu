@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.core.storage

import androidx.compose.runtime.*
import kotlin.js.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

/** 브라우저는 파일 선택 창 하나로 PDF 하나만 고른다(S15P31E102-1225). */
actual val sheetFileSources: List<SheetFileSource> = listOf(SheetFileSource.Files)

/**
 * 파일 창이 닫혀 페이지에 focus가 돌아온 뒤 이 시간 안에 `change`·`cancel`이 없으면 취소로 본다.
 * `cancel` 이벤트가 없는 브라우저에서 '파일 읽는 중'에 멈추지 않게 하는 대체 처리다(S15P31E102-1266).
 * 늦게 온 `change`는 이 뒤에도 그대로 처리한다.
 */
private const val CANCEL_FALLBACK_MS = 1_000L

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
    val dialog = remember { PickerDialog(scope) { callback(SheetFileSelection.Cancelled) } }
    DisposableEffect(input) {
        document.body?.appendChild(input)
        input.addEventListener("cancel", { _: Event -> dialog.cancel() })
        input.onchange = {
            dialog.close()
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
        onDispose { dialog.close(); input.onchange = null; input.remove() }
    }
    return { _ -> input.value = ""; dialog.open(); input.click() }
}

/** 연 파일 창이 아직 결과를 보내지 않았는지와 취소 대체 처리 대기. 결과(취소)는 [onCancelled]로 한 번만 보낸다. */
private class PickerDialog(private val scope: CoroutineScope, private val onCancelled: () -> Unit) {
    private var waiting = false
    private var fallback: Job? = null

    fun open() { close(); waiting = true; PageReturn.watch(this) }
    fun close() { waiting = false; fallback?.cancel(); fallback = null; PageReturn.unwatch(this) }
    fun cancel() { if (waiting) { close(); onCancelled() } }
    fun pageReturned() {
        if (!waiting) return
        fallback?.cancel()
        // 그냥 `cancel()`이라 쓰면 launch 블록의 `CoroutineScope.cancel()`로 해석돼 이 Job만 취소되고 끝난다.
        fallback = scope.launch { delay(CANCEL_FALLBACK_MS); this@PickerDialog.cancel() }
    }
}

/**
 * 파일 창이 닫혀 페이지로 돌아온 것(window focus)을 열린 [PickerDialog]에 알린다.
 * Wasm은 Kotlin 람다를 넘길 때마다 새 JS 함수를 만들어 `removeEventListener`로 뗄 수 없으므로 리스너는 처음 한 번만 단다.
 */
private object PageReturn {
    private var watcher: PickerDialog? = null
    private var installed = false

    fun watch(dialog: PickerDialog) {
        watcher = dialog
        if (installed) return
        installed = true
        window.addEventListener("focus", { _: Event -> watcher?.pageReturned() })
    }
    fun unwatch(dialog: PickerDialog) { if (watcher === dialog) watcher = null }
}

/** Data URL·Base64 문자열을 거치지 않고 바이트를 바로 읽는다(S15P31E102-1266). */
private suspend fun readSheetFile(file: File): ByteArray = suspendCancellableCoroutine { continuation ->
    val reader = FileReader()
    reader.onload = {
        if (continuation.isActive) {
            val bytes = runCatching { Int8Array(reader.result as ArrayBuffer).toByteArray() }
            continuation.resumeWith(bytes)
        }
    }
    reader.onerror = { if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("파일 읽기 실패"))) }
    continuation.invokeOnCancellation { reader.abort() }
    reader.readAsArrayBuffer(file)
}
