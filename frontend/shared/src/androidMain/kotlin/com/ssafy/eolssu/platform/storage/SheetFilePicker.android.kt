package com.ssafy.eolssu.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

actual val sheetFileSources: List<SheetFileSource> = listOf(SheetFileSource.Album, SheetFileSource.Files)

/** 앨범은 사진 선택기(권한 없이 사진만, 최대 10장), 파일은 문서 선택기(PDF·PNG·JPEG)로 연다. */
@Composable
actual fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): (SheetFileSource) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    val receive: (List<Uri>) -> Unit = { uris ->
        if (uris.isEmpty()) callback(SheetFileSelection.Cancelled)
        else scope.launch { callback(withContext(Dispatchers.IO) { readSheetFiles(context, uris) }) }
    }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SHEET_IMAGES), receive)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), receive)
    return { source ->
        try {
            when (source) {
                SheetFileSource.Album -> album.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                SheetFileSource.Files -> files.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
            }
        } catch (_: Exception) { callback(SheetFileSelection.Failed) }
    }
}

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
