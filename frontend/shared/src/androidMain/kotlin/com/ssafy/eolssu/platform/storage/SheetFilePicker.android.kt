package com.ssafy.eolssu.core.storage

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

actual val sheetFileSources: List<SheetFileSource> = listOf(SheetFileSource.Files, SheetFileSource.Album, SheetFileSource.Scan)

/** 앨범은 사진 선택기(권한 없이 사진만, 최대 10장), 파일은 문서 선택기(PDF·PNG·JPEG)로 연다. */
@Composable
actual fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): (SheetFileSource) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    val receive: (List<Uri>) -> Unit = { uris ->
        if (uris.isEmpty()) callback(SheetFileSelection.Cancelled)
        else scope.launch { callback(withContext(Dispatchers.IO) { readSheetFiles(context, uris) }) }
    }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SHEET_IMAGES), receive)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), receive)
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_CANCELED -> callback(SheetFileSelection.Cancelled)
            Activity.RESULT_OK -> {
                val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
                if (pages.isEmpty()) callback(SheetFileSelection.Failed)
                else receive(pages.map { it.imageUri })
            }
            else -> callback(SheetFileSelection.Failed)
        }
    }
    val scanner = remember {
        GmsDocumentScanning.getClient(GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(MAX_SHEET_IMAGES)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build())
    }
    return { source ->
        try {
            when (source) {
                SheetFileSource.Album -> album.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                SheetFileSource.Files -> files.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
                SheetFileSource.Scan -> {
                    if (activity == null) callback(SheetFileSelection.Failed)
                    else scanner.getStartScanIntent(activity)
                        .addOnSuccessListener { sender ->
                            scope.launch {
                                try { scan.launch(IntentSenderRequest.Builder(sender).build()) }
                                catch (_: IllegalStateException) { callback(SheetFileSelection.Failed) }
                            }
                        }
                        .addOnFailureListener { scope.launch { callback(SheetFileSelection.Failed) } }
                }
            }
        } catch (_: Exception) { callback(SheetFileSelection.Failed) }
    }
}

/** 한 번 올릴 때 받는 사진 수. 서버 `SHEET_IMPORT_MAX_FILES`(10)와 같다. 내 악보 개수 제한(10개)과는 다른 규칙이다. */
private const val MAX_SHEET_IMAGES = 10

private fun readSheetFiles(context: Context, uris: List<Uri>): SheetFileSelection = try {
    require(uris.size <= MAX_SHEET_IMAGES)
    var remaining = SHEET_MAX_BYTES
    val files = uris.map { uri ->
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "악보"
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= remaining) {
                val count = stream.read(buffer, 0, minOf(buffer.size, remaining + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("파일 읽기 실패")
        if (bytes.size > remaining) return SheetFileSelection.TooLarge
        remaining -= bytes.size
        SheetFile(name, bytes)
    }
    SheetFileSelection.Selected(files)
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { SheetFileSelection.Failed }
