@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ssafy.eolssu.core.storage

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.PhotosUI.*
import platform.UIKit.*
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

actual val sheetFileSources: List<SheetFileSource>
    get() = if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera))
        listOf(SheetFileSource.Files, SheetFileSource.Album, SheetFileSource.Scan)
    else listOf(SheetFileSource.Files, SheetFileSource.Album)

/** 앨범은 PHPicker(권한 없이 사진만, 최대 10장, 호환 형식으로 받음), 파일은 문서 선택기(PDF·PNG·JPEG)로 연다. */
@Composable
actual fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): (SheetFileSource) -> Unit {
    val callback by rememberUpdatedState(onResult)
    val documentDelegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                callback(SheetFileSelection.Cancelled)
            }
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val result = runCatching {
                    require(didPickDocumentsAtURLs.size in 1..MAX_SHEET_IMAGES)
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
                            val bytes = data.toByteArray()
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
    val albumDelegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                when {
                    results.isEmpty() -> callback(SheetFileSelection.Cancelled)
                    results.size > MAX_SHEET_IMAGES -> callback(SheetFileSelection.Failed)
                    else -> loadAlbumImages(results) { callback(it) }
                }
            }
        }
    }
    val scanDelegate = remember {
        object : NSObject(), VNDocumentCameraViewControllerDelegateProtocol {
            override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) {
                controller.dismissViewControllerAnimated(true, null)
                callback(SheetFileSelection.Cancelled)
            }

            override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFailWithError: NSError) {
                controller.dismissViewControllerAnimated(true, null)
                callback(SheetFileSelection.Failed)
            }

            override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFinishWithScan: VNDocumentCameraScan) {
                controller.dismissViewControllerAnimated(true, null)
                callback(scanFiles(didFinishWithScan))
            }
        }
    }
    var presented by remember { mutableStateOf<UIViewController?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            (presented as? UIDocumentPickerViewController)?.delegate = null
            (presented as? PHPickerViewController)?.delegate = null
            (presented as? VNDocumentCameraViewController)?.delegate = null
            presented?.dismissViewControllerAnimated(false, null)
        }
    }
    return { source ->
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
        if (presenter == null) callback(SheetFileSelection.Failed)
        else {
            val controller: UIViewController = when (source) {
                SheetFileSource.Album -> {
                    val configuration = PHPickerConfiguration()
                    configuration.selectionLimit = MAX_SHEET_IMAGES.toLong()
                    configuration.filter = PHPickerFilter.imagesFilter
                    // HEIC 같은 형식을 서버가 받는 JPEG로 바꿔 받는다.
                    configuration.preferredAssetRepresentationMode =
                        PHPickerConfigurationAssetRepresentationMode.PHPickerConfigurationAssetRepresentationModeCompatible
                    PHPickerViewController(configuration = configuration).also { it.delegate = albumDelegate }
                }
                SheetFileSource.Scan -> VNDocumentCameraViewController().also { it.delegate = scanDelegate }
                SheetFileSource.Files -> UIDocumentPickerViewController(
                    documentTypes = listOf("com.adobe.pdf", "public.png", "public.jpeg"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen,
                ).also {
                    it.allowsMultipleSelection = true
                    it.delegate = documentDelegate
                }
            }
            presented = controller
            presenter.presentViewController(controller, true, null)
        }
    }
}

/** 한 번 올릴 때 받는 사진 수. 서버 `SHEET_IMPORT_MAX_FILES`(10)와 같다. 내 악보 개수 제한(10개)과는 다른 규칙이다. */
private const val MAX_SHEET_IMAGES = 10

/** 고른 순서대로 사진 데이터를 받아 모두 끝나면 메인 스레드에서 한 번 [done]을 부른다. */
private fun loadAlbumImages(results: List<PHPickerResult>, done: (SheetFileSelection) -> Unit) {
    val loaded = arrayOfNulls<SheetFile>(results.size)
    var pending = results.size
    var failed = false
    var tooLarge = false
    results.forEachIndexed { index, result ->
        val provider = result.itemProvider
        val png = provider.hasItemConformingToTypeIdentifier("public.png")
        val name = "${provider.suggestedName ?: "사진 ${index + 1}"}.${if (png) "png" else "jpg"}"
        provider.loadDataRepresentationForTypeIdentifier(if (png) "public.png" else "public.jpeg") { data, _ ->
            val large = data != null && data.length > SHEET_MAX_BYTES.toULong()
            val bytes = data?.takeUnless { large }?.toByteArray()
            dispatch_async(dispatch_get_main_queue()) {
                when {
                    large -> tooLarge = true
                    bytes == null -> failed = true
                    else -> loaded[index] = SheetFile(name, bytes)
                }
                pending -= 1
                if (pending == 0) {
                    val files = loaded.filterNotNull()
                    done(when {
                        failed -> SheetFileSelection.Failed
                        tooLarge || files.sumOf { it.bytes.size.toLong() } > SHEET_MAX_BYTES -> SheetFileSelection.TooLarge
                        else -> SheetFileSelection.Selected(files)
                    })
                }
            }
        }
    }
}

private fun scanFiles(scan: VNDocumentCameraScan): SheetFileSelection {
    val count = scan.pageCount.toInt()
    if (count !in 1..MAX_SHEET_IMAGES) return SheetFileSelection.Failed
    var remaining = SHEET_MAX_BYTES
    val files = (0 until count).map { index ->
        val image = scan.imageOfPageAtIndex(index.toULong())
        val jpeg = UIImageJPEGRepresentation(image, 0.9) ?: return SheetFileSelection.Failed
        if (jpeg.length > remaining.toULong()) return SheetFileSelection.TooLarge
        val bytes = jpeg.toByteArray()
        remaining -= bytes.size
        SheetFile("스캔 ${index + 1}.jpg", bytes)
    }
    return SheetFileSelection.Selected(files)
}

private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
