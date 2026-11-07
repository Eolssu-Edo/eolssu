package com.ssafy.eolssu.core.storage

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberSheetFilePicker(onResult: (SheetFileSelection) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) callback(SheetFileSelection.Cancelled)
        else scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    require(uris.size <= 10)
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
                        if (bytes.size > remaining) return@withContext SheetFileSelection.TooLarge
                        remaining -= bytes.size
                        SheetFile(name, bytes)
                    }
                    SheetFileSelection.Selected(files)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { SheetFileSelection.Failed }
            }
            callback(result)
        }
    }
    return {
        try { launcher.launch(arrayOf("application/pdf", "image/png", "image/jpeg")) }
        catch (_: Exception) { callback(SheetFileSelection.Failed) }
    }
}
