package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.RecordingFiles
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * 업로드를 기다리는 녹음을 Application Support의 `pending-recordings/`에 둔다.
 * [IosAudioRecorder]는 새 녹음을 시작할 때 캐시의 `recordings/`를 비우고, 캐시는 시스템이 지울 수도 있어 따로 옮긴다.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRecordingFiles(
    private val directory: String = applicationSupportPath() + "/" + DIRECTORY,
) : RecordingFiles {
    private val files = NSFileManager.defaultManager

    override fun keep(path: String): String? {
        if (!exists(path)) return null
        if (path.substringBeforeLast('/') == directory) return path
        if (!files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)) return null
        val target = directory + "/" + path.substringAfterLast('/')
        files.removeItemAtPath(target, error = null)
        if (files.moveItemAtPath(path, toPath = target, error = null)) return target
        files.removeItemAtPath(target, error = null)
        return null
    }

    override fun exists(path: String): Boolean = files.fileExistsAtPath(path)

    override fun delete(path: String) {
        files.removeItemAtPath(path, error = null)
    }

    override fun deleteKeptExcept(path: String?) {
        val names = files.contentsOfDirectoryAtPath(directory, error = null) ?: return
        names.filterIsInstance<String>().map { "$directory/$it" }.filter { it != path }.forEach { delete(it) }
    }

    private companion object {
        const val DIRECTORY = "pending-recordings"
    }
}

internal fun applicationSupportPath(): String =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
