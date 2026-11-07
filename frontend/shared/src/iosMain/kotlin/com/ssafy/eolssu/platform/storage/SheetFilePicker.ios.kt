@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ssafy.eolssu.core.storage

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                callback(SheetFileSelection.Cancelled)
            }
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val result = runCatching {
                    require(didPickDocumentsAtURLs.size in 1..10)
                    var remaining = SHEET_MAX_BYTES
                    val files = didPickDocumentsAtURLs.map { value ->
                        val url = value as NSURL
                        val accessed = url.startAccessingSecurityScopedResource()
                        try {
                            val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(requireNotNull(url.path), null)
                            val size = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: error("파일 크기 확인 실패")
                            if (size > remaining) return@runCatching SheetFileSelection.TooLarge
                            val data = NSData.dataWithContentsOfURL(url, options = NSDataReadingMappedIfSafe, error = null) ?: error("파일 읽기 실패")
                            if (data.length > remaining.toULong()) return@runCatching SheetFileSelection.TooLarge
                            val bytes = ByteArray(data.length.toInt())
                            if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                            remaining -= bytes.size
                            SheetFile(url.lastPathComponent ?: "악보", bytes)
                        } finally { if (accessed) url.stopAccessingSecurityScopedResource() }
                    }
                    SheetFileSelection.Selected(files)
                }.getOrElse { SheetFileSelection.Failed }
                callback(result)
            }
        }
    }
    var picker by remember { mutableStateOf<UIDocumentPickerViewController?>(null) }
    DisposableEffect(Unit) { onDispose { picker?.delegate = null; picker?.dismissViewControllerAnimated(false, null) } }
    return {
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        if (presenter == null) callback(SheetFileSelection.Failed)
        else {
            val controller = UIDocumentPickerViewController(documentTypes = listOf("com.adobe.pdf", "public.png", "public.jpeg"), inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen)
            controller.allowsMultipleSelection = true
            controller.delegate = delegate
            picker = controller
            presenter.presentViewController(controller, true, null)
        }
    }
}
