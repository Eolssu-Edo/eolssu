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
 * 앱을 업데이트하면 앱 폴더 경로가 바뀔 수 있으므로, 저장해 둔 경로에 파일이 없으면 보관 위치의 같은 파일 이름으로 찾는다.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRecordingFiles(
    private val directory: String = applicationSupportPath() + "/" + DIRECTORY,
) : RecordingFiles {
    private val files = NSFileManager.defaultManager

    override fun keep(path: String): String? {
        val source = locate(path) ?: return null
        if (source.substringBeforeLast('/') == directory) return source
        if (!files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)) return null
        val target = kept(source)
        files.removeItemAtPath(target, error = null)
        if (files.moveItemAtPath(source, toPath = target, error = null)) return target
        files.removeItemAtPath(target, error = null)
        return null
    }

    override fun exists(path: String): Boolean = locate(path) != null

    override fun locate(path: String): String? = path.takeIf(files::fileExistsAtPath) ?: kept(path).takeIf(files::fileExistsAtPath)

    override fun delete(path: String) {
        locate(path)?.let { files.removeItemAtPath(it, error = null) }
    }

    override fun deleteKeptExcept(path: String?) {
        val names = files.contentsOfDirectoryAtPath(directory, error = null) ?: return
        val keptName = path?.substringAfterLast('/')
        names.filterIsInstance<String>().filter { it != keptName }.forEach { files.removeItemAtPath("$directory/$it", error = null) }
    }

    /** 보관 위치에 둔 같은 이름의 파일 경로. */
    private fun kept(path: String): String = directory + "/" + path.substringAfterLast('/')

    private companion object {
        const val DIRECTORY = "pending-recordings"
    }
}

internal fun applicationSupportPath(): String =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
